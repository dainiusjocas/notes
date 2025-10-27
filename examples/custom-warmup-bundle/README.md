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

```shell
echo '{"id":"id:lucene:lucene::174422292","fields":{"mytext": "1759637724"}}' | vespa feed -
vespa query 'select * from sources * where true limit 1' 'ranking=unranked' 'timeout=5s' -t local --verbose
curl 'http://127.0.0.1:8080/search2/?ranking=unranked&timeout=5s&yql=select+%2A+from+sources+%2A+where+true+limit+1'
curl 'http://127.0.0.1:8080/search3/?ranking=unranked&timeout=5s&yql=select+%2A+from+sources+%2A+where+true+limit+1'
```
