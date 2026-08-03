# Layered ranking issue

When a ranking profile that is used to get the best chunks in a layered ranking setup doesn't have specified summary features, then no best chunks are returned.

```shell
container run --cpus 4 --memory 4g --rm --detach \
  --name vespa \
  --publish 127.0.0.1:8080:8080 \
  --publish 127.0.0.1:19071:19071 \
  --publish 127.0.0.1:19050:19050 \
  vespaengine/vespa:8.731.17
```

```shell
vespa deploy
```

When deploying an application that doesn't contain a ranking profile that has summary features defined, then app fails to deploy:

```text
Uploading application package... failed
Error: invalid application package (status 400)
Invalid application:
For schema 'doc', document-summary 'default', summary field 'chunks':
For schema 'doc', document-summary 'default', summary field 'chunks':
select-elements-by summary feature 'best_chunks' is not defined for source field 'chunks'.
```

Feed some data:
```shell
echo '{
  "id": "id:doc:doc::1", 
  "fields": {
    "chunks": [
      "test", 
      "test test", 
      "test test test", 
      "test test test test"
    ],
    "meta": [
      {"idx": 1},
      {"idx": 2},
      {"idx": 3},
      {"idx": 4}
    ]
  }
}' | jq -c | vespa feed -
```

Query with the `default` query profile:

```shell
vespa query 'yql=select * from sources * where chunks contains text("test")' 'ranking.profile=default'
```
The response doesn't contain any chunks
```json
{
    "root": {
        "children": [
            {
                "fields": {
                    "documentid": "id:doc:doc::1",
                    "sddocname": "doc"
                },
                "id": "id:doc:doc::1",
                "relevance": 1.6749440431594849,
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

But when queried with a ranking profile that contains `summary-features` then summary is returned correctly:
```shell
vespa query 'yql=select * from sources * where chunks contains text("test")' 'ranking.profile=default.demo'
```

```json
{
    "root": {
        "children": [
            {
                "fields": {
                    "chunks": [
                        "test test test",
                        "test test test test"
                    ],
                    "documentid": "id:doc:doc::1",
                    "sddocname": "doc",
                    "summaryfeatures": {
                        "best_chunks": {
                            "cells": {
                                "2": 0.4334935247898102,
                                "3": 0.4410456717014313
                            },
                            "type": "tensor<float>(chunk{})"
                        },
                        "vespa.summaryFeatures.cached": 0
                    }
                },
                "id": "id:doc:doc::1",
                "relevance": 1.6749440431594849,
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
