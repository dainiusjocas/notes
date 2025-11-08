# ACORN

One nice aspect of using [ACORN](https://blog.vespa.ai/additions-to-hnsw/) (a.k.a. _filer-first_) is that it matches up to the `targetHits` documents per content node. 

If you set params in the request like this:
```python
{
  'ranking.matching.approximateThreshold': 0,
  'ranking.matching.filterFirstThreshold': 0.3,
}
```

Vespa is not going to execute exact nearest neighbor search. It will instead execute approximate nearest neighbor search using ACORN when estimated portion of matched docs is less than 0.3 and when estimate is larger the the regular approximate nearest neighbor search.

Keep in mind, that with ACORN, the recall is lower than with exact nearest neighbor search, i.e., more 0-results searches are expected.
