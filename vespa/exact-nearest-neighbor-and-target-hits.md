---
thumbnail: _static/brute-force-search.png
title: How does targetHits impact the number of matches in the ENN Search?
date: 2026-05-12
---

**TL;DR:** In Vespa ENN exposes approximately `targetHits(1 + ln(totalDocCount/targetHits))` docs to the first-phase ranking.

## Context

Vespa dynamically switches between the exact (ENN) and approximate (ANN) nearest neighbor search [depending on the selectivity of the query filters](https://blog.vespa.ai/tweaking-ann-parameters/).
The `totalTargetHits`  [specify](https://docs.vespa.ai/en/reference/querying/yql.html#totaltargethits): 
> the wanted number of hits exposed to the first-phase ranking function in total over the content nodes evaluating the query (a group).

The `targetHits` is per content node.
But docs are a bit [vague](https://docs.vespa.ai/en/reference/querying/yql.html#nearestneighbor) on how many hits are produced:
> Note that more or less hits may actually be produced.

`More or less` exactly how many?

When ANN is executed then exactly up to `targetHits` results are exposed per content node.
So, it can be only less.
No surprises here.
But what about ENN?
Let's find out the formula by analyzing how queries are executed and learn a bit of math.

## Query execution mechanism

By default, [Vespa does DAAT](https://docs.vespa.ai/en/querying/query-api.html#matching-ranking-grouping) (Document-At-A-Time) where the matching and the first-phase score calculation are interleaved and **not** two separate, sequential phases.
As each doc is iterated and evaluated only once, and so the bookkeeping of the top-k "best" docs for ENN needs to be done.
This bookkeeping is [backed](https://github.com/vespa-engine/vespa/blob/0c55dc92a3bf889c67fac1ca855e6e33e1994904/vespalib/src/vespa/vespalib/util/priority_queue.h#L26) by a [priority queue](https://en.wikipedia.org/wiki/Priority_queue).

During the execution of ENN, a priority queue of size `k` is filled with the first `k` docs that are iterated over by the query engine. 
Then, if a doc at `k+i-th` position is "better" than the worst doc in the priority queue, that worst doc is removed, the current doc is added, and the queue is reordered.

Consequently, the docs exposed to first-phase ranking are exclusively those that were, at some point during iteration, held within this priority queue.

## Math

Think about finding a max value in a sequence of random numbers.
When a sequence has the length N, counting the number of times, a new "temporary maximum" is found can be represented with the N-th [harmonic number](https://en.wikipedia.org/wiki/Harmonic_number): 
$$
H_N = E[K] = \sum_{i=1}^N \frac{1}{i}
$$
The $\frac{1}{i}$ is a probability that the i-th element is the "temporary maximum".
Intuitively, the first element checked is always going to be the current maximum, hence the probability of 1.
The probability that the second element is the new maximum is $\frac{1}{2}$, etc.
For large sequences the number of "temporary maximums" until the global maximum is discovered is approximately equal to $ln(N) + {\displaystyle \gamma }$, where $\gamma$ is the [Euler-Mascheroni constant](https://en.wikipedia.org/wiki/Euler%27s_constant).


Distances between query and document vectors can be roughly thought as a list of random numbers, and we want to find the `top-k=targetHits` closest document vectors with the ENN search.
Of course, your actual indices might not be entirely in a random order.
E.g., docs are iterated from oldest to newest, and the query targets some event that happened in the past, and there are no recent docs about it.
But despite such nuances, the number of docs exposed to the first phase ranking is logarithm from the total number of docs.

When we need to track the `targetHits` best elements, then the expected number of the priority queue "updates" is
$$
E[K] = targetHits + targetHits \sum_{i=targetHits + 1}^N \frac{1}{i} = targethits(1 + ln(N/targetHits))
$$

## Putting it all together

With ENN the number of docs exposed to the first-phase ranking per content node is approximately
$$
targetHits(1 + ln(totalDocCount/targetHits))
$$
So, the higher the `targetHits` the more docs are exposed to the first-phase ranking.

## Sidequest: `targetHits=1`

Coming back to Vespa land, when `targetHits=1` then formula boils down to: `1 + ln(totalDocCount)`.
`1` is because the first element always is added into the priority queue during the search.
The `ln(totalDocCount)` captures the sum of probabilities that the n-th element is the maximum.
When the list has only one element then `ln(1)=0`, and the final sum is 1.
All is good.

## Experiment

In Vespa response the [`totalCount`](https://docs.vespa.ai/en/reference/querying/default-result-format.html#totalcount) is 
> The value is the number of hits after first-phase dropping.

With 10M single float-dimensional vectors `tensor<float>(x[1])` for various `targetHits`, the actual `totalCount` values do closely match the formula:

```plaintext
targetHits=1 totalCount: 14 estimate=17.11809565095832
targetHits=10 totalCount: 131 estimate=148.15510557964274
targetHits=100 totalCount: 1253 estimate=1251.2925464970228
targetHits=1000 totalCount: 10284 estimate=10210.340371976183
targetHits=10000 totalCount: 79194 estimate=79077.55278982136
targetHits=100000 totalCount: 560368 estimate=560517.0185988091
targetHits=1000000 totalCount: 1712078 estimate=3302585.092994046
```
The main formula is used to calculate estimates.
Full setup is [here](../notebooks/failed-range-filter-hack.ipynb).

## Q&A

### What about filters?

The formula then becomes 
$$
targetHits(1 + ln(totalDocCount*filterFactor/targetHits))
$$ 
where `filterFactor` is [0,1].
The stricter the filter, the fewer docs are exposed to the first-phase ranking.

### What about the `distanceThreshold` parameter?

The `distanceThreshold` [parameter](https://docs.vespa.ai/en/reference/querying/yql.html#distancethreshold) acts like a filter.
E.g. if `distanceThreshold=0.5` matches only about 50% of the docs, then the formula becomes 
$$
targetHits(1 + ln(totalDocCount*distanceThresholdFilterFactor/targetHits))
$$
So, the lower the distance threshold the fewer docs are exposed to the first-phase ranking.
If additional filters are added, then multiply the `totalDocCount` by `filterFactor`.
Of course, filters can be negatively correlated with the query, then fewer docs would pass the distance threshold filter.

### What about the match-phase limiter?

The `totalDocCount` becomes "how many docs the match phase limiter iterated over".
E.g., if match-phase max-docs is `10000` and `targetHits` is 100, then (ignoring the match-phase estimation part) the number becomes 
$$
100(1 + ln(10000/100))=560,5170185988
$$
The number is not impacted by filters, because match-phase continues to search until the `max-docs` are found.
Here the ENN with distance threshold acts as an "additional filter" for finding `max-docs` matches.

### Why I don't see the `totalCount` from your formula with the `unranked` rank profile?

Yes, the `totalCount` is equal to the number of docs in the content node by default.
It is because the `unranked` rank profile [also sets](https://github.com/vespa-engine/vespa/blob/master/config-model/src/main/java/com/yahoo/schema/UnrankedRankProfile.java) `keep-rank-count: 0` and `rerank-count: 0`.
[Set the](https://docs.vespa.ai/en/reference/api/query.html#ranking.keeprankcount) `ranking.keepRankCount=10000` HTTP parameter and the `totalCount` will follow the formula.

## Conclusion

ENN can be all you need when binarized embeddings are used.
The cost of ranking phases is linear to the matched docs, the formula can help you with estimating the number of matched docs.
I hope this helps, and maybe the formula is going to be added to the official Vespa docs.
