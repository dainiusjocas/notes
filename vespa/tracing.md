---
thumbnail: _static/filters-enn-weeakAnd-query-tree.png
title: Tips and Tricks for Tracing Vespa queries
date: 2026-04-20
---

# WIP

Vespa queries are typically very fast.
But when they are arent, you better be prepared for an adventure.
And for the adventure you need to have some tools to help you along the way.
On such tool is tracing.

## Tips

Tracing is great, but it requires some experience to get the most out of it.

### Always run queries with and without traces.

Tracing adds some overhead to the query execution.
When tracing gets into the hot loop, then overhead explodes.
And your query latency might increase significantly.
It is like measuring the temperature: if you do it once in a while, it is fine.
But if you do it million of times per second, you might get "hot" because of the work you do measuring it. (My wife laughed at this explanation, so it is here.)

E.g., if you measure the query latency, and you declare that posting list preparation is dominating the latency. Then you rewrite the query, so that the majority of the work shifts towards the ranking, then trace timing will be skewed towards the ranking.  

Without tracing the query takes:
```json
{
  "querytime": 0.438,
  "searchtime": 0.438,
  "summaryfetchtime": 0
}
```

With tracing the timing reported is 
```json
{
  "querytime": 2.314,
  "searchtime": 2.314,
  "summaryfetchtime": 0
}
```
About 2 seconds slower, or 5x slower, for `select * from sources * where true limit 1` on 10 million documents.


### Use Vespa CLI to preview traces

As of version 8.676.12[^check] `vespa cli` can now preview the traces without writing them to a file first.

```shell
vespa query 'select * from sources * where true limit 1' \
  --profile \
  --profile-file - \
  -t http://localhost:8080
  | vespa inspect profile -f -
```

And you'll get a nice breakdown of what is being executed and where time is spent:

```text
┌─────────┬─────────────┐
│ total   │ 2314.000 ms │
├─────────┼─────────────┤
│ query   │ 2314.000 ms │
│ summary │    0.000 ms │
│ other   │    0.000 ms │
└─────────┴─────────────┘
found 1 search
┌────────┬───────┬───────────────┬───────────────┐
│ search │ nodes │ back-end time │ document type │
├────────┼───────┼───────────────┼───────────────┤
│      0 │     1 │   2311.557 ms │ doc           │
└────────┴───────┴───────────────┴───────────────┘
looking into search #0
slowest node was: doc[0]: 2311.557 ms
┌───────────────┬────────────┐
│ task          │ doc[0]     │
├───────────────┼────────────┤
│ global filter │   0.000 ms │
│ ann setup     │   0.000 ms │
│ matching      │ 539.856 ms │
│ first phase   │ 311.739 ms │
│ second phase  │   0.000 ms │
└───────────────┴────────────┘
looking into node doc[0]
┌─────────────┬─────────────────────────────────────────────────────────────┐
│ timestamp   │ event                                                       │
├─────────────┼─────────────────────────────────────────────────────────────┤
│    0.140 ms │ searching for 1 hits at offset 0                            │
│    0.195 ms │ Start query setup                                           │
│    0.198 ms │ Deserialize and build query tree                            │
│    0.206 ms │ Build query execution plan                                  │
│    0.219 ms │ Optimize query execution plan                               │
│    0.224 ms │ Perform dictionary lookups and posting lists initialization │
│    0.225 ms │ Prepare shared state for multi-threaded rank executors      │
│    0.238 ms │ Complete query setup                                        │
│             │ (query execution happens here, analyzed below)              │
│ 2311.554 ms │ returning 1 hits from total 10000000                        │
└─────────────┴─────────────────────────────────────────────────────────────┘
found 1 thread
slowest matching and ranking was thread #0: 851.595 ms
┌──────────────┬────────────┐
│ task         │ thread #0  │
├──────────────┼────────────┤
│ matching     │ 539.856 ms │
│ first phase  │ 311.739 ms │
│ second phase │   0.000 ms │
└──────────────┴────────────┘
looking into thread #0
┌─────────────┬──────────────────────────────────┐
│ timestamp   │ event                            │
├─────────────┼──────────────────────────────────┤
│    0.356 ms │ Start MatchThread::run           │
│    0.482 ms │ Start match and first phase rank │
│ 2311.017 ms │ Create result set                │
│ 2311.214 ms │ Wait for result processing token │
│ 2311.250 ms │ Start result processing          │
│ 2311.462 ms │ Start thread merge               │
│ 2311.462 ms │ MatchThread::run Done            │
└─────────────┴──────────────────────────────────┘
match profiling for thread #0 (total time was 539.856 ms)
┌──────────┬──────────┬─────────┬──────┬────────────────┐
│ seeks    │ total_ms │ self_ms │ step │ query tree     │
├──────────┼──────────┼─────────┼──────┼────────────────┤
│ 10000000 │  539.856 │ 539.856 │ S    │  WhiteList[1]  │
└──────────┴──────────┴─────────┴──────┴────────────────┘
first phase rank profiling for thread #0 (total time was 311.739 ms)
┌──────────┬─────────┬───────────────────────────────────┐
│ count    │ self_ms │ component                         │
├──────────┼─────────┼───────────────────────────────────┤
│ 10000000 │ 311.739 │ rank feature nativeRank           │
│        1 │   0.000 │ rank feature nativeFieldMatch     │
│        1 │   0.000 │ rank feature nativeProximity      │
│        1 │   0.000 │ rank feature nativeAttributeMatch │
└──────────┴─────────┴───────────────────────────────────┘
```

You can use that output for sharing either with the Vespa team or with your colleagues.


---
[^check]: check the version of Vespa CLI with the `vespa version` command.
