---
thumbnail: _static/asymmetric-ranking-optimization.png
title: Optimize Asymmetric Re-ranking with Algebra
date: 2026-09-01
---

**TL;DR**: a bit of algebra speeds up your asymmetric re-ranking!

## Context

Binary quantization (BQ) is all the rage these days!
 > “Binary Quantization & Rescoring: 96% Less Memory, Faster Search” [source](https://www.mongodb.com/company/blog/product-release-announcements/binary-quantization-rescoring-96-less-memory-faster-search) 

But it is also well known that BQ loses recall.
> _"recall isn't exceptionally high"_ [source](https://weaviate.io/blog/binary-quantization#performance-improvements-with-bq)

So, we need to fight back that loss in recall with some re-ranking after we (quickly) get the initial candidate set.
Probably the cheapest reranking to do is the asymmetric scoring: take the full-precision query embedding (which you almost always already have) and measure closeness against a BQ document embedding(s).

## What are we talking about?

In Vespa the asymmetric re-ranking typically looks like this:

```text
rank-profile asymmetric {
    inputs {
        query(query) tensor<float>(x[128])
    }
    function unpack_binary_representation() {
        # Schema has a field embeddings_bin of type tensor<int8>(x[16])
        # Unpack bits and convert to a signed integer {1, -1}
        expression: 2 * unpack_bits(attribute(embeddings_bin)) - 1
    }
    function asymmetric_closeness() {
        # dot product and scale back to [0,1]
        expression {
            (sum(query(query) * unpack_binary_representation()) + 1) / 2
        }
    }
}
```
Good, but in which ranking phase we can fit it in?
Spin-up Vespa docker container `vespaengine/vespa:8.738.17`.
Let's see how long does it take to compute the asymmetric closeness for 3755976 documents and then decide?

```shell
vespa query \
    'ranking.profile=asymmetric' \
    'select * from sources chunk where true limit 1' \
    'input.query(query_query)=[0.5]' \
    'presentation.timing=true' \
    'model.searchPath=0/0' \
    'ranking.matching.numThreadsPerSearch=1' \
    -t http://localhost:8080 \
    --profile --profile-file - \
    && vespa inspect profile -f -
```

Gives
```text
first phase rank profiling for thread #0 (total time was 562.884 ms)
┌─────────┬─────────┬────────────────────────────────────────┐
│ count   │ self_ms │ component                              │
├─────────┼─────────┼────────────────────────────────────────┤
│ 3755976 │ 273.861 │ function unpack_binary_representation  │
│ 3755976 │ 225.112 │ function asymmetric_closeness          │
│ 3755976 │  63.912 │ rank feature attribute(embeddings_bin) │
│       1 │   0.000 │ rank feature query(query)              │
└─────────┴─────────┴────────────────────────────────────────┘
```

This gives us roughly 0.562884 / 3755976 ~ 0.0000001498635774s, 149,8635774 ns/doc.
So, re-ranking 1M docs single threaded takes ~150 ms.

NOTE: Keep in mind that latency numbers are from profiling, i.e., with huge overhead, e.g., the full request latency without profiling is 267 ms.

## Can it be optimized?

Can algebraic rewrite help?

```text
rank-profile asymmetric_rewrite {
    function query_bit_sum() {
        # Factor that normalizes the scores into [0,1]
        # but it doesn't change the order
        expression: (1 - sum(query(query))) / 2
    }
    function asymmetric_closeness() {
        expression {
            sum(query(query) * unpack_bits(attribute(embeddings_bin)), x)
            + query_bit_sum()
        }
    }
    first-phase {
        expression: asymmetric_closeness()
    }
}
```

Is that correct? The overall shape is `(q*(2*doc -1) + 1) / 2`, which is equivalent to `(2*q*doc - sum(q) + 1) / 2` which is equivalent to  `q*doc + (1-sum(q))/2`.
Just like in back high school!

Then the same query gives:
```text
first phase rank profiling for thread #0 (total time was 365.990 ms)
┌─────────┬─────────┬────────────────────────────────────────┐
│ count   │ self_ms │ component                              │
├─────────┼─────────┼────────────────────────────────────────┤
│ 3755976 │ 303.069 │ function asymmetric_closeness          │
│ 3755976 │  62.918 │ rank feature attribute(embeddings_bin) │
│       1 │   0.003 │ function query_bit_sum                 │
│       1 │   0.000 │ rank feature query(query)              │
└─────────┴─────────┴────────────────────────────────────────┘
```
Nice, from 562.884 ms down to 365.990 ms, -35%!
Which puts asymmetric re-ranking for 1M docs at 97ms.

NOTE: Latency without profiling is ~193 ms,

### Constant-folding

Analyzing the previous trace a bit deeper, we can see that the function `query_bit_sum` was computed only once.
In Vespa, ranking expressions that do not depend on the document fields are constant-folded.

E.g., if we would inline `query_bit_sum` back into `asymmetric_closeness`:
```text
function asymmetric_closeness() {
    expression {
        sum(query(query) * unpack_bits(attribute(embeddings_bin), float, big), x)
        + (1 - sum(query(query))) / 2
    }
}
```
Then trace would be something like:
```text
first phase rank profiling for thread #0 (total time was 526.484 ms)
┌─────────┬─────────┬────────────────────────────────────────┐
│ count   │ self_ms │ component                              │
├─────────┼─────────┼────────────────────────────────────────┤
│ 3755919 │ 464.754 │ function asymmetric_closeness          │
│ 3755919 │  61.730 │ rank feature attribute(embeddings_bin) │
│       1 │   0.000 │ rank feature query(query)              │
└─────────┴─────────┴────────────────────────────────────────┘
```

Huh, much slower! 
Almost as slow as the original ranking profile.
The thing is that the `(1 - sum(query(query))) / 2` now is calculated for every document.
So, it is worth checking it if query-dependent parts are placed into their own ranking function!
If you're really short on compute in content nodes, then you can pre-calculate it in the container nodes and send that number as a ranking feature.

### Bare asymmetric scoring
What would happen if we eliminated the `query_bit_sum` function?
```text
first phase rank profiling for thread #0 (total time was 346.356 ms)
┌─────────┬─────────┬────────────────────────────────────────┐
│ count   │ self_ms │ component                              │
├─────────┼─────────┼────────────────────────────────────────┤
│ 3755919 │ 283.643 │ function asymmetric_closeness          │
│ 3755919 │  62.714 │ rank feature attribute(embeddings_bin) │
│       1 │   0.000 │ rank feature query(query)              │
└─────────┴─────────┴────────────────────────────────────────┘
```
A bit faster, but even though ordering is the same, scores are not scaled back to [0,1] anymore.
If you don't need to combine it with other scores, here asymmetric re-scoring is as fast as it gets: ~92 ms per million documents.

## Maybe digging a little deeper still?

In the ranking we now have two significant parts: `function asymmetric_closeness` and `rank feature attribute(embeddings_bin)`.
Nothing much to say about `attribute(embeddings_bin)`: plenty of pointer chasing for 16 byte tensor data.
The top-level operation within `asymmetric_closeness` is a dot product between the query `tensor<float>(x[128])` and the unpacked document `tensor<float>(x[128])`.
The float dot product itself is as optimized as it gets, nothing to do here.
But we have the bit unpacking to convert from `tensor<int8>(x[16])` into `tensor<float>(x[128])`.
That smells like a lot of allocations in the hot loop: the newly allocated tensor is passed further into the dot product.

There are two angles of attack for further optimization:
1. Can we make the unpacking faster?
2. Can we eliminate the allocation of the intermediate tensor?

### Making the unpacking faster

We are talking about this piece of [code in](https://github.com/vespa-engine/vespa/blob/5e11e65ebce6aefcc706a00505bdc2c667560e42/eval/src/vespa/eval/instruction/unpack_bits_function.cpp#L29-L47) `unpack_bits_function.cpp`.
```text
template <typename OCT, bool big> void my_unpack_bits_op(InterpretedFunction::State& state, uint64_t param) {
    const ValueType& res_type = unwrap_param<ValueType>(param);
    auto             packed_cells = state.peek(0).cells().typify<Int8Float>();
    auto             unpacked_cells = state.stash.create_uninitialized_array<OCT>(packed_cells.size() * 8);
    OCT*             dst = unpacked_cells.data();
    for (Int8Float cell : packed_cells) {
        if constexpr (big) {
            for (int n = 7; n >= 0; --n) {
                *dst++ = (OCT) bool(cell.get_bits() & (1 << n));
            }
        } else {
            for (int n = 0; n <= 7; ++n) {
                *dst++ = (OCT) bool(cell.get_bits() & (1 << n));
            }
        }
    }
    Value& result_ref = state.stash.create<ValueView>(res_type, state.peek(0).index(), TypedCells(unpacked_cells));
    state.pop_push(result_ref);
}
```
The loop takes a byte, iterates over the bits, and casts the bit-boolean into the parameterized width type.
Then the result of the loop is placed into a stash memory.
The calculations are done on pretty small tensors, so applying SIMD wouldn't be a miracle.
Maybe a look-up-table (LUT) could speed up the process a bit?
Micro-benchmarks say it would maybe by 2x, but under a real workload the LUT itself would have to fight for a place in L1/L2 cache in a hot loop.
Only Vespa performance test suite could answer whether it is worth it.

[Here](https://github.com/vespa-engine/vespa/pull/37738) is the PR with LUT optimization implemented by yours truly.

### Fusing the unpacking with the dot product

Vespa has a lot of optimizations for ranking expressions.
They work like this: if a known pattern is matched, then some special code is used.
That special code can go crazy and use whatever magic that gets the job done.

E.g., recently there was a [special kernel contributed](https://github.com/vespa-engine/vespa/pull/37563) for the chunked Hamming MaxSim which cut the latency by ~30x.

In our case, the pattern to detect is pretty simple:
```text
sum(query(query) * unpack_bits(attribute(embeddings_bin)), x)
```
I think this is a good candidate for a specialized kernel: it optimizes a real and pretty common computation.
E.g., with one chunk per Vespa document, or in the layered ranking when `title` of the document has its own embedding.
If the optimized kernel could bring the asymmetric re-ranking down from 92 ms to something like 30–50 ms per million documents, that would be a huge win!

Is that reasonable?
Say the server has a memory bandwidth of 4GB/s/core.
1 M of 16 byte tensors weight 16 MB; transferring raw bytes to the CPU takes at least 16 / 4096 = 3.9 ms.
Assuming that the optimized dot product (10–50 ns per operation) for 1M docs could take about 10–50 ms.
So, expecting the latency to go <50 ms is pretty reasonable.

## Phased ranking

But where in the [retrieval funnel](https://learn.vespa.ai/reranking-ltr/multiphase-ranking/#the-ranking-pipeline) to put the asymmetric re-ranking?
You should design ranking phases so that the 1st phase can comfortably run on ~million of [documents](https://learn.vespa.ai/reranking-ltr/multiphase-ranking/) single threaded within your latency and compute budget.
If your total latency budget is 50ms, be careful with retrievers, but if you target about 500ms, then IMHO all good. 
Scores like BM25 (single digit ms) and scalar operations are typically fine in the 1st phase.

2nd phase is a safe place to put the asymmetric re-ranking as it should do <1000 docs per node.
Global-phase is a bad idea, because that means transferring tensor data over the network.

## Next steps

How about layered-ranking when you have multiple embeddings per document?

## Summary

Asymmetric scoring is an approachable option to fight back some recall loss due to binary quantization.
Algebraic rewriting of a ranking expression reduced the latency by 35%: all done in user space!
While rewriting, we've learned a neat trick about constant-folding of ranking expressions.
Also, peeked at the code of the `unpack_bits` function.
And identified a couple of optimizations that could make asymmetric reranking even cheaper.
Hope, you enjoyed this post as much as I did writing it!
