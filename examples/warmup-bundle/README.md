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

Now In vespa logs I get:
```plain
[2025-10-28 09:10:44.119] WARNING container        Container.com.yahoo.container.di.Container	Failed to set up new component graph. Retaining previous component generation.
exception=
java.lang.IllegalArgumentException: Failed retrieving the next config generation
	at com.yahoo.container.di.CloudSubscriber.waitNextGeneration(CloudSubscriber.java:88)
	at com.yahoo.container.di.ConfigRetriever.getComponentsSnapshot(ConfigRetriever.java:102)
	at com.yahoo.container.di.ConfigRetriever.getConfigsOnce(ConfigRetriever.java:73)
	at com.yahoo.container.di.ConfigRetriever.getConfigs(ConfigRetriever.java:53)
	at com.yahoo.container.di.Container.waitForNewConfigGenAndCreateGraph(Container.java:117)
	at com.yahoo.container.di.Container.waitForNextGraphGeneration(Container.java:75)
	at com.yahoo.container.core.config.HandlersConfigurerDi.waitForNextGraphGeneration(HandlersConfigurerDi.java:155)
	at com.yahoo.container.jdisc.ConfiguredApplication.doReconfigurationLoop(ConfiguredApplication.java:367)
	at java.base/java.lang.Thread.run(Thread.java:840)
Caused by: java.lang.IllegalArgumentException: Bad config in response
	at com.yahoo.config.subscription.impl.JRTConfigSubscription.setNewConfig(JRTConfigSubscription.java:144)
	at com.yahoo.config.subscription.impl.JRTConfigSubscription.nextConfig(JRTConfigSubscription.java:94)
	at com.yahoo.config.subscription.ConfigSubscriber.acquireSnapshot(ConfigSubscriber.java:278)
	at com.yahoo.config.subscription.ConfigSubscriber.nextGeneration(ConfigSubscriber.java:242)
	at com.yahoo.config.subscription.ConfigSubscriber.nextGeneration(ConfigSubscriber.java:215)
	at com.yahoo.container.di.CloudSubscriber.waitNextGeneration(CloudSubscriber.java:83)
	... 8 more
Caused by: java.lang.IllegalArgumentException: Failed creating new instance of 'com.yahoo.search.config.ClusterConfig' for config id 'container/searchchains/chain/content/component/com.yahoo.prelude.searcher.ValidateSortingSearcher'
	at com.yahoo.config.subscription.ConfigInstanceUtil.getNewInstance(ConfigInstanceUtil.java:51)
	at com.yahoo.vespa.config.ConfigPayload.toInstance(ConfigPayload.java:101)
	at com.yahoo.config.subscription.impl.JRTConfigSubscription.toConfigInstance(JRTConfigSubscription.java:171)
	at com.yahoo.config.subscription.impl.JRTConfigSubscription.setNewConfig(JRTConfigSubscription.java:141)
	... 13 more
Caused by: java.lang.reflect.InvocationTargetException
	at java.base/jdk.internal.reflect.NativeConstructorAccessorImpl.newInstance0(Native Method)
	at java.base/jdk.internal.reflect.NativeConstructorAccessorImpl.newInstance(NativeConstructorAccessorImpl.java:77)
	at java.base/jdk.internal.reflect.DelegatingConstructorAccessorImpl.newInstance(DelegatingConstructorAccessorImpl.java:45)
	at java.base/java.lang.reflect.Constructor.newInstanceWithCaller(Constructor.java:500)
	at java.base/java.lang.reflect.Constructor.newInstance(Constructor.java:481)
	at com.yahoo.config.subscription.ConfigInstanceUtil.getNewInstance(ConfigInstanceUtil.java:46)
	... 16 more
Caused by: java.lang.IllegalArgumentException: The following builder parameters for cluster must be initialized: [clusterName]
	at com.yahoo.search.config.ClusterConfig.<init>(ClusterConfig.java:197)
	at com.yahoo.search.config.ClusterConfig.<init>(ClusterConfig.java:192)
	... 22 more
```
