---
thumbnail: _static/hoist-tensors-hack.png
title: Getting all matching docs
date: 2026-08-17
---

## TL;DR: try to implement it by searching with some ordering by an attribute

## Context

It is fairly typical to want to get all documents that match a query, e.g.:
get all the mentions for some event, sync with some other system, etc.
While Vespa is a system optimized to get top-k best matching documents, there are still a couple of options to get all matching documents.

The options are:
1. Visiting
2. Paging with offset and limit
3. Grouping
4. Searching with some ordering by an attribute

Each has their tradeoffs.

## Visiting

This is the somewhat suggested way to get all matching documents, e.g.:
```shell
TODO
```

But its problems are:
1. Somewhat slow
2. Only basic filtering is supported, e.g.: no `nearestNeighbor` matching.
3. Fetching imported fields doesn't work.
4. Fetching many slices in parallel is resource-intensive.

## Paging

The main issue is that if your ranking is somewhat expensive, with each subsequent page Vespa has to redo all the ranking of already "seen" documents.
Which is wasteful.

## Grouping

```shell
TODO
```
Grouping supports continuations, but it is very efficient.
And the more content nodes you have, the more expensive it is, especially on the container nodes.
Also, the more docs are matching to more expensive paging gets, e.g., getting 1M docs will melt you garbage collectors.

## Searching with some ordering by an attribute

This is how you can get your stuff done, and you have all the freedom to be the most efficient.

The idea is to iterate over temporal windows of documents and manually page through them.

The main problem is that you have to manually page through the documents, and deal with all the possible errors.

Once you get into the implementation, the main concerns become either:
1. Can I miss any documents?
2. Does it produce duplicates?

Keep in mind that Vespa doesn't provide you snapshot isolation, so depending on the use case, you must be defensive.
While within one request, you can't miss any documents or get duplicates.

The main aspect for this new attribute is how evenly distributed are the values through which you're iterating.
E.g.: if you're iterating over a time field with many docs per value, e.g.: a burst in feeding activity or a `update-where`, then you risk missing docs. The remedies are either adding a filter of the already seen doc ids (if you have one); iterating with an offset; using a secondary field as a tiebreaker.

How to create such a secondary field?
Create a synthetic field. In the indexing language you have a couple of options: random, hash.

Hashing is also vulnerable to many docs with the same value. What to hash on? 
Even though document ids are unique, and they are an implicit attribute, but you can't use them for pretty much anything other than including into summaries.

One of the Vespa principles are to pay for what you use. So, it is not feng shui to add some extra functionality to documentid field.
So, we would need to ask Vespa devs to make them available in the indexing language.
If there were a way to do that, then we could take advantage of it and a simple hashing would help us.

`order by` throws away the relevance score. But 
In case you need document ranking scores

## Neat tricks

Did you know that Vespa accepts indexing docs with no fields?
```shell
```shell
echo '{
  "id": "id:doc:doc::1",
  "fields": {}
}' | jq -c | vespa feed -
```

Creating evenly distributed numeric values out of thin air:
```shell
field tiebreaker type long {
  indexing: now | attribute
}

field tiebreaker2 type int {
  indexing: random 10000000 | attribute
}
```
`now` returns a current timestamp in seconds since epoch.
`random` returns a random number between 0 and the provided value, e.g., `10000000`.

There is also `hash` expression available, but you need a good attribute to hash on.
Probably the best one is `documentid`.

How about differences between content groups?
Consider sending a stable value with the search group parameter `model.searchGroup`.

Consider that `order by` accepts the `[docid]` as a secondary sort key.

Also, keep in mind if a numeric attribute has `fast-search` enabled, then order by on that field is going to trigger the `match-phase` optimization.
This means, that the `totalCount` is not going to be accurate.
