# custom-warmup-bundle

Run Vespa docker container:

```shell
docker run --rm --detach \
  --name vespa \
  --hostname vespa-container \
  --publish 127.0.0.1:8080:8080 \
  --publish 127.0.0.1:19071:19071 \
  --publish 127.0.0.1:19050:19050 \
  vespaengine/vespa:8.599.6
```

Packaging:
```shell
mvn clean package && vespa deploy -t local
```


