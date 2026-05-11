---
thumbnail: _static/filters-enn-weeakAnd-query-tree.png
title: Exact Nearest Neighbor Search and the `targetHits` parameter
date: 2026-05-12
---

**TL;DR:** In Vespa ENN exposes `targetHits(1 + ln(totalDocCount/targetHits))` docs for the first-phase ranking.

## Context

Vespa dynamically switches between the exact (ENN) and approximate (ANN) nearest neighbor search [depending on the filter selectivity](https://blog.vespa.ai/tweaking-ann-parameters/).
The `totalTargetHits`  [specify](https://docs.vespa.ai/en/reference/querying/yql.html#totaltargethits): 
> the wanted number of hits exposed to the first-phase ranking function in total over the content nodes evaluating the query (a group).

The `targetHits` is per content node.
But docs are a bit [vague](https://docs.vespa.ai/en/reference/querying/yql.html#nearestneighbor) 
> Note that more or less hits may actually be produced.

`More or less` exactly how many?

When ANN is executed then exactly up to `targetHits` results are exposed per content node.
No surprises here.
But what about ENN?
That formula can be derived from how the query is executed and a bit of math.

## Query execution

By default, [Vespa uses DAAT](https://docs.vespa.ai/en/querying/query-api.html#matching-ranking-grouping) (Document-At-A-Time) where the matching and the first-phase score calculation is interleaved and **not** two separate, sequential phases.
So, each doc is iterated and evaluated only once, and so the bookkeeping of the "best" docs for ENN is done.
This bookkeeping is backed by the [priority queue](https://en.wikipedia.org/wiki/Priority_queue).

During the execution, a priority queue of size N is filled with the first N docs that are iterated over by the query engine, and then if a doc at N + k position is "better" than the worst doc in the priority queue, that worst doc is removed, the current doc is added and the elements to the queue which is also reordered.

The docs exposed to the first-phase ranking are the ones that at some point in time were added to the priority queue.

## Math

When you iterate through a randomly shuffled list (and high-dimensional vectors can be thought as being randomly ordered) of length N and keep track of the "current maximum," you are counting how many times a new maximum is found.
That is exactly the N-th [harmonic number](https://en.wikipedia.org/wiki/Harmonic_number): 
$$
H_N = E[K] = \sum_{i=1}^N \frac{1}{i}
$$
The $\frac{1}{i}$ is a probability that the current maximum is the i-th element.
Intuitively, the more elements were already checked, the lower is the probability that the current element is the maximum.
For large numbers it is equal to $ln(N) + {\displaystyle \gamma }$, where $\gamma$ is the [Euler-Mascheroni constant](https://en.wikipedia.org/wiki/Euler%27s_constant).

When we need to track the `targetHits` best elements, then the expected number of the priority queue "updates" is
$$
E[K] = targetHits + targetHits \sum_{i=targetHits + 1}^N \frac{1}{i} = targethits(1 + ln(N/targetHits))
$$

## Putting it all together

With ENN the number exposed to the first-phase ranking is `targetHits(1 + ln(totalDocCount/targetHits))` per content node.
So, the higher the `targetHits` the more docs are exposed to the first-phase ranking.

## Sidequest: `targetHits=1`

The task can be reformulated as finding the maximum in the list of random numbers.
Comming back to Vespa land, when `targetHits=1` then formula boils down to: `1 + ln(totalDocCount)`.
`1` is because the first element always gets into the priority queue during the search.
The `ln(totalDocCount)` captures the sum of probabilities that the n-th element is the maximum.
When the list has only one element then `ln(1)=0`.
All is good.

## Experiments

In vespa response the [`totalCount`](https://docs.vespa.ai/en/reference/querying/default-result-format.html#totalcount) is 
> The value is the number of hits after first-phase dropping.

With 10M single float-dimensional vectors, for various `targetHits` matches the formula very closely:

```plaintext
targetHits=1 totalCount: 14 estimate=17.11809565095832
targetHits=10 totalCount: 131 estimate=148.15510557964274
targetHits=100 totalCount: 1253 estimate=1251.2925464970228
targetHits=1000 totalCount: 10284 estimate=10210.340371976183
targetHits=10000 totalCount: 79194 estimate=79077.55278982136
targetHits=100000 totalCount: 560368 estimate=560517.0185988091
targetHits=1000000 totalCount: 1712078 estimate=3302585.092994046
```
The main formula calculates the estimate.
Full setup is [here](../notebooks/failed-range-filter-hack.ipynb).

## Q&A

### What about filters?

The formula then becomes `targetHits(1 + ln(totalDocCount*filterFactor/targetHits))` where `filterFactor` is [0,1].

### What about ths `distanceThreshold` parameter?

It acts more or less like a filter.
E.g. if `distanceThreshold=0.5` matches only about 50% of the docs, then the formula becomes `targetHits(1 + ln(totalDocCount*filterFactor/targetHits))`.
So, the lower the distance threshold the fewer docs are exposed to the first-phase ranking.

### What about the match-phase limiter?

The `totalDocCount` becomes how many docs the match phase limiter iterated over.
E.g., if match-phase max-docs is `10000` and `targetHits` is 100, then (ignoring the estimation part) the number becomes `100(1 + ln(10000/100))=560,5170185988`.
Yes, it is corpus size independent.

### Why I don't see the `totalCount` from your formula with the `unranked` rank profile?

Yes, the `totalCount` is equal to the number of docs in the content node by default.
It is because the `unranked` rank profile [also sets](https://github.com/vespa-engine/vespa/blob/master/config-model/src/main/java/com/yahoo/schema/UnrankedRankProfile.java) also sets `keep-rank-count: 0` and `rerank-count: 0`.
[Set the](https://docs.vespa.ai/en/reference/api/query.html#ranking.keeprankcount) `ranking.keepRankCount=10000` HTTP parameter and the `totalCount` will follow the formula.

## Conclusion

I hope this helps, and maybe the formula is going to be added to the official Vespa docs.
