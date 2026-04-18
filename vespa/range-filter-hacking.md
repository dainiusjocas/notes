---
thumbnail: ../_static/foo.png
---

# Range filters are expensive, what can we do about it?

2026-04-17

TL;DR: be careful with range filters on high-cardinality fields.

## Context

So, you have a second-granularity timestamp field. Or price field.
You expose a user-controlled range filter on it.
And in your query log you see some slow queries.

## The situation

You pick one slow query, and find that the query is very basic:

:::{embed}../notebooks/failed-range-filter-hack.ipynb#baseline_query
:remove-output: false
:remove-input: true
:::

You trace it and see something like this shows up:

:::{embed}../notebooks/failed-range-filter-hack.ipynb#and_query
:remove-output: false
:remove-input: true
:::

Which says that the dictionary lookups and posting lists initialization took ~330ms!
The query took a lot of time even before the matching started!

Of course, this is a synthetic case, field cardinality is equal to the document count in an index with 10M documents.
But it illustrates the point clearly.


## What can we do about it?

You remember from the university that you can do data "roll-up": group the data by some dimension, and in this way to reduce the cardinality of the field.
That should make posting lists initialization faster.
Let's try hour-level roll-up (field cardinality reduced by ~3600x):

:::{embed}../notebooks/failed-range-filter-hack.ipynb#timestamp_hour_simple_filter
:remove-output: false
:remove-input: true
:::

Much better: from ~330 ms down to ~40 ms, ~87% decrease!
Using daily roll-up, it is ~30 ms, a tiny bit faster.

## Precision

By filtering on the hour level, we gained a lot of speed.
But how we can maintain the precision of the filter to the second level?

Let's illustrate with a simple example:

From the time axis we want to filter docs in the range `[X_min_second,Y_max_second]`, i.e. marked with `+`.
```plain
>-------------------|X_min_second|+++++++++|Y_max_second|---------------------> (time)
```

Assume, we filter on a field that only has hour granularity. By rounding second level timestamp to an hour, we overfetch to take also documents marked `x`.

```plain
>---|X_min_hour|xxx|X_min_second|+++++++++|Y_max_second|xxx|Y_max_hour|-------> (time)
```
`X_min_hour = int(X_min_second / 3600) * 3600`, (the first second of the hour of the head-end range).
`Y_max_hour = (int(Y_max_second / 3600) * 3600) + 3600` (the first second of the next hour of the tail-end range).

So, it shouldn't be a problem to filter  out `x` docs?

But if we filter on the field that has `fast-search` then we pay the latency cost of posting lists initialization.
This defeats the purpose of hour granularity.

What if we could use second granularity field without `fast-search`?
We could expect that the query planner is clever enough to first evaluate the hour level filters, and then what filter out the `x` with the non `fast-search` field filter.

But apparently as of `8.672.3` Vespa can't do that.

### Analysis

It also really depends on how you structure the `AND` filters.
I used ENN filter to act as an additional filter.

If the query filters are written in the order of `AND(hour_min>, second_min>, <second_max, <hour_max, ENN)`,
then we get the worst behavior.

If we rewrite the query as `AND(hour_min>, <hour_max, second_min>, <second_max, ENN)`, then vespa cleverly collapse the `hour_min>` and `hour_max<` filters into a single hour level filter, same with second level filters.

This is also not ideal, because 

## What if `fast-search` is removed from the field?

Then there is no time spent in posting lists initialization.
But the matching latency is dominated by dictionary lookups and iteration overhead.

:::{embed}../notebooks/failed-range-filter-hack.ipynb#nofs_iteration_overhead
:remove-output: false
:remove-input: true
:::

Crazy slow.

## Wouldn't it be better to use first-phase-ranking instead of range filters on no fast-search field?

[Using](https://vinted.engineering/2025/11/18/dense-retrieval/) the global ranking phase as a filter
