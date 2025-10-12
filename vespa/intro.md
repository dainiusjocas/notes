# Vespa for Data Scientists Intro

What is the point?

> If you can make it work on your laptop, scaling is on me.

+++

## Querying overview

```{mermaid}
flowchart LR
  A[User Query with filters] --> C
  B[AB test flags] --> C
  BB[Business rules] --> C
  C(Vespa) --> D{Container node}
  D --> E[Searcher Chain]
  E --> F[Content node]
  F --> G[Matching]
  G --> H[Ranking]
  H --> I[Container Node]
  I --> J[Global Phase]
  D --> k[Render]
```

## How matching in ranking are related?

Think like this:
1. Matching finds all the docs that satisfy query.
2. Ranking takes all the matched docs scores.

### Grey zone between matching and ranking

Query operators that match top-k "best" docs.
What is this "best"? 
"Best" is to some score.
So, scores can appear during the matching? Yes!

The operators are:
- nearestNeighbors
- wand
- weakAnd
- range with some option
- geo(?)

And in the ranking you can use that score from the matching phase.

Keep in mind that only the top-k best matches according the operator are going to have the non-zero score.

`match-phase` is a bit odd one here. But similar to the `range` operator. As it's score is not contributing to the ranking directly: its value is accessible as the `attribute(field)` which is largely the same.

:::{dropdown} Special topics

- Sorting vs. Global Phase
- Models for ranking
- How to debug why model produces unexpected score?
  - Justina lightgbm
  - reranker data types mismatch
- 
:::


## 
