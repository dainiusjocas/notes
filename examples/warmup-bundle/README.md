# warmup-bundle

Run Vespa docker container:

```shell
docker run --rm --detach \
  --name vespa \
  --hostname vespa-container \
  --publish 127.0.0.1:8080:8080 \
  --publish 127.0.0.1:19071:19071 \
  --publish 127.0.0.1:19050:19050 \
  vespaengine/vespa:8.600.35
```

Packaging:
```shell
mvn clean package && vespa deploy -t local
```


In the logs there is:

```plain
[2025-10-22 07:00:41.606] ERROR   container        Container.com.yahoo.container.jdisc.ConfiguredApplication	Reconfiguration failed, your application package must be fixed, unless this is a JNI reload issue: When resolving dependencies of 'lt.jocas.examples.MySearchHandler': No global component of class com.yahoo.search.searchchain.ExecutionFactory to inject into component 'lt.jocas.examples.MySearchHandler'.\nexception=\njava.lang.RuntimeException: When resolving dependencies of 'lt.jocas.examples.MySearchHandler'\nCaused by: java.lang.IllegalStateException: No global component of class com.yahoo.search.searchchain.ExecutionFactory to inject into component 'lt.jocas.examples.MySearchHandler'.\n
```

UPDATE: the solution is to add the following to pom.xml:
```xml
<dependency>
  <groupId>com.yahoo.vespa</groupId>
  <artifactId>container</artifactId>
  <version>${vespa.version}</version>
  <scope>provided</scope>
</dependency>
```
And VAP is properly deployed.


```shell
echo '{"id":"id:lucene:lucene::174422292","fields":{"mytext": "1759637724"}}' | vespa feed -
vespa query 'select * from sources * where true limit 1' 'ranking=unranked' 'timeout=5s' -t local --verbose
```
