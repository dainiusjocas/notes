---
thumbnail: _static/silent-append-failure.png
title: Silent Append Failure 
date: 2026-08-19
---

## Corner case

As of 8.738.17, when a partial update appends to an array field and an embedding is calculated from that field, the synthetic tensor field is silently not updated.

Note that the embedder is still invoked, but the calculated values are silently dropped.

## Concrete example

Say you calculate embeddings inside Vespa with a model.
You wire in an embedding model into the `services.xml`.
```xml
<component id="e5" type="hugging-face-embedder">
  <transformer-model url="https://huggingface.co/intfloat/e5-small-v2/resolve/main/model.onnx"/>
  <tokenizer-model url="https://huggingface.co/intfloat/e5-small-v2/raw/main/tokenizer.json"/>
  <prepend>
    <query>query:</query>
    <document>passage:</document>
  </prepend>
</component>
```
You configure the schema to calculate the embeddings, just like in the numerous sample apps.

```text
schema doc {
  document doc {
    struct chunk {
      field text type string {}
      field sentiment type int {}
    }
    field chunks type array<chunk> {
      indexing: summary
    }
  }
  # truncated to just 1 dense dimension for simplicity
  field embeddings type tensor<float>(offset{}, x[1]) {
    indexing {
      input chunks |
      for_each{ get_field text } |
      embed e5 |
      attribute
    }
    attribute {
        distance-metric: angular
    }
  }
  field text type array<string> {
    indexing: input chunks | for_each{ get_field text } | index
  }
}
```

And then you feed documents partially, e.g., you do chunking and some processing of text outside Vespa.
Then you try to upsert the doc like this:

```shell
curl -v -X PUT -H "Content-Type:application/json" --data '
  {
  "create": true,
  "update": "id:doc:doc::1",
  "fields": {
    "chunks": {
      "add": [
        {"text": "three", "sentiment": 20}
      ]
    }
  }
}' \
  http://localhost:8080/document/v1/doc/doc/docid/1
```

And you check the content:
```shell
vespa visit --field-set="[all]" | jq
```
Which gives:
```json
{
  "id": "id:doc:doc::1",
  "fields": {
    "chunks": [
      {
        "text": "three",
        "sentiment": 20
      }
    ],
    "text": [
      "three"
    ]
  }
}
```
Note: no `embeddings` field!
While `text` field is correctly appended.

See the full app here.

Why? Most likely because [here](https://github.com/vespa-engine/vespa/blob/a63787265645cb9f7ed3599c29e003b2b3851eca/indexinglanguage/src/main/java/com/yahoo/vespa/indexinglanguage/FieldUpdateFieldValues.java#L136-L138) the value is not handled.
And if the label is an offset, then there is nothing to do:
offset can only be set to the offsets within the partial update, i.e., 0, 1... 
That is because a document processor can't know what is the actual offset, as it is known only in the content node.

### Corner case of the corner case

When a synthetic field is not a tensor, then an update happens properly.
Just like we've seen in the example above.

## Workarounds

Is there a trick how to get the synthetic tensor field updated?
Yes, trigger reindexing and your embeddings will appear.
```shell
curl -X POST  http://localhost:19071/application/v2/tenant/default/application/default/environment/default/region/default/instance/default/reindex | jq
vespa deploy -w 60
```

Of course, the problem disappears if you feed full documents into Vespa.

## Discussion

How could this failure mode be less silent? 
Maybe there could be some signaling in the feed response headers that the update is not full?
Something similar to the `X-Vespa-Ignored-Operation` header.

Maybe embedder could look up the length of the array in the content node and lock the document for updates?
Probably a bad idea.

Maybe `embed` could have an alternative mode to construct the tensor labels, and insead of offset it could use a hash on the content of the input?
In this case, your app shouldn't care about what the actual labels are.
Also, be careful with the tensor label cardinality problem: at high cardinality bad things happen. 

## Conclusion

Be aware that partial updates have some nasty corner-cases.
Use partial updates
