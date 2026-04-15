# Helping Vespa query planner to do hybrid search

TL;DR: in Vespa when the exact nearest neighbor search is combined with weakAnd
it is better to construct query like `(filters AND nearestNeighbors) OR (filters AND weakAnd)` instead of `(filters AND (nearestNeighbor OR weakAnd))`.

## Context 

Hybrid search typically means combining vector with lexical retrievers.
Also, a fact of life is that anything useful typically also requires good ol' filtering.
So, it is natural to write queries like:

```sql
select * 
from sources *
where filters AND (nearestNeighbor OR weakAnd)
```

However, when `nearestNeighbor` is doing exact nearest neighbors (ENN) search then, you might experience elevated latencies, maybe even timeouts.

## Experimental setup

Schema:

:::{embed}../notebooks/beating-query-planner.ipynb#schema
:remove-output: false
:remove-input: true
:::

It has three fields:
- `id` for filtering on int values
- `embedding` for nearest neighbor search
- `lexical` for weakAnd queries.

We've indexed 100,000 documents with some random data.

For full details check the [notebook](../notebooks/beating-query-planner.ipynb).

## Query execution

:::{embed}../notebooks/beating-query-planner.ipynb#yql_base
:remove-output: true
:::
when this query is executed, the matching phase has a problem: weakAnd can't prune any documents because it is in `OR` with exact nearest neighbor search which matches all documents (yes, `distanceThreshold` doesn't help).

See the summary of matching traces:

:::{embed}../notebooks/beating-query-planner.ipynb#base_matching_summary
:remove-output: false
:remove-input: true
:::

However, if we rewrite the query like this:
:::{embed}../notebooks/beating-query-planner.ipynb#yql_alt
:remove-output: true
:::
This clearly preserves the matching logic, it just duplicates the filter `id>1` to both branches.
However, the execution is vastly different!


:::{embed}../notebooks/beating-query-planner.ipynb#alt_matching_summary
:remove-output: false
:remove-input: true
:::

The matching latency went from 91.847 ms to 21.827 ms.

The main difference is in the number of items evaluated by the weakAnd: down from 99998 to 213 documents!

Now imagine, if you have millions of documents per content node, and you now have timeouts for matching phase.
Restrictive filters help, but only to some extent.

## HNSW index

Even if the field has an HNSW index, when filters are very restrictive, Vespa executes ENN, which might have exactly this problem.

## Downsides

The yql is more complicated for seemingly no good reason.
The filters are executed twice.

## Conclusion

If you happen to work with hybrid search, feel free to try this optimization.

## P.S.: where are those nice tables coming from?

Vespa CLI has a `vespa inspect profile` that given a Vespa response with the trace data prints those nice tables.

My script that combines querying and visualizing in one command is [here](https://gist.github.com/dainiusjocas/8b2e7eebfe80f0d710d819632fb46b95):
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
