# custom-warmup-bundle

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

Now when added to the services.xml:

```xml
  <container id="container" version="1.0">
    <config name="search.config">
      <clusterName>content</clusterName>
    </config>
```

We get:
```plain
[2025-10-27 18:42:27.078] ERROR   container        Container.lt.jocas.examples.CustomSearchHandler	Failed executing query=[TRUE] offset=0 hits=1 sources=[] restrict= [] rank profile=demo [/search/?ranking=demo&timeout=5s&yql=select+%2A+from+sources+%2A+where+true+limit+1]
exception=
java.lang.IllegalStateException: No suitable groups to dispatch query. Rejected: []
	at com.yahoo.search.dispatch.Dispatcher.getInternalInvoker(Dispatcher.java:353)
	at com.yahoo.search.dispatch.Dispatcher.lambda$getSearchInvoker$5(Dispatcher.java:284)
	at java.base/java.util.Optional.orElseGet(Optional.java:364)
	at com.yahoo.search.dispatch.Dispatcher.getSearchInvoker(Dispatcher.java:284)
	at com.yahoo.prelude.fastsearch.IndexedBackend.getSearchInvoker(IndexedBackend.java:132)
	at com.yahoo.prelude.fastsearch.IndexedBackend.doSearch2(IndexedBackend.java:70)
	at com.yahoo.prelude.fastsearch.VespaBackend.search(VespaBackend.java:175)
	at com.yahoo.prelude.cluster.ClusterSearcher.perSchemaSearch(ClusterSearcher.java:241)
	at com.yahoo.prelude.cluster.ClusterSearcher.doSearch(ClusterSearcher.java:221)
	at com.yahoo.prelude.cluster.ClusterSearcher.search(ClusterSearcher.java:160)
	at com.yahoo.search.Searcher.process(Searcher.java:135)
	at com.yahoo.processing.execution.Execution.process(Execution.java:112)
	at com.yahoo.search.searchchain.Execution.search(Execution.java:500)
	at com.yahoo.search.searchers.ContainerLatencySearcher.search(ContainerLatencySearcher.java:34)
	at com.yahoo.search.Searcher.process(Searcher.java:135)
	at com.yahoo.processing.execution.Execution.process(Execution.java:112)
	at com.yahoo.search.searchchain.Execution.search(Execution.java:500)
	at com.yahoo.prelude.searcher.ValidatePredicateSearcher.search(ValidatePredicateSearcher.java:37)
	at com.yahoo.search.Searcher.process(Searcher.java:135)
	at com.yahoo.processing.execution.Execution.process(Execution.java:112)
	at com.yahoo.search.searchchain.Execution.search(Execution.java:500)
	at com.yahoo.search.grouping.vespa.GroupingExecutor.search(GroupingExecutor.java:81)
	at com.yahoo.search.Searcher.process(Searcher.java:135)
	at com.yahoo.processing.execution.Execution.process(Execution.java:112)
	at com.yahoo.search.searchchain.Execution.search(Execution.java:500)
	at com.yahoo.search.querytransform.BooleanSearcher.search(BooleanSearcher.java:46)
	at com.yahoo.search.Searcher.process(Searcher.java:135)
	at com.yahoo.processing.execution.Execution.process(Execution.java:112)
	at com.yahoo.search.searchchain.Execution.search(Execution.java:500)
	at com.yahoo.prelude.searcher.ValidateSortingSearcher.search(ValidateSortingSearcher.java:50)
	at com.yahoo.search.Searcher.process(Searcher.java:135)
	at com.yahoo.processing.execution.Execution.process(Execution.java:112)
	at com.yahoo.search.searchchain.Execution.search(Execution.java:500)
	at com.yahoo.search.querytransform.LowercasingSearcher.search(LowercasingSearcher.java:41)
	at com.yahoo.search.Searcher.process(Searcher.java:135)
	at com.yahoo.processing.execution.Execution.process(Execution.java:112)
	at com.yahoo.search.searchchain.Execution.search(Execution.java:500)
	at com.yahoo.prelude.querytransform.NormalizingSearcher.search(NormalizingSearcher.java:52)
	at com.yahoo.search.Searcher.process(Searcher.java:135)
	at com.yahoo.processing.execution.Execution.process(Execution.java:112)
	at com.yahoo.search.searchchain.Execution.search(Execution.java:500)
	at com.yahoo.prelude.querytransform.StemmingSearcher.search(StemmingSearcher.java:109)
	at com.yahoo.search.Searcher.process(Searcher.java:135)
	at com.yahoo.processing.execution.Execution.process(Execution.java:112)
	at com.yahoo.search.searchchain.Execution.search(Execution.java:500)
	at com.yahoo.search.significance.SignificanceSearcher.search(SignificanceSearcher.java:95)
	at com.yahoo.search.Searcher.process(Searcher.java:135)
	at com.yahoo.processing.execution.Execution.process(Execution.java:112)
	at com.yahoo.search.searchchain.Execution.search(Execution.java:500)
	at com.yahoo.search.searchers.InputCheckingSearcher.search(InputCheckingSearcher.java:63)
	at com.yahoo.search.Searcher.process(Searcher.java:135)
	at com.yahoo.processing.execution.Execution.process(Execution.java:112)
	at com.yahoo.search.searchchain.Execution.search(Execution.java:500)
	at com.yahoo.search.yql.FieldFiller.search(FieldFiller.java:37)
	at com.yahoo.search.Searcher.process(Searcher.java:135)
	at com.yahoo.processing.execution.Execution.process(Execution.java:112)
	at com.yahoo.search.searchchain.Execution.search(Execution.java:500)
	at com.yahoo.search.searchers.ValidateSameElementSearcher.search(ValidateSameElementSearcher.java:32)
	at com.yahoo.search.Searcher.process(Searcher.java:135)
	at com.yahoo.processing.execution.Execution.process(Execution.java:112)
	at com.yahoo.search.searchchain.Execution.search(Execution.java:500)
	at com.yahoo.search.searchers.ValidateFuzzySearcher.search(ValidateFuzzySearcher.java:38)
	at com.yahoo.search.Searcher.process(Searcher.java:135)
	at com.yahoo.processing.execution.Execution.process(Execution.java:112)
	at com.yahoo.search.searchchain.Execution.search(Execution.java:500)
	at com.yahoo.search.searchers.ValidateMatchPhaseSearcher.search(ValidateMatchPhaseSearcher.java:54)
	at com.yahoo.search.Searcher.process(Searcher.java:135)
	at com.yahoo.processing.execution.Execution.process(Execution.java:112)
	at com.yahoo.search.searchchain.Execution.search(Execution.java:500)
	at com.yahoo.search.searchers.ValidateNearestNeighborSearcher.search(ValidateNearestNeighborSearcher.java:51)
	at com.yahoo.search.Searcher.process(Searcher.java:135)
	at com.yahoo.processing.execution.Execution.process(Execution.java:112)
	at com.yahoo.search.searchchain.Execution.search(Execution.java:500)
	at com.yahoo.prelude.querytransform.RecallSearcher.search(RecallSearcher.java:47)
	at com.yahoo.search.Searcher.process(Searcher.java:135)
	at com.yahoo.processing.execution.Execution.process(Execution.java:112)
	at com.yahoo.search.searchchain.Execution.search(Execution.java:500)
	at com.yahoo.search.querytransform.WandSearcher.search(WandSearcher.java:159)
	at com.yahoo.search.Searcher.process(Searcher.java:135)
	at com.yahoo.processing.execution.Execution.process(Execution.java:112)
	at com.yahoo.search.searchchain.Execution.search(Execution.java:500)
	at com.yahoo.search.grouping.GroupingValidator.search(GroupingValidator.java:65)
	at com.yahoo.search.Searcher.process(Searcher.java:135)
	at com.yahoo.processing.execution.Execution.process(Execution.java:112)
	at com.yahoo.search.searchchain.Execution.search(Execution.java:500)
	at com.yahoo.search.searchers.QueryValidator.search(QueryValidator.java:41)
	at com.yahoo.search.Searcher.process(Searcher.java:135)
	at com.yahoo.processing.execution.Execution.process(Execution.java:112)
	at com.yahoo.search.searchchain.Execution.search(Execution.java:500)
	at com.yahoo.search.querytransform.SortingDegrader.search(SortingDegrader.java:52)
	at com.yahoo.search.Searcher.process(Searcher.java:135)
	at com.yahoo.processing.execution.Execution.process(Execution.java:112)
	at com.yahoo.search.searchchain.Execution.search(Execution.java:500)
	at com.yahoo.search.querytransform.RangeQueryOptimizer.search(RangeQueryOptimizer.java:43)
	at com.yahoo.search.Searcher.process(Searcher.java:135)
	at com.yahoo.processing.execution.Execution.process(Execution.java:112)
	at com.yahoo.search.searchchain.Execution.search(Execution.java:500)
	at com.yahoo.prelude.querytransform.LiteralBoostSearcher.search(LiteralBoostSearcher.java:38)
	at com.yahoo.search.Searcher.process(Searcher.java:135)
	at com.yahoo.processing.execution.Execution.process(Execution.java:112)
	at com.yahoo.search.searchchain.Execution.search(Execution.java:500)
	at com.yahoo.prelude.querytransform.CJKSearcher.search(CJKSearcher.java:43)
	at com.yahoo.search.Searcher.process(Searcher.java:135)
	at com.yahoo.processing.execution.Execution.process(Execution.java:112)
	at com.yahoo.search.searchchain.Execution.search(Execution.java:500)
	at com.yahoo.search.querytransform.DefaultPositionSearcher.search(DefaultPositionSearcher.java:63)
	at com.yahoo.search.Searcher.process(Searcher.java:135)
	at com.yahoo.processing.execution.Execution.process(Execution.java:112)
	at com.yahoo.search.searchchain.Execution.search(Execution.java:500)
	at com.yahoo.search.querytransform.NGramSearcher.search(NGramSearcher.java:66)
	at com.yahoo.search.Searcher.process(Searcher.java:135)
	at com.yahoo.processing.execution.Execution.process(Execution.java:112)
	at com.yahoo.search.searchchain.Execution.search(Execution.java:500)
	at com.yahoo.search.federation.FederationSearcher.search(FederationSearcher.java:287)
	at com.yahoo.search.federation.FederationSearcher.search(FederationSearcher.java:267)
	at com.yahoo.search.federation.FederationSearcher.search(FederationSearcher.java:262)
	at com.yahoo.search.Searcher.process(Searcher.java:135)
	at com.yahoo.processing.execution.Execution.process(Execution.java:112)
	at com.yahoo.search.searchchain.Execution.search(Execution.java:500)
	at com.yahoo.search.searchers.OpportunisticWeakAndSearcher.search(OpportunisticWeakAndSearcher.java:40)
	at com.yahoo.search.Searcher.process(Searcher.java:135)
	at com.yahoo.processing.execution.Execution.process(Execution.java:112)
	at com.yahoo.search.searchchain.Execution.search(Execution.java:500)
	at com.yahoo.search.querytransform.WeakAndReplacementSearcher.search(WeakAndReplacementSearcher.java:32)
	at com.yahoo.search.Searcher.process(Searcher.java:135)
	at com.yahoo.processing.execution.Execution.process(Execution.java:112)
	at com.yahoo.search.searchchain.Execution.search(Execution.java:500)
	at com.yahoo.prelude.searcher.BlendingSearcher.search(BlendingSearcher.java:57)
	at com.yahoo.search.Searcher.process(Searcher.java:135)
	at com.yahoo.processing.execution.Execution.process(Execution.java:112)
	at com.yahoo.search.searchchain.Execution.search(Execution.java:500)
	at com.yahoo.search.grouping.GroupingQueryParser.search(GroupingQueryParser.java:52)
	at com.yahoo.search.Searcher.process(Searcher.java:135)
	at com.yahoo.processing.execution.Execution.process(Execution.java:112)
	at com.yahoo.search.searchchain.Execution.search(Execution.java:500)
	at com.yahoo.prelude.semantics.SemanticSearcher.search(SemanticSearcher.java:90)
	at com.yahoo.search.Searcher.process(Searcher.java:135)
	at com.yahoo.processing.execution.Execution.process(Execution.java:112)
	at com.yahoo.search.searchchain.Execution.search(Execution.java:500)
	at com.yahoo.prelude.searcher.PosSearcher.search(PosSearcher.java:62)
	at com.yahoo.search.Searcher.process(Searcher.java:135)
	at com.yahoo.processing.execution.Execution.process(Execution.java:112)
	at com.yahoo.search.searchchain.Execution.search(Execution.java:500)
	at com.yahoo.prelude.searcher.JuniperSearcher.search(JuniperSearcher.java:74)
	at com.yahoo.search.Searcher.process(Searcher.java:135)
	at com.yahoo.processing.execution.Execution.process(Execution.java:112)
	at com.yahoo.search.searchchain.Execution.search(Execution.java:500)
	at com.yahoo.search.yql.FieldFilter.search(FieldFilter.java:35)
	at com.yahoo.search.Searcher.process(Searcher.java:135)
	at com.yahoo.processing.execution.Execution.process(Execution.java:112)
	at com.yahoo.search.searchchain.Execution.search(Execution.java:500)
	at com.yahoo.search.yql.MinimalQueryInserter.search(MinimalQueryInserter.java:86)
	at com.yahoo.search.Searcher.process(Searcher.java:135)
	at com.yahoo.processing.execution.Execution.process(Execution.java:112)
	at com.yahoo.search.searchchain.Execution.search(Execution.java:500)
	at com.yahoo.prelude.searcher.FieldCollapsingSearcher.search(FieldCollapsingSearcher.java:95)
	at com.yahoo.search.Searcher.process(Searcher.java:135)
	at com.yahoo.processing.execution.Execution.process(Execution.java:112)
	at com.yahoo.search.searchchain.Execution.search(Execution.java:500)
	at com.yahoo.prelude.querytransform.PhrasingSearcher.search(PhrasingSearcher.java:60)
	at com.yahoo.search.Searcher.process(Searcher.java:135)
	at com.yahoo.processing.execution.Execution.process(Execution.java:112)
	at com.yahoo.search.searchchain.Execution.search(Execution.java:500)
	at com.yahoo.prelude.statistics.StatisticsSearcher.search(StatisticsSearcher.java:235)
	at com.yahoo.search.Searcher.process(Searcher.java:135)
	at com.yahoo.processing.execution.Execution.process(Execution.java:112)
	at com.yahoo.search.searchchain.Execution.search(Execution.java:500)
	at lt.jocas.examples.CustomSearchHandler.searchAndFill(CustomSearchHandler.java:262)
	at lt.jocas.examples.CustomSearchHandler.search(CustomSearchHandler.java:208)
	at lt.jocas.examples.CustomSearchHandler.handleBody(CustomSearchHandler.java:181)
	at lt.jocas.examples.CustomSearchHandler.handle(CustomSearchHandler.java:120)
	at com.yahoo.container.jdisc.ThreadedHttpRequestHandler.handle(ThreadedHttpRequestHandler.java:82)
	at com.yahoo.container.jdisc.ThreadedHttpRequestHandler.handleRequest(ThreadedHttpRequestHandler.java:92)
	at com.yahoo.container.jdisc.ThreadedRequestHandler$RequestTask.processRequest(ThreadedRequestHandler.java:190)
	at com.yahoo.container.jdisc.ThreadedRequestHandler$RequestTask.run(ThreadedRequestHandler.java:184)
	at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1136)
	at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:635)
	at java.base/java.lang.Thread.run(Thread.java:840)

```
Yes, config is not there. So, I need to find a way to get it from the rest of the services.xml.

