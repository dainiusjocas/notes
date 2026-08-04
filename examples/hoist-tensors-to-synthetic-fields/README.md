# Hack how to hoist tensors from array of structs

https://docs.vespa.ai/en/reference/rag/embedding.html#custom-embedders

```shell
container run --cpus 4 --memory 4g --rm --detach \
  --name vespa \
  --publish 127.0.0.1:8080:8080 \
  --publish 127.0.0.1:19071:19071 \
  --publish 127.0.0.1:19050:19050 \
  vespaengine/vespa:8.731.17
```

```shell
mvn clean package && vespa deploy
```

```shell
echo '
{
  "id": "id:doc:doc::1",
  "fields": {
    "chunks": [
      {"embedding": [3]},
      {"embedding": [2]},
      {"embedding": [1]}
    ]
  }
}' \
| jq -c | vespa feed -
```

```shell
vespa visit --field-set="doc:embeddings" | jq
```
Returns:
```json
{
  "id": "id:doc:doc::1",
  "fields": {
    "embeddings": {
      "type": "tensor<float>(offset{},x[1])",
      "blocks": {
        "0": [
          1.0
        ],
        "1": [
          2.0
        ],
        "2": [
          3.0
        ]
      }
    }
  }
}
```

Let's try running NN query:
```shell
vespa query \
  'select * from sources doc where {targetHits: 1}nearestNeighbor(embeddings, query)' \
  'input.query(query)=[1.0]' \
  'ranking.profile=default'
```

Which returns:
```json
{
    "root": {
        "children": [
            {
                "fields": {
                    "chunks": [
                        {
                            "embedding": "BgEBAXgBP4AAAA=="
                        },
                        {
                            "embedding": "BgEBAXgBQAAAAA=="
                        },
                        {
                            "embedding": "BgEBAXgBQEAAAA=="
                        }
                    ],
                    "documentid": "id:doc:doc::1",
                    "sddocname": "doc"
                },
                "id": "id:doc:doc::1",
                "relevance": 0.0017429193899782135,
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
Voila!

Why `embedding` field values are base64 encoded?
