# ACORN

One nice aspect of using [ACORN](https://blog.vespa.ai/additions-to-hnsw/) (a.k.a. _filer-first_) is that it matches up to the `targetHits` documents per content node. 

If you set params in the request like this:
```json
{
  'ranking.matching.approximateThreshold': 0,
  'ranking.matching.filterFirstThreshold': 0.3,
}
```

Vespa is not going to execute the exact nearest neighbor search. 
It will instead execute the approximate nearest neighbor search (ANN) using ACORN when the estimated portion of matched docs is less than 0.3 and when the estimate is larger the regular ANN over HNSW is executed.

Keep in mind, that with ACORN, the recall[^recall] is lower than with the exact nearest neighbor search, i.e., more 0-results searches are expected.


[^recall]: overlap between the ENN and ANN query results.
