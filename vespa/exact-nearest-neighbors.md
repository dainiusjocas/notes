# Exact Nearest Neighbors

## HNSW graph size

The entire HNSW needs to be in memory. 
For binary vectors it adds 5-6x memory overhead for binary vectors.
E.g. 20M vectors of int8<16> vectors weights about 400MB, while with HNSW it weights about 3.2GB.

Another aspect is filtering:
- HNSW the more restrictive filters the worse it performs.
- ENN the stricter the filters the better it performs as fewer distance calculations need to be done.

## Ranking phases

How many matches are exposed to the first phase ranking?
The number is not random, there is a model behind it.

Priority queue (a.k.a. heap) is used to maintain top matches.
Priority queue is empty and first `targetHits` are all added to it, and hence sent to the first phase ranking.
As the iteration goes on, the matches that scores high enough to be added to the priority queue are also sent to the first phase ranking.

Assuming that documents are uniformly randomly distributed in vespa LID (Local ID) space the number of matches exposed to the first phase ranking is: `docsRanked = targetHits(1 + ln(totalDocCount/targetHits))`.

What happens with filters and ENN?
The ENN is the most expensive filter to execute, so it is evaluated last.
Which means, that the amount of docs that the ENN sees is equal to the filter selectivity.
This gives the estimate `docsRanked = targetHits(1 + ln(totalDocCount * filterSelectivity /targetHits))`.
