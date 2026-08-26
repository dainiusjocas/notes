# Layered ranking with sameElement filter

The goal is to explore how `sameElement` helps for the layered ranking approach.

## Setup

```shell
container run --cpus 4 --memory 4g --rm --detach \
  --name vespa \
  --publish 127.0.0.1:8080:8080 \
  --publish 127.0.0.1:19071:19071 \
  --publish 127.0.0.1:19050:19050 \
  vespaengine/vespa:8.738.17
```

```shell
vespa deploy -w 60
```

```shell
vespa feed ext/doc.json
```

```shell
vespa query 'select * from sources * where chunks contains "intro"'
```

## Notes

```plain
field text type array<string> {
  indexing: input chunks | for_each{ get_field text } | index
}
document-summary chunk_selection {
  summary text {
    select-elements-by: chunk_selector
  }
}
rank-profile default {
  function chunk_selector() {
    expression: elementwise(bm25(text), dimension, float)
  }
  summary-features {
    chunk_selector
  }
}
```

The document has two chunks:
```json
[
  "intro one two three",
  "outro one two three"
]
```

## Base single-term handling

```shell
vespa query \
  'select * from sources * where text contains sameElement("intro")' \
  'presentation.summary=chunk_selection'
```
```json
{
    "root": {
        "children": [
            {
                "fields": {
                    "sddocname": "doc",
                    "summaryfeatures": {
                        "chunk_selector": {
                            "cells": {
                                "0": 0.2628660500049591
                            },
                            "type": "tensor<float>(dimension{})"
                        },
                        "vespa.summaryFeatures.cached": 0
                    },
                    "text": [
                        "intro one two three"
                    ]
                },
                "id": "index:content/0/c4ca42382d3a459f312cd1f1",
                "relevance": 0.38186238359951247,
                "source": "content"
            }
        ],
        "coverage": {
            "coverage": 100,
            "documents": 1,
            "full": true,
            "nodes": 1,
            "results": 1,
            "resultsFull": 1
        },
        "fields": {
            "totalCount": 1
        },
        "id": "toplevel",
        "relevance": 1
    }
}
```
Note the `chunk_selector` score.

## Negative terms

Query for `sameElement` for negative term:
```shell
vespa query 
```shell
vespa query \
  'select * from sources * where text contains sameElement(!"intro" and "one")' \
  'presentation.summary=chunk_selection'
```
```json
{
    "root": {
        "children": [
            {
                "fields": {
                    "sddocname": "doc",
                    "summaryfeatures": {
                        "chunk_selector": {
                            "cells": {
                                "1": 0.2628660500049591
                            },
                            "type": "tensor<float>(dimension{})"
                        },
                        "vespa.summaryFeatures.cached": 0
                    },
                    "text": [
                        "outro one two three"
                    ]
                },
                "id": "index:content/0/c4ca42382d3a459f312cd1f1",
                "relevance": 0.16343879032006287,
                "source": "content"
            }
        ],
        "coverage": {
            "coverage": 100,
            "documents": 1,
            "full": true,
            "nodes": 1,
            "results": 1,
            "resultsFull": 1
        },
        "fields": {
            "totalCount": 1
        },
        "id": "toplevel",
        "relevance": 1
    }
}
```
Note that only the `outro` document is returned.

## Optional keywords

```shell
vespa query \
  'select * from sources * where text contains sameElement("intro" or "one")' \
  'presentation.summary=chunk_selection'
```
```json
{
    "root": {
        "children": [
            {
                "fields": {
                    "sddocname": "doc",
                    "summaryfeatures": {
                        "chunk_selector": {
                            "cells": {
                                "0": 0.5257321000099182,
                                "1": 0.2628660500049591
                            },
                            "type": "tensor<float>(dimension{})"
                        },
                        "vespa.summaryFeatures.cached": 0
                    },
                    "text": [
                        "intro one two three",
                        "outro one two three"
                    ]
                },
                "id": "index:content/0/c4ca42382d3a459f312cd1f1",
                "relevance": 0.34549181319413913,
                "source": "content"
            }
        ],
        "coverage": {
            "coverage": 100,
            "documents": 1,
            "full": true,
            "nodes": 1,
            "results": 1,
            "resultsFull": 1
        },
        "fields": {
            "totalCount": 1
        },
        "id": "toplevel",
        "relevance": 1
    }
}
```

## Mandatory keyword with a bunch of optional terms for scoring

```shell
vespa query \
  'select * from sources * where text contains sameElement("intro" and ("one" or "two"))' \
  'presentation.summary=chunk_selection'
```

```json
{
    "root": {
        "children": [
            {
                "fields": {
                    "sddocname": "doc",
                    "summaryfeatures": {
                        "chunk_selector": {
                            "cells": {
                                "0": 0.7885981202125549
                            },
                            "type": "tensor<float>(dimension{})"
                        },
                        "vespa.summaryFeatures.cached": 0
                    },
                    "text": [
                        "intro one two three"
                    ]
                },
                "id": "index:content/0/c4ca42382d3a459f312cd1f1",
                "relevance": 0.2932444398915306,
                "source": "content"
            }
        ],
        "coverage": {
            "coverage": 100,
            "documents": 1,
            "full": true,
            "nodes": 1,
            "results": 1,
            "resultsFull": 1
        },
        "fields": {
            "totalCount": 1
        },
        "id": "toplevel",
        "relevance": 1
    }
}
```

## What else?
