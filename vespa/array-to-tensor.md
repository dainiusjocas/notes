---
thumbnail: _static/layered-ranking-gotchas.png
title: Array to Tensor in an indexing language
date: 2026-08-06
---

## TL;DR:
Abuse the `embed` expression to convert an array of numbers into a mapped tensor.

## Problem

As of 8.731.17, there is no way to convert an array of number attribute into a mapped tensor where the label is the offset within the array.
I.e., such a schema doesn't work:

```text
schema doc {
  document doc {
    struct chunk {
      field sentiment type int {}
    }
    field chunks type array<chunk> {
      indexing: summary
    }
  }
  
  field sentiment type tensor<float>(offset{}) {
    indexing {
        input chunks |
        for_each{ get_field sentiment } |
        to_tensor |
        attribute
    }
  }
}
```

Why would you want to do this?
If you're doing [layered ranking](https://blog.vespa.ai/introducing-layered-ranking-for-rag-applications/) and using [elementwise BM25](https://docs.vespa.ai/en/reference/ranking/rank-features.html#elementwise-bm25) scores to find the best chunk, you might have some per chunk numeric attributes, e.g., sentiment score, that you can use to rank the chunks.
Elementwise scores are available as a mapped offset-labeled tensor.
So you need to somehow get your numeric attributes also into a mapped tensor with the compatible label to do `elementwise(bm25(chunks), chunk, float) * attribute(sentiment)`.

## Workaround

We can implement an embedder that takes in a string, which is a concatenated array of numbers,
splits it, parses into doubles, and then builds a tensor of the required type.

The key part of the `Embedder` implementation is fairly simple:
```java
@Override
public Tensor embed(String input, Context context, TensorType tensorType) {
    Tensor.Builder builder = Tensor.Builder.of(tensorType);
    List<Double> numbers = Arrays.stream(input.split(","))
            .map(String::trim)
            .map(Double::parseDouble)
            .toList();
    for (int offset = 0; offset < numbers.size(); offset++) {
        builder.cell(numbers.get(offset), offset);
    }
    return builder.build();
}
```
Wire in the embedder component:
```xml
<component id="numeric"
           class="lt.jocas.examples.NumberEmbedder"
           bundle="hoist-tensor">
</component>
```
And you can use it in the schema like this:
```text
field sentiment type tensor<float>(offset{}) {
  indexing {
      input chunks |
      for_each{ (get_field sentiment | to_string) || "0" } | # with default value 0 to prevent "gaps"
      join "," | # embedder splits on "," so we need to join on the same
      embed numeric | # call our embedder
      attribute
  }
}
```

Et voila!

A full demo can be found [here](https://github.com/dainiusjocas/notes/pull/31).

## Discussion

The primary solution should reach for should be to construct the tensor in the feeding pipeline, outside Vespa.
But that is not always possible due to system ownership and the stage of the PoC nuances.

Another proper way to solve it is with a custom document processor.
You know, get the value of the source field, encode it as a tensor, make sure that the types align, wire it in, and test the setup end to end.
But that is a lot of detailed and fairly complex work.

Yet another way is to shift the work into ranking expressions.
Something like this:
```text
reduce(
  tensorFromStructs(attribute(chunks), chunk_index, sentiment, float)
  * tensorFromLabelsWithOffset(attribute(chunk_index),chunk_index,offset),
  max,
  chunk_index
)
```
Gives you the tensor of the required type.
Schema needs an array of structs and a document level array to create a mapper into offsets.
But even then, Vespa needs to allocate multiple tensors at ranking time, which is not very efficient.

## Summary

Layered ranking is a powerful feature of Vespa.
But for many aspects you're on your own to implement the details.
It is an abuse of the `Embedder` interface, but it gets the job done.

What I'd like for Vespa 
