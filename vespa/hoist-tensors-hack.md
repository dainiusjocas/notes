---
thumbnail: _static/hoist-tensors-hack.png
title: Hoist Tensors with an Embedder Hack
date: 2026-08-04
---

## TL;DR: 

Serialize tensor to string, use `Embedder` to deserialize, and store in the synthetic field.

## Problem

Say that embeddings are fed into Vespa, and your schema already looks like this:

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
You need to support `nearestNeighbor` (NN) queries on the `embedding` data.
To support NN queries the embedding tensor must be a top-level document field with one indexed dimension and optional mapped dimensions.[^support]
[^support]: As of 8.731.17, Vespa doesn't support NN queries on tensors that are inside an array of structs. You can only store it in the docstore.
In this particular case the top level field with type `tensor<float>(offset{}, x[1])`.
How to do it, if you can't change the data shape upstream?

One option is to write a document processor that hoists the tensor into a top-level document on which you can slap an HNSW index.
But the docproc has a lot of finicky work to do!
Also, testing of such docproc is not trivial.

We want to get stuff done in the indexing language!

## Hack

Let's see how we can bend Vespa Embedder abstraction to help us.
An [`Embedder`](https://github.com/vespa-engine/vespa/blob/b5c6717954f9ff8bf08246346e0e9e6aee0ba22f/indexinglanguage/src/main/java/com/yahoo/vespa/indexinglanguage/expressions/EmbedExpression.java#L44) takes a string (or a list of strings) and converts them into a tensor.
When a list of strings is passed, then the output is a mixed  tensor with one mapped dimension for the offset and one indexed dimension for the actual embedding.[^dimensions]
[^dimensions]: [There are several valid](https://github.com/vespa-engine/vespa/blob/b5c6717954f9ff8bf08246346e0e9e6aee0ba22f/indexinglanguage/src/main/java/com/yahoo/vespa/indexinglanguage/expressions/EmbedExpression.java#L396-L406) dimensionality targets for the tensor.
Nice, offset handling are already done for us.

So, what if in the indexing language we serialize the tensors we have in the array of structs into  astring, then pass that string into a custom `Embedder`, in the embedder we just deserialize the tensor, profit!

The demo application is [here](https://github.com/dainiusjocas/notes/pull/30).

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
under the `<container>` tag.

The component points to the `DeserializerEmbedder` class:
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

Which is packaged in to the `hoist-tensor` bundle as specified in the `pom.xml`.
Et voilà!

### Offsets and empty values 

Note that `offset` is not going to have "gaps" in the tensor. because the `embed` expression [filters out empty strings](https://github.com/vespa-engine/vespa/blob/b5c6717954f9ff8bf08246346e0e9e6aee0ba22f/indexinglanguage/src/main/java/com/yahoo/vespa/indexinglanguage/expressions/EmbedExpression.java#L420-L429).
So, in case your chunk struct doesn't have an embedding, then make sure to put a default string in there, e.g.:
```text
for_each{ (get_field embedding | to_string) || "tensor<float>(x[1]):[0]" } |
```
Ugly, requires special handling later in the ranking, but works.

## Demo

Let's feed a document with three chunks and immediately visit it::
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
Which gives:
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
Note that mapped dimension labels are offsets within the source document, no relation to values.

And your NN queries now work:

```shell
vespa query \
  'select * from sources doc where {targetHits: 1}nearestNeighbor(embeddings, query)' \
  'input.query(query)=[1.0]' \
  'ranking.profile=default'
```

The full schema:
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
  field embeddings type tensor<float>(offset{}, x[1]) {
    indexing {
      input chunks |
      for_each{ (get_field embedding | to_string) || "tensor<float>(x[1]):[0]" } |
      embed deserializer |
      attribute
    }
  }
  rank-profile default {
    inputs {
      query(query) tensor<float>(x[1])
    }
  }
}
```

## Conclusion

By wiring in a custom embedder that wraps `Tensor.from` method, we've got the indexing language to do all the complicated work of hoisting a tensor from an array of structs for us.
Having offsets as a tensor mapped dimension label can help us with combining `elementwise` [scores](https://blog.vespa.ai/introducing-layered-ranking-for-rag-applications/).
An obvious downside is that the feed container node uses more CPU to serialize and deserialize the tensor value.
But overall, a nice hack!
