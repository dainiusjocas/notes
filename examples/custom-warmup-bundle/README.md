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


After commenting out the `<search/>` from `services.xml`:
```text
[2025-10-27 16:19:18.933] WARNING container        Container.com.yahoo.container.di.Container	Failed to set up new component graph. Retaining previous component generation.
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
	at jdk.internal.reflect.GeneratedConstructorAccessor146.newInstance(Unknown Source)
	at java.base/jdk.internal.reflect.DelegatingConstructorAccessorImpl.newInstance(DelegatingConstructorAccessorImpl.java:45)
	at java.base/java.lang.reflect.Constructor.newInstanceWithCaller(Constructor.java:500)
	at java.base/java.lang.reflect.Constructor.newInstance(Constructor.java:481)
	at com.yahoo.config.subscription.ConfigInstanceUtil.getNewInstance(ConfigInstanceUtil.java:46)
	... 16 more
Caused by: java.lang.IllegalArgumentException: The following builder parameters for cluster must be initialized: [clusterName]
	at com.yahoo.search.config.ClusterConfig.<init>(ClusterConfig.java:197)
	at com.yahoo.search.config.ClusterConfig.<init>(ClusterConfig.java:192)
	... 21 more
```

It seems that some configuration is missing. How to add it>
