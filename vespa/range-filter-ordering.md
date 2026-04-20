---
title: Range Filter Conditions Reordering
date: 2026-04-21
thumbnail: _static/ordering-range-filters-by-field.png
---

## TL;DR

As of Vespa `8.672.3`, it is better to group range filters by the field:
```sql
(field_a >= X AND field_a <= Y) AND (field_b > X AND field_b < Y)
```

## Context

Filtering on high-cardinality[^high-cardinality] fields is expensive.
[^high-cardinality]: Let's say that by high cardinality, we mean a field that on average the value has less than five documents.
For such fields the posting list creation will take a lot of time if the range query that matches millions of documents.
E.g., a query with a single filter that matches ~10M distinct values takes about 374 ms (without tracing enabled). 

When tracing slow queries, you might see something like this:

:::{embed}../notebooks/failed-range-filter-hack.ipynb#and_query
:remove-output: false
:remove-input: true
:::

Note the `Perform dictionary lookups and posting lists initialization` entry which took about 364 ms.
When trace is turned off, the timing matches: most of the time was spent in preparations before matching.

## Optimization

A natural trick is to reduce the granularity of the field.
E.g., given a timestamp field with the second granularity, round it to hours.
This reduces the cardinality of the field by a factor of 3600[^hour].
[^hour]: 3600 seconds in an hour, 60 seconds * 60 minutes.
It reduces the posting list fetch time in our example from 364 ms to 40 ms, ~89% decrease!

Not only the query overhead is large, but also the b-tree[^b-tree] on the attribute is massive.
E.g. ~25M docs in a content node with a field `fast-search` weights about 459MB, while the raw field is about 186 MB. The hourly field weights 200 MB. 

[^b-tree]: A b-tree is a data structure that is used to store data in a sorted order.

## The problem

However, by rounding we lose precision.
What if we could use the `fast-search` field to filter on the hour and then additionally filter on the second granularity field but without the `fast-search`?
This would eliminate dictionary lookups and posting lists initialization on the high-cardinality field.
While the lower cardinality field would do the heavy lifting of filtering.

The baseline query with range filters on both ends:
:::{embed}../notebooks/failed-range-filter-hack.ipynb#baseline_grouped_filters_yql
:remove-output: false
:remove-input: true
:::

The latency is ~190 ms.

:::{embed}../notebooks/failed-range-filter-hack.ipynb#baseline_grouped_trace
:remove-output: false
:remove-input: true
:::

The initial query that used combined hour and second fields looked like this:

:::{embed}../notebooks/failed-range-filter-hack.ipynb#non_grouped_filters_yql
:remove-output: false
:remove-input: true
:::

:::{div} my-custom-panel
:class: specialized-border highlight-effect
```{mermaid}
graph TD
    %% Root Node
    AND1((AND))
    
    %% Left Branch
    AND3((AND))
    gt_hour(timestamp_hour>)
    lt_hour(timestamp_second> )
    
    %% Right Branch
    AND2((AND))
    gt_second(timestamp_hour<)
    lt_second(timestamp_second<)

    %% Structure
    AND1 --> AND3
    AND1 --> AND2
    AND2 --> gt_second
    AND2 --> lt_second
    AND3 --> gt_hour
    AND3 --> lt_hour
    
    %% Styling for better readability in circular nodes
    style AND1 fill:#bbf,stroke:#333,rx:100,ry:100
```
:::

And the latency without trace was 165 ms.

The matching breakdown:

:::{embed}../notebooks/failed-range-filter-hack.ipynb#non_grouped_request_matching_trace
:remove-output: false
:remove-input: true
:::

Tracing for this query adds a lot of overhead, but the structure here is important.
Anyway, this looked suspiciously slow.

When the query was rewritten to group range filters by field:

:::{embed}../notebooks/failed-range-filter-hack.ipynb#grouped_filters_yql
:remove-output: false
:remove-input: true
:::

:::{div} my-custom-panel
:class: specialized-border highlight-effect
```{mermaid}
graph TD
    %% Root Node
    AND1((AND))
    
    %% Left Branch
    AND3((AND))
    gt_hour(timestamp_hour>)
    lt_hour(timestamp_hour<)
    
    %% Right Branch
    AND2((AND))
    gt_second(timestamp_second>)
    lt_second(timestamp_second<)

    %% Structure
    AND1 --> AND3
    AND1 --> AND2
    AND2 --> gt_second
    AND2 --> lt_second
    AND3 --> gt_hour
    AND3 --> lt_hour
    
    %% Styling for better readability in circular nodes
    style AND1 fill:#bbf,stroke:#333,rx:100,ry:100
```
:::

The latency dropped to ~119 ms!

:::{embed}../notebooks/failed-range-filter-hack.ipynb#grouped_request_matching_trace
:remove-output: false
:remove-input: true
:::

And most importantly, the matching breakdown now shows that instead of having four filters, now we have only two:
for each field.
Vespa managed to collapse the filters!

## Summary

By reducing the field cardinality, using `fast-search` strategically, and rearranging range filters by field, we reduced the latency from ~187 ms down to ~119 ms (-36%)!
All while keeping the memory footprint at the same level.
But the query now is more complicated.
What other rewrites yield better latency with range filters?

## P.S. Why simply dropping `fast-search` is not good enough?

If we simply drop the `fast-search` attribute, the query latency drops to ~ 73 ms!
Sounds great!

However, if used in combination with other retrievers such as ENN, a rage filter on a field that doesn't have `fast-search` adds too much overhead.
E.g., a brute force ENN search over 10 M docs takes about ~60 ms.
If a filter that matches about 3 M docs is used, then latency jumps to ~80 ms.
If the selectivity is even lower, then latency grows even further.
When using the field with `fast-search` with 3 M selectivity, then latency is ~135 ms!
When filtering on the hour granularity field, the latency is 47 ms.

## P.P.S. How about inverting filters?

Doesn't help, posting lists initialization still takes a lot of time.
Especially if the range is limited on both ends.
