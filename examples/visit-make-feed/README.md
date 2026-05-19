# Vespa visit --make-feed bug

When application contains multiple content clusters then vespa `visit --make-feed` will render an invalid JSON, because a comma is missing between docs comming from different content clusters.

## Reproduction 

```shell
docker run \
  --detach \
  --rm \
  --name vespa-demo \
  --publish 0.0.0.0:8080:8080 \
  --publish 0.0.0.0:19050:19050 \
  --publish 0.0.0.0:19071:19071 \
  --publish 0.0.0.0:19092:19092 \
  vespaengine/vespa:8.691.19
```

```shell
vespa version
# Vespa CLI version 8.687.75 compiled with go1.26.3 on darwin/arm64
```

```shell
vespa deploy -t local
```

Feed 2 documents:
```shell
echo '
  {"id": "id:namespace:doc1::1", "fields": {"my_id": "1"}}\n
  {"id": "id:namespace:doc2::2", "fields": {"my_id": "2"}}
' | vespa feed - -t local
```

Try to fetch the docs in feed format:
```shell
vespa visit --make-feed -t local | jq
```

Returns an error:
```text
jq: parse error: Expected separator between values at line 3, column 1
```

Because the output is:
```json
[
{"id":"id:namespace:doc1::1","fields":{"my_id":"1"}}
{"id":"id:namespace:doc2::2","fields":{"my_id":"2"}}
]
```

Cleanup:

```shell
docker stop vespa-demo
```
