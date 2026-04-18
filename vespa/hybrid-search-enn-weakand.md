---
thumbnail: _static/filters-enn-weeakAnd-query-tree.png
---

# Strategic Query Shaping for Vespa Hybrid Search

2026-04-15

**TL;DR:** In [Vespa](https://vespa.ai/), when combining **exact nearest neighbor** search with **weakAnd**, it is often more efficient to distribute your filters: `(filters AND ENN) OR (filters AND weakAnd)`. This performs better than the nested approach: `filters AND (ENN OR weakAnd)`.

## Context

[Hybrid search](https://docs.vespa.ai/en/learn/tutorials/hybrid-search) typically means combining vector and lexical retrievers.
Also, a fact of life is that anything useful typically also requires good ol' filtering.
Therefore, it is natural to write queries like:

```sql
SELECT * 
FROM docs
WHERE filters AND (nearestNeighbor OR weakAnd)
```

## The issue

When [`nearestNeighbor`](https://docs.vespa.ai/en/querying/nearest-neighbor-search-guide) means [executing exact nearest neighbors (ENN) search](https://docs.vespa.ai/en/querying/nearest-neighbor-search-guide#exact-nearest-neighbor-search), and it is combined through `OR` with [`weakAnd`](https://docs.vespa.ai/en/ranking/wand.html#weakand), then you might experience elevated latencies, maybe even timeouts, for seemingly no good reason.

Let's demonstrate the issue with a small example application.

## Experimental setup

The setup runs on Vespa version [8.673.18](https://hub.docker.com/layers/vespaengine/vespa/8.673.18/images/sha256-81251d6b162725fc7f7703d94482409c9b2d60a38b7feebbbf16932936f6556a).

For full details check the [notebook](../notebooks/beating-query-planner.ipynb).

Schema has three fields:
- `id`: an attribute with `fast-search` for filtering on `int` values;
- `embedding`: a [tensor](https://docs.vespa.ai/en/learn/glossary.html#tensor) with one indexed float dimension for the nearest neighbor search;
- `lexical`: string field with an index for `weakAnd` queries.

:::{embed}../notebooks/beating-query-planner.ipynb#schema
:remove-output: false
:remove-input: true
:::

Let's index 100,000 documents with some random data.

### Queries

The **baseline** YQL:

:::{embed}../notebooks/beating-query-planner.ipynb#yql_base
:remove-output: false
:remove-input: true
:::

```{mermaid}
graph TD
    %% Root Node
    AND1((AND))
    
    %% Right Branch
    OR((OR))
    ENN((ENN))
    WA((weakAnd))

    %% Left Branch
    Filters1((filters))
    
    %% Structure
    AND1 --> Filters1
    AND1 --> OR
    OR --> WA
    OR --> ENN
    
    %% Styling for better readability in circular nodes
    style OR fill:#f96,stroke:#333,stroke-width:2px,rx:100,ry:100
    style AND1 fill:#bbf,stroke:#333,rx:100,ry:100
    style ENN fill:#dfd,stroke:#333,rx:100,ry:100
    style WA fill:#dfd,stroke:#333,rx:100,ry:100
    style Filters1 fill:#eee,stroke:#333,rx:100,ry:100
```

The **alternative** YQL:

:::{embed}../notebooks/beating-query-planner.ipynb#yql_alt
:remove-output: false
:remove-input: true
:::


```{mermaid}
graph TD
    %% Root Node
    OR((OR))
    
    
    %% Left Branch
    AND1((AND))
    Filters1((filters))
    ENN((ENN))
    
    %% Right Branch
    AND2((AND))
    Filters2((filters))
    WA((weakAnd))

    %% Structure
    OR --> AND1
    OR --> AND2
    
    AND1 --> Filters1
    AND1 --> ENN
    
    AND2 --> Filters2
    AND2 --> WA

    %% Styling for better readability in circular nodes
    style OR fill:#f96,stroke:#333,stroke-width:2px,rx:100,ry:100
    style AND1 fill:#bbf,stroke:#333,rx:100,ry:100
    style AND2 fill:#bbf,stroke:#333,rx:100,ry:100
    style ENN fill:#dfd,stroke:#333,rx:100,ry:100
    style WA fill:#dfd,stroke:#333,rx:100,ry:100
    style Filters1 fill:#eee,stroke:#333,rx:100,ry:100
    style Filters2 fill:#eee,stroke:#333,rx:100,ry:100
```

This clearly preserves the matching logic, it just duplicates the filter `id>1` to both branches.
However, the execution of the queries is very different!

[`targetHits`](https://docs.vespa.ai/en/reference/querying/yql.html#totaltargethits) is set to relatively high 1000 to make the query to do a bit more work to emphasize the difference.

### Execution

The **baseline** query without tracing takes consistently about **12 ms**. 
While the alternative query takes about **7 ms**.
That is about 56% faster in an index of just 100k docs for the same logic: both queries have provided **5692** docs for the first phase ranking.
Why then the difference in latency?

### Traces

Given that the ranking profile is the same, and it gets the same number of hits, let's focus on the matching phase.

**Baseline** matching phase summary:

:::{embed}../notebooks/beating-query-planner.ipynb#base_matching_summary
:remove-output: false
:remove-input: true
:::

**Alternative** matching phase summary:

:::{embed}../notebooks/beating-query-planner.ipynb#alt_matching_summary
:remove-output: false
:remove-input: true
:::

Matching latency dropped by 76% (comparable to what we've seen without the tracing overhead), from **91.8 ms down to 21.8 ms**.

When looking closer, the biggest difference is in the **weakAnd** seeks: **baseline** evaluated 99,998 docs, while the **alternative** evaluated only 213 docs.
The **NearestNeighbor** seeked about the same number of documents: 100198 vs. 99998.

It looks like when the **baseline** query was executed, **weakAnd** couldn't prune any documents.
Probably because **weakAnd** is combined with **NearestNeighbor** through the `OR` operator, and given that all docs match ENN, **weakAnd** is forced to evaluate `OR` on all query terms for each document which is a lot of work!
In case you wonder, `distanceThreshold` doesn't help.

Now imagine, if you have millions of documents per content node, then depending on the query terms and filtering ratio, you now have multi second latency and probably timeouts due to unnecessary work in the matching phase.

## Discussion

What other aspects do we know?

### HNSW index

Even if the tensor field has an [HNSW](https://docs.vespa.ai/en/reference/schemas/schemas.html#index-hnsw) index, when filters are very restrictive, [Vespa executes ENN](https://blog.vespa.ai/tweaking-ann-parameters/).
And if your query has the same shape, you might experience high latencies.

When the **approximate nearest neighbor** (ANN) search is executed, then there is no such problem.
Primarily because the actual ANN hits are found during the [blueprint phase](https://github.com/vespa-engine/vespa/blob/34c0bec6ecb6d07fb646e18dde6b81213ce9c86f/searchlib/src/vespa/searchlib/queryeval/nearest_neighbor_blueprint.cpp#L149) (a.k.a., the query planning phase, i.e., before matching) and during the matching phase those hits are represented as a strict iterator over a [bitvector](https://github.com/vespa-engine/vespa/blob/17514b8f2784bd7a7065084ccbeed720e4f8d9ef/searchlib/src/vespa/searchlib/common/bitvector.h#L27), on top it gives a correct hit estimate (i.e. `<=targetHits`), which allows **weakAnd** to prune documents, which in turn makes the search fast.

### Lexical search with AND

If your lexical retriever is configured with [`grammar: "all"`](https://docs.vespa.ai/en/reference/querying/yql.html#grammar) (i.e., all terms are required to match), then you're not affected by this issue.
But probably you have low lexical recall to fight against, tradeoffs.

:::{embed}../notebooks/beating-query-planner.ipynb#and_query
:remove-output: false
:remove-input: false
:::

Same **7 ms** as in the **alternative** query.

### Disadvantages of the alternative query

- There **must** be a comment about **why** it makes sense to duplicate the filters.
- The YQL is more complicated for seemingly no good reason.
- The filters are checked twice in each `OR` branch.
- Maybe one day this optimization will be obsolete, as Vespa might improve the query planner.

### Future work

What if there are more retrievers combined with `OR` operator, e.g. `wand`?
What about [`match-phase`](https://docs.vespa.ai/en/reference/schemas/schemas.html#match-phase)?
Not everything is crystal clear for me about the Vespa query execution, but I'm getting there.

## Conclusion

When confronted with ENN and high latencies, the instinct is to throw more search threads at the problem and continue with your life.
Even though this helps to some extent, you might be better off writing your queries in a way that the query execution is more efficient.

If you're working with the hybrid search, feel free to try this query rewrite.

### P.S.: where are those nice tables coming from?

[Vespa CLI](https://docs.vespa.ai/en/clients/vespa-cli.html) has a `vespa inspect profile` [command](https://docs.vespa.ai/en/reference/clients/vespa-cli/vespa_inspect.html) which, given a Vespa response with the trace data, prints many nice summary tables.

A script that combines querying and visualizing traces in one command is [here](https://gist.github.com/dainiusjocas/8b2e7eebfe80f0d710d819632fb46b95):
```shell

#!/bin/bash

# Wrap querying and trace visualizations
# Accepts all params that `vespa query` accepts.
# Virtual pipes are needed because `vespa inspect profile` doesn't support reading from stdin.

# Usage:
# ./vespa-query-inspect --file=query.json -t 'http://localhost:8080'

# 1. Create a virtual pipe file in your current directory
mkfifo v_profile

# 2. Start the query in the background with all the params from invoking this script
# It will "hang" there until something reads from the other side.
vespa query --profile "$@" \
  --profile-file=v_profile 2> /dev/null > /dev/null &

# 3. Tell the inspector to read from that virtual file
vespa inspect profile -f v_profile

# 4. Clean up the pipe when done
rm v_profile
```

### P.P.S.: What if no filters are present?

Surprisingly, the query is fast because **weakAnd** prunes (!) docs freely even when ENN is combined through `OR`:

:::{embed}../notebooks/beating-query-planner.ipynb#no_filters_matching_summary
:remove-output: false
:remove-input: true
:::
