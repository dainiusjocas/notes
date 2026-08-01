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

```shell
vespa feed ext/doc.json
```

```shell
vespa query 'select * from sources * where chunks.text contains "intro"'
```
