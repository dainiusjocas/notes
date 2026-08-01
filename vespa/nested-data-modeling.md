---
thumbnail: _static/nested-data-modeling.png
title: Nested Data Modeling 
date: 2026-08-01
---

## TL:DR: 

Inside schema's `document` block should be your feeding data shape; searchable fields should be top-level synthetic fields filled from document fields.

## The Issue

Say your documents are shaped like this[^note]
[^note]: Note the `chunks` array that has multiple objects.
```json
{
  "title": "Title",
  "chunks": [
    {
      "text": "intro",
      "chunk_index": 1
    },
    {
      "text": "outro",
      "chunk_index": 2
    }
  ]
}
```

The Vespa schema that accepts that data shape is:
```sd
schema doc {
  document doc {
    field title type string {
      indexing: index
    }
    struct chunk {
      field text type string {}
      field chunk_index type int {}
    }
    field chunks type array<chunk> {
      indexing: summary
      struct-field chunk_index {
          indexing: attribute
          attribute: fast-search
      }
      struct-field text {
        indexing: index
      }
    }
  }
}
```
When deployed[^deploys], Vespa gives a warning:
```text
WARNING For cluster 'content', schema 'doc': The following complex fields have struct fields with 'indexing: index' which is not supported and has no effect: chunks (chunks.text). Remove setting or change to 'indexing: attribute' if needed for matching.
```
I.e. `chunks.text` field is not full-text searchable.
[Quick consultation with the docs](https://docs.vespa.ai/en/reference/schemas/schemas.html#array) 
> Restrictions: ... Some parts of struct arrays can be searched
> ...
> And `index` is only supported in the streaming search mode.

Within an `array<struct>` only the primitive types[^types] are searchable[^sameelement].
[^types]: no arrays, no maps, no structs, no tensors.
[^sameelement]: probably with [sameElement](https://docs.vespa.ai/en/reference/querying/yql.html#sameelement)

## The Solution

Hoist the fields from the nested fields to the top-level synthetic fields.

```sd
shema doc {
  document doc {
    field title type string { 
      indexing: index
    }
    struct chunks {
      field text type string {}
      field sentiment type float {}
    }
    field chunks type array<chunks> {
      indexing: summary
      struct-field sentiment {
        indexing: attribute
        attribute: fast-search
    }
  }
  # outside `document`
  field chunk_text type array<string> {
    indexing: input chunks | for_each{ get_field text } | index
  }  
}
```

The synthetic `chunk_text` field is searchable.

```shell
vespa query 'select * from sources * where text contains "intro"'
```

### Aliasing

Also, synthetic field names can have aliases.
```sd
field text type array<string> {
  indexing: input chunks | for_each{ get_field text } | index
  alias: chunk.text
}
```
Then this query works.
```shell
vespa query 'select * from sources * where chunk.text contains "intro"'
```

Somewhat unfortunate is that an alias, if the alias shadows the field name, e.g. `chunks.text`, even though the app deploys, but search returns no hits[^probably].
[^probably]: probably because the name points to a non-searchable field.
But if we use the schema naming convention that struct is singular, field name is plural, then a searchable alias can be constructed as `[struct_name].[field_name]`, e.g. `chunk.text`.

### Deeper

Say your chunk can have an array of something, like entities.
Then it's natural to have a synthetic field for the array of strings.
E.g.[^pipe]:
[^pipe]: yes, pipe must be at the end of the line
```sd
field entities type array<string> {
  indexing {
    input chunks |
    for_each {
      get_field entities |
      join " " | # join the array of chunk entities
    }
    join " " |   # join joined entities from all chunks
    split " " |  # split into individual entity strings
    attribute    # exact matching
  }
}
```
Even though matching works, but we lose the information which chunk contained the matched entity.
That is solvable, but let's keep it simple for now.

## Complex

Synthetic fields can be a combination of multiple fields.
Also, nobody prevents you from having multiple synthetic fields that use the same input data and provide different matching and/or ranking logic.

## Limitations

Chunk level tensors can't be easily moved to the top-level synthetic field.
Say, your embeddings are fed into Vespa.
There is no way to simply assign a mapped dimension to a tensor in the indexing language[^embedders].
[^embedders]: but embedders can do it!

One workaround is to write your own document processor that collects tensors and shapes them.

Even crazier approach would be to write a custom embedder.
The scheme is:
```sd
document {
  struct chunk {
    field chunk_tensor type tensor<float>(x[1]) {}
  }
  field chunks type array<chunk> {
    indexing: summary
  }
}
field chunk_tensors type tensor<float>(offset{}, x[1]) {
  indexing {
    input chunks |
    for_each{ get_field chunk_tensor | to_string } |
    embed tensor_parser |
    attribute
  }
}
```
The custom embedder now only has to parse the serialized tensor e.g. `"tensor<float>(x[1]):[1.0]"` into the right dimensions.
However, it is on task for the reader to write and wire in the `tensor_parser` embedder [^claude].

## Summary

Vespa is great as it allows you to accommodate the shape of the incoming data and then define how it is searchable inside the schema[^searchapi], i.e., to decouple feeding from querying.
Even if input data is deeply nested, it still can be made searchable.
Of course, if top-level document fields should be searchable, then there is no need to create synthetic fields.
I've had multiple Vespa schema creation sessions, and this strategy worked wonders.

[^searchapi]: Query API can also take almost any HTTP request and within a custom searcher construct the query from what was in the request.
[^deploys]: yes, strangely the schema deploys!
[^claude]: LLM is your friend ;)