UPDATE(2025-10-29):

It is enough to override bindings so that the `SearchHandler` would not be needed and we can keep `<search/>` in the `services.xml`.

```xml
<search/>
<handler bundle="custom-warmup-bundle" id="lt.jocas.examples.CustomSearchHandler">
  <binding>http://*/search</binding>
  <binding>http://*/search/*</binding>
</handler>
```

Yeah, both bindings are needed.

The fact that `SearchHandler` is not created can be observed by checking metrics for request count of the `unranked` rank profile:

```shell
watch -n 1 'curl -s 0:8080/metrics/v2/values\?consumer=vespa | jq ".nodes[0] | .services[]" |  grep "unranked" -A 10 -B 10'
```

```text
        "content.proton.documentdb.matching.rank_profile.rerank_time.count": 0,
        "content.proton.documentdb.matching.rank_profile.rerank_time.max": 0,
        "content.proton.documentdb.matching.rank_profile.query_setup_time.sum": 0,
        "content.proton.documentdb.matching.rank_profile.query_setup_time.count": 0,
        "content.proton.documentdb.matching.rank_profile.query_setup_time.max": 0,
        "content.proton.documentdb.matching.rank_profile.query_latency.sum": 0,
        "content.proton.documentdb.matching.rank_profile.query_latency.count": 0,
        "content.proton.documentdb.matching.rank_profile.query_latency.max": 0
      },
      "dimensions": {
        "rankProfile": "unranked",
        "documenttype": "lucene",
        "serviceId": "searchnode"
      }
    },
    {
      "values": {
        "content.proton.documentdb.ready.index.disk_usage.average": 0
      },
      "dimensions": {
        "documenttype": "lucene",
```
