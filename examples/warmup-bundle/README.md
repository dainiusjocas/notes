# warmup-bundle

Run Vespa docker container:

```shell
docker run --rm --detach \
  --name vespa \
  --hostname vespa-container \
  --publish 127.0.0.1:8080:8080 \
  --publish 127.0.0.1:19071:19071 \
  --publish 127.0.0.1:19050:19050 \
  vespaengine/vespa:8.596.20
```

Packaging:
```shell
mvn clean package && vespa deploy -t local
```


In the logs there is:

```plain
[2025-10-22 07:00:41.606] ERROR   container        Container.com.yahoo.container.jdisc.ConfiguredApplication	Reconfiguration failed, your application package must be fixed, unless this is a JNI reload issue: When resolving dependencies of 'lt.jocas.examples.MySearchHandler': No global component of class com.yahoo.search.searchchain.ExecutionFactory to inject into component 'lt.jocas.examples.MySearchHandler'.\nexception=\njava.lang.RuntimeException: When resolving dependencies of 'lt.jocas.examples.MySearchHandler'\nCaused by: java.lang.IllegalStateException: No global component of class com.yahoo.search.searchchain.ExecutionFactory to inject into component 'lt.jocas.examples.MySearchHandler'.\n
```
