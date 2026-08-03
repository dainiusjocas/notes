---
thumbnail: _static/nested-data-modeling.png
title: Layered Ranking Gotchas 
date: 2026-08-01
---

## TL;DR: 

You better closely follow demo applications, otherwise you'll scratch your head a lot.

## Introduction

Vespa [layered ranking](https://blog.vespa.ai/introducing-layered-ranking-for-rag-applications/) approach can simplify search for RAG application and make it more efficient: no metadata duplication, faster exact nearest neighbor search, correct document level grouping counts, etc.
However, it relies on several somewhat new features which have some gotchas.

## `select-elements-by` 

Per field summary `select-elements-by` function can have only one name.

Say you have a field:
```sd
field chunks type array<string> {
  indexing: index | summary
  summary {
      select-elements-by: best_chunks
  }
}
```
Then in named document summaries you can't specify `select-elements-by` with any other function name, e.g., adding this document summary

```plain
document-summary worst {
  from-disk
  summary chunks {
    source: chunks
    select-elements-by: worst_chunks
  }
}
```
would fail to deploy with a validation error:
```text
Error: invalid application package (status 400)
Invalid application:
Conflicting summary elements selectors. summary 'chunks' in document-summary 'worst' in schema 'doc' is already defined as summary 'chunks' in field 'chunks'. A field with the same name can not have different element selectors in different summary classes
```

But in named document summaries you can still use that field, no worries.
Simply omit `select-elements-by`[^omit], i.e., this works fine:
```text
document-summary demo {
  summary chunks {}
}
```
[^omit]: or use the same function name if you like to be explicit.

Lessons learned:
- summary elements and ranking profiles just got coupled;
- it is possible to override the function used for `select-elements-by` in (inner) rank profiles to get some flexibility/debugability;
- use somewhat generic function name for `select-elements-by` because if you're not after `best_chunks`, e.g., you want to get only the first and the last chunks, then something like `chunk_selector_fn` is a better idea;
- Different fields can have different `select-elements-by` function names.

## `select-elements-by` function name must be used in `summary-features` block

Deploying an application that doesn't use the function used in `select-elements-by` fails with a validation error:

```text
Uploading application package... failed
Error: invalid application package (status 400)
Invalid application:
For schema 'doc', document-summary 'default', summary field 'chunks':
For schema 'doc', document-summary 'default', summary field 'chunks':
select-elements-by summary feature 'best_chunks' is not defined for source field 'chunks'.
```

Lessons learned:
- create a default/placeholder/base rank profile that defines the functions used in `select-elements-by` and declares `summary-features` block.

## Rank profiles 

Once the app deploys, there are several ways how to fail getting `chunks`:
- Querying with a rank profile that doesn't declare or inherit the `select-elements-by` function returns no chunks at all.
- If a rank profile doesn't specify summary-features at all, then field data is not returned in the summary.

Lessons learned:
- Selecting chunks is tightly coupled with the rank profile.
- It is enough for one unrelated rank profile to declare summary features, and deploying no longer complains.

## Misc tips & tricks

It is possible to pass in the tensor to select chunks with the query parameter. E.g.
```text
rank-profile demo3 {
  inputs {
    query(chunk_selection) tensor(offset{}):{"0": 1.0, "1": 1.0}
  }
  function best_chunks() {
    expression: query(chunk_selection)
  }
  summary-features: best_chunks
}
```
See that the `best_chunks` function simply wraps the query input parameter.
Then:
```shell
vespa query 'yql=select * from sources * where true' \
 'ranking.profile=demo3' \
 'input.query(chunk_selection)={"1": 1.0}'
```
Selects the second chunk[^offsets].
[^offsets]: offsets start with 0.

Note: the `query(chunk_selection)` is declared with a default value, i.e., it always selects something if query parameter is not specified.

If for some reason you need to get all the chunks, then the new trick with `tensorFromLabelsWithOffset` can help.
Most likely you're going to have some metadata about each chunk, e.g., its index in the array:
```text
struct chunk {
  field idx type int {}
}
field meta type array<chunk> {
  indexing: summary
  struct-field idx {
    indexing: attribute
  }
}
```
Then querying with a rank profile `demo4`:
```text
rank-profile demo4 {
  function best_chunks() {
    expression: reduce(tensorFromLabelsWithOffset(attribute(meta.idx), "label", "offset"), max, label)
  }
  summary-features: best_chunks
}
```
Would return all the chunks.
Of course, you might as well have another field[^synthetic] with a copy of the data for such a summary.
[^synthetic]: a synthetic field is a good candidate.

## Summary

Once you get it working, the sky is the limit.
My suggestion is to specify some `default` rank profile per schema[^schema], so that the actual rank profiles used for search can simply inherit it and specify the nuanced behavior. 

[^schema]: maybe even directly in the schema file. A `.profile` file is also an option, but then how to name it so that it is obvious that it is the base rank profile?
