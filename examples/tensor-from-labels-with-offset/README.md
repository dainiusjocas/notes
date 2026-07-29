# Tensor type mismatch when using `tensorFromLabelsWithOffset` fuunction

Somehow ranking expression can't figure out the typing information when `tensorFromLabelsWithOffset` is used.

Run Vespa 
```shell
container run -c=2 -m=4G \
  --detach \
  --rm \
  --name vespa-test \
  -e VESPA_TIMER_HZ=100 \
  --publish 0.0.0.0:8080:8080 \
  --publish 0.0.0.0:19050:19050 \
  --publish 0.0.0.0:19071:19071 \
  --publish 0.0.0.0:19092:19092 \
  vespaengine/vespa:8.728.1
```

Feed one document:
```shell
echo '{"id":"id:multichunk:multichunk::1","fields":{"identifiers": [2, 3]}}' | vespa feed - -t local
```
```shell
vespa query 'select * from sources * where true' 'ranking.profile=demo' -t local
```
results in:
```json
{
    "root": {
        "children": [
            {
                "fields": {
                    "documentid": "id:multichunk:multichunk::1",
                    "identifiers": [
                        2,
                        3
                    ],
                    "matchfeatures": {
                        "offset_to_value": {
                            "cells": [
                                {
                                    "address": {
                                        "offset": "0",
                                        "value": "2"
                                    },
                                    "value": 1
                                },
                                {
                                    "address": {
                                        "offset": "1",
                                        "value": "3"
                                    },
                                    "value": 1
                                }
                            ],
                            "type": "tensor<float>(offset{},value{})"
                        }
                    },
                    "sddocname": "multichunk"
                },
                "id": "id:multichunk:multichunk::1",
                "relevance": 0,
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
note the type of tensor is `tensor<float>(offset{},value{})`.

```shell
vespa query 'select * from sources * where true' 'ranking.profile=demo.allgood' -t local
```
returns
```json
{
    "root": {
        "children": [
            {
                "fields": {
                    "documentid": "id:multichunk:multichunk::1",
                    "identifiers": [
                        2,
                        3
                    ],
                    "matchfeatures": {
                        "merged": {
                            "cells": {
                                "0": 2,
                                "1": 2
                            },
                            "type": "tensor<float>(offset{})"
                        },
                        "offset_to_value": {
                            "cells": [
                                {
                                    "address": {
                                        "offset": "0",
                                        "value": "2"
                                    },
                                    "value": 1
                                },
                                {
                                    "address": {
                                        "offset": "1",
                                        "value": "3"
                                    },
                                    "value": 1
                                }
                            ],
                            "type": "tensor<float>(offset{},value{})"
                        },
                        "some_offsets": {
                            "cells": {
                                "0": 1,
                                "1": 1
                            },
                            "type": "tensor<float>(offset{})"
                        }
                    },
                    "sddocname": "multichunk"
                },
                "id": "id:multichunk:multichunk::1",
                "relevance": 0,
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

when 


```shell
Uploading application package... failed
Error: invalid application package (status 400)
Invalid application:
In schema 'multichunk', rank profile 'demo.problem':
The function 'merged' is invalid:
Types in merge() dimensions mismatch:
tensor<float>(offset{}) != tensor()
```
