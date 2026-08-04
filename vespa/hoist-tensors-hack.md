---
thumbnail: _static/hoist-tensors-hack.png
title: Hoist Tensors with an Embedder Hack
date: 2026-08-04
---

## TL;DR: Embedder Converts Strings to Tensor

## Problem

Imagine that embeddings are fed into Vespa, and your schema already looks like this:

```text
schema doc {
  document doc {
    struct chunk {
      field embedding type tensor<float>(x[1]) {}
    }
    field chunks type array<chunk> {
      indexing: summary
    }
  }
}
```
you need to support nearestNeighbor queries, and you can't really change the data shape upstream.
Currently Vespa doesn't support NN queries on tensors that are inside an array of structs.
To support NN queries the tensor must be a top-level field with one indexed dimension and optional mapped dimensions.
In this particular case the top level field with type `tensor<float>(offset{}, x[1])`.
How to do it?

One option is to write a document processor that hoists the tensor into a top-level document on which you can put an HNSW index.
But the docproc has a lot of finicky work to do.
Also testing of such docproc is not trivial.

We want to get stuff done in the indexing language!

## Hack

Let's see how we can bend Vespa Embedder abstraction to help us.
Embedder takes a string (or a list of strings) and converts them into a tensor.
When a list of strings is passed, then the output tensor is mixed with one mapped dimension for offset and one indexed dimension for the actual embedding.
Nice, offset calculations are already done for us.

So, what if in the indexing language we serialize the tensors we have in the array of structs into string, pass that into a custom `Embedder`, in the embedder we just deserialize the tensor, and voila!

The demo application is here.

### Implementation details

Let's pretend that we have successfully wired in the custom `Embedder` and start by writing the indexing expression first:
```text
field embeddings type tensor<float>(offset{}, x[1]) {
  indexing {
    input chunks |
    for_each{ get_field embedding | to_string } |
    embed deserializer |
    attribute
  }
}
```
For this to work, the services.xml should have:
```xml
<component id="deserializer"
           class="lt.jocas.examples.DeserializerEmbedder"
           bundle="hoist-tensor">
</component>
```
under `<container>` tag.

Which points to the `DeserializerEmbedder` class:
```java
public class DeserializerEmbedder implements Embedder {
    @Override
    public List<Integer> embed(String text, Context context) {
        return List.of();
    }
    @Override
    public Tensor embed(String text, Context context, TensorType tensorType) {
        return Tensor.from(text);
    }
}
```

Which is packaged in to the `hoist-tensor` bundle.
Et voilà!

Note that `offset` is not going to have "gaps" in the tensor. because the `embed` expression [filters out empty strings](https://github.com/vespa-engine/vespa/blob/b5c6717954f9ff8bf08246346e0e9e6aee0ba22f/indexinglanguage/src/main/java/com/yahoo/vespa/indexinglanguage/expressions/EmbedExpression.java#L420-L429).
So, in case your chunk struct doesn't have an embedding, then make sure to put a default string there, e.g.:
```text
for_each{ (get_field embedding | to_string) || "tensor<float>(x[1]):[0]" } |
```

## Demo

Let's feed a document with three chunks into Vespa:
```shell
echo '
{
  "id": "id:doc:doc::1",
  "fields": {
    "chunks": [
      {"embedding": [3]},
      {"embedding": [2]},
      {"embedding": [1]}
    ]
  }
}' \
| jq -c | vespa feed -
vespa visit --field-set="doc:embeddings" | jq
```
Whic gives:
```json
{
  "id": "id:doc:doc::1",
  "fields": {
    "embeddings": {
      "type": "tensor<float>(offset{},x[1])",
      "blocks": {
        "0": [3.0],
        "1": [2.0],
        "2": [1.0]
      }
    }
  }
}
```
Note that mapped dimension labels are offsets within the source document.

And your NN queries now works:

```shell
vespa query \
  'select * from sources doc where {targetHits: 1}nearestNeighbor(embeddings, query)' \
  'input.query(query)=[1.0]' \
  'ranking.profile=default'
```

## Conclusion

By wiring in a custom embedder that wraps `Tensor.from` method, we've got the indexing language to do all the complicated work of hoisting a tensor from an array of structs for us.
An obvious downside is that the feed container node uses more CPU to serialize and deserialize the tensor value.
But overall, a nice hack!
