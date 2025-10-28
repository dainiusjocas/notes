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

Some config is missing, trying to fix it by adding:

```xml
<config name="search.config">
  <clusterName>content</clusterName>
</config>
```

But during `vespa deploy` I get:

```text
Uploading application package... done
Success: Deployed 'target/application' with session ID 5
WARNING Unable to find config definition 'search.config.def'. Please ensure that the name is spelled correctly, and that the def file is included in a bundle.
WARNING Jar file 'warmup-bundle-0.0.1-deploy.jar' uses non-public Vespa APIs: [com.yahoo.container.core, com.yahoo.container.handler.threadpool]
```

Logs say it was deployed successfully.

But when I try to query I get:
```json
Error: 500 Server Error from container at 127.0.0.1:8080
{
    "root": {
        "id": "toplevel",
        "relevance": 1.0,
        "fields": {
            "totalCount": 0
        },
        "errors": [
            {
                "code": 5,
                "summary": "Unspecified error",
                "message": "Failed: No suitable groups to dispatch query. Rejected: []",
                "stackTrace": "java.lang.IllegalStateException: No suitable groups to dispatch query. Rejected: []\n\tat com.yahoo.search.dispatch.Dispatcher.getInternalInvoker(Dispatcher.java:353)\n\tat com.yahoo.search.dispatch.Dispatcher.lambda$getSearchInvoker$5(Dispatcher.java:284)\n\tat java.base/java.util.Optional.orElseGet(Optional.java:364)\n\tat com.yahoo.search.dispatch.Dispatcher.getSearchInvoker(Dispatcher.java:284)\n\tat com.yahoo.prelude.fastsearch.IndexedBackend.getSearchInvoker(IndexedBackend.java:132)\n\tat com.yahoo.prelude.fastsearch.IndexedBackend.doSearch2(IndexedBackend.java:70)\n\tat com.yahoo.prelude.fastsearch.VespaBackend.search(VespaBackend.java:175)\n\tat com.yahoo.prelude.cluster.ClusterSearcher.perSchemaSearch(ClusterSearcher.java:243)\n\tat com.yahoo.prelude.cluster.ClusterSearcher.doSearch(ClusterSearcher.java:223)\n\tat com.yahoo.prelude.cluster.ClusterSearcher.search(ClusterSearcher.java:162)\n\tat com.yahoo.search.Searcher.process(Searcher.java:135)\n\tat com.yahoo.processing.execution.Execution.process(Execution.java:112)\n\tat com.yahoo.search.searchchain.Execution.search(Execution.java:500)\n\tat com.yahoo.search.searchers.ContainerLatencySearcher.search(ContainerLatencySearcher.java:34)\n\tat com.yahoo.search.Searcher.process(Searcher.java:135)\n\tat com.yahoo.processing.execution.Execution.process(Execution.java:112)\n\tat com.yahoo.search.searchchain.Execution.search(Execution.java:500)\n\tat com.yahoo.prelude.searcher.ValidatePredicateSearcher.search(ValidatePredicateSearcher.java:37)\n\tat com.yahoo.search.Searcher.process(Searcher.java:135)\n\tat com.yahoo.processing.execution.Execution.process(Execution.java:112)\n\tat com.yahoo.search.searchchain.Execution.search(Execution.java:500)\n\tat com.yahoo.search.grouping.vespa.GroupingExecutor.search(GroupingExecutor.java:81)\n\tat com.yahoo.search.Searcher.process(Searcher.java:135)\n\tat com.yahoo.processing.execution.Execution.process(Execution.java:112)\n\tat com.yahoo.search.searchchain.Execution.search(Execution.java:500)\n\tat com.yahoo.search.querytransform.BooleanSearcher.search(BooleanSearcher.java:46)\n\tat com.yahoo.search.Searcher.process(Searcher.java:135)\n\tat com.yahoo.processing.execution.Execution.process(Execution.java:112)\n\tat com.yahoo.search.searchchain.Execution.search(Execution.java:500)\n\tat com.yahoo.prelude.searcher.ValidateSortingSearcher.search(ValidateSortingSearcher.java:50)\n\tat com.yahoo.search.Searcher.process(Searcher.java:135)\n\tat com.yahoo.processing.execution.Execution.process(Execution.java:112)\n\tat com.yahoo.search.searchchain.Execution.search(Execution.java:500)\n\tat com.yahoo.search.querytransform.LowercasingSearcher.search(LowercasingSearcher.java:41)\n\tat com.yahoo.search.Searcher.process(Searcher.java:135)\n\tat com.yahoo.processing.execution.Execution.process(Execution.java:112)\n\tat com.yahoo.search.searchchain.Execution.search(Execution.java:500)\n\tat com.yahoo.prelude.querytransform.NormalizingSearcher.search(NormalizingSearcher.java:52)\n\tat com.yahoo.search.Searcher.process(Searcher.java:135)\n\tat com.yahoo.processing.execution.Execution.process(Execution.java:112)\n\tat com.yahoo.search.searchchain.Execution.search(Execution.java:500)\n\tat com.yahoo.prelude.querytransform.StemmingSearcher.search(StemmingSearcher.java:109)\n\tat com.yahoo.search.Searcher.process(Searcher.java:135)\n\tat com.yahoo.processing.execution.Execution.process(Execution.java:112)\n\tat com.yahoo.search.searchchain.Execution.search(Execution.java:500)\n\tat com.yahoo.search.significance.SignificanceSearcher.search(SignificanceSearcher.java:95)\n\tat com.yahoo.search.Searcher.process(Searcher.java:135)\n\tat com.yahoo.processing.execution.Execution.process(Execution.java:112)\n\tat com.yahoo.search.searchchain.Execution.search(Execution.java:500)\n\tat com.yahoo.search.searchers.InputCheckingSearcher.search(InputCheckingSearcher.java:63)\n\tat com.yahoo.search.Searcher.process(Searcher.java:135)\n\tat com.yahoo.processing.execution.Execution.process(Execution.java:112)\n\tat com.yahoo.search.searchchain.Execution.search(Execution.java:500)\n\tat com.yahoo.search.yql.FieldFiller.search(FieldFiller.java:37)\n\tat com.yahoo.search.Searcher.process(Searcher.java:135)\n\tat com.yahoo.processing.execution.Execution.process(Execution.java:112)\n\tat com.yahoo.search.searchchain.Execution.search(Execution.java:500)\n\tat com.yahoo.search.searchers.ValidateSameElementSearcher.search(ValidateSameElementSearcher.java:32)\n\tat com.yahoo.search.Searcher.process(Searcher.java:135)\n\tat com.yahoo.processing.execution.Execution.process(Execution.java:112)\n\tat com.yahoo.search.searchchain.Execution.search(Execution.java:500)\n\tat com.yahoo.search.searchers.ValidateFuzzySearcher.search(ValidateFuzzySearcher.java:38)\n\tat com.yahoo.search.Searcher.process(Searcher.java:135)\n\tat com.yahoo.processing.execution.Execution.process(Execution.java:112)\n\tat com.yahoo.search.searchchain.Execution.search(Execution.java:500)\n\tat com.yahoo.search.searchers.ValidateMatchPhaseSearcher.search(ValidateMatchPhaseSearcher.java:54)\n\tat com.yahoo.search.Searcher.process(Searcher.java:135)\n\tat com.yahoo.processing.execution.Execution.process(Execution.java:112)\n\tat com.yahoo.search.searchchain.Execution.search(Execution.java:500)\n\tat com.yahoo.search.searchers.ValidateNearestNeighborSearcher.search(ValidateNearestNeighborSearcher.java:51)\n\tat com.yahoo.search.Searcher.process(Searcher.java:135)\n\tat com.yahoo.processing.execution.Execution.process(Execution.java:112)\n\tat com.yahoo.search.searchchain.Execution.search(Execution.java:500)\n\tat com.yahoo.prelude.querytransform.RecallSearcher.search(RecallSearcher.java:47)\n\tat com.yahoo.search.Searcher.process(Searcher.java:135)\n\tat com.yahoo.processing.execution.Execution.process(Execution.java:112)\n\tat com.yahoo.search.searchchain.Execution.search(Execution.java:500)\n\tat com.yahoo.search.querytransform.WandSearcher.search(WandSearcher.java:159)\n\tat com.yahoo.search.Searcher.process(Searcher.java:135)\n\tat com.yahoo.processing.execution.Execution.process(Execution.java:112)\n\tat com.yahoo.search.searchchain.Execution.search(Execution.java:500)\n\tat com.yahoo.search.grouping.GroupingValidator.search(GroupingValidator.java:65)\n\tat com.yahoo.search.Searcher.process(Searcher.java:135)\n\tat com.yahoo.processing.execution.Execution.process(Execution.java:112)\n\tat com.yahoo.search.searchchain.Execution.search(Execution.java:500)\n\tat com.yahoo.search.searchers.QueryValidator.search(QueryValidator.java:41)\n\tat com.yahoo.search.Searcher.process(Searcher.java:135)\n\tat com.yahoo.processing.execution.Execution.process(Execution.java:112)\n\tat com.yahoo.search.searchchain.Execution.search(Execution.java:500)\n\tat com.yahoo.search.querytransform.SortingDegrader.search(SortingDegrader.java:52)\n\tat com.yahoo.search.Searcher.process(Searcher.java:135)\n\tat com.yahoo.processing.execution.Execution.process(Execution.java:112)\n\tat com.yahoo.search.searchchain.Execution.search(Execution.java:500)\n\tat com.yahoo.search.querytransform.RangeQueryOptimizer.search(RangeQueryOptimizer.java:43)\n\tat com.yahoo.search.Searcher.process(Searcher.java:135)\n\tat com.yahoo.processing.execution.Execution.process(Execution.java:112)\n\tat com.yahoo.search.searchchain.Execution.search(Execution.java:500)\n\tat com.yahoo.prelude.querytransform.LiteralBoostSearcher.search(LiteralBoostSearcher.java:38)\n\tat com.yahoo.search.Searcher.process(Searcher.java:135)\n\tat com.yahoo.processing.execution.Execution.process(Execution.java:112)\n\tat com.yahoo.search.searchchain.Execution.search(Execution.java:500)\n\tat com.yahoo.prelude.querytransform.CJKSearcher.search(CJKSearcher.java:43)\n\tat com.yahoo.search.Searcher.process(Searcher.java:135)\n\tat com.yahoo.processing.execution.Execution.process(Execution.java:112)\n\tat com.yahoo.search.searchchain.Execution.search(Execution.java:500)\n\tat com.yahoo.search.querytransform.DefaultPositionSearcher.search(DefaultPositionSearcher.java:63)\n\tat com.yahoo.search.Searcher.process(Searcher.java:135)\n\tat com.yahoo.processing.execution.Execution.process(Execution.java:112)\n\tat com.yahoo.search.searchchain.Execution.search(Execution.java:500)\n\tat com.yahoo.search.querytransform.NGramSearcher.search(NGramSearcher.java:66)\n\tat com.yahoo.search.Searcher.process(Searcher.java:135)\n\tat com.yahoo.processing.execution.Execution.process(Execution.java:112)\n\tat com.yahoo.search.searchchain.Execution.search(Execution.java:500)\n\tat com.yahoo.search.federation.FederationSearcher.search(FederationSearcher.java:287)\n\tat com.yahoo.search.federation.FederationSearcher.search(FederationSearcher.java:267)\n\tat com.yahoo.search.federation.FederationSearcher.search(FederationSearcher.java:262)\n\tat com.yahoo.search.Searcher.process(Searcher.java:135)\n\tat com.yahoo.processing.execution.Execution.process(Execution.java:112)\n\tat com.yahoo.search.searchchain.Execution.search(Execution.java:500)\n\tat com.yahoo.search.searchers.OpportunisticWeakAndSearcher.search(OpportunisticWeakAndSearcher.java:40)\n\tat com.yahoo.search.Searcher.process(Searcher.java:135)\n\tat com.yahoo.processing.execution.Execution.process(Execution.java:112)\n\tat com.yahoo.search.searchchain.Execution.search(Execution.java:500)\n\tat com.yahoo.search.querytransform.WeakAndReplacementSearcher.search(WeakAndReplacementSearcher.java:32)\n\tat com.yahoo.search.Searcher.process(Searcher.java:135)\n\tat com.yahoo.processing.execution.Execution.process(Execution.java:112)\n\tat com.yahoo.search.searchchain.Execution.search(Execution.java:500)\n\tat com.yahoo.prelude.searcher.BlendingSearcher.search(BlendingSearcher.java:57)\n\tat com.yahoo.search.Searcher.process(Searcher.java:135)\n\tat com.yahoo.processing.execution.Execution.process(Execution.java:112)\n\tat com.yahoo.search.searchchain.Execution.search(Execution.java:500)\n\tat com.yahoo.search.grouping.GroupingQueryParser.search(GroupingQueryParser.java:52)\n\tat com.yahoo.search.Searcher.process(Searcher.java:135)\n\tat com.yahoo.processing.execution.Execution.process(Execution.java:112)\n\tat com.yahoo.search.searchchain.Execution.search(Execution.java:500)\n\tat com.yahoo.prelude.semantics.SemanticSearcher.search(SemanticSearcher.java:90)\n\tat com.yahoo.search.Searcher.process(Searcher.java:135)\n\tat com.yahoo.processing.execution.Execution.process(Execution.java:112)\n\tat com.yahoo.search.searchchain.Execution.search(Execution.java:500)\n\tat com.yahoo.prelude.searcher.PosSearcher.search(PosSearcher.java:62)\n\tat com.yahoo.search.Searcher.process(Searcher.java:135)\n\tat com.yahoo.processing.execution.Execution.process(Execution.java:112)\n\tat com.yahoo.search.searchchain.Execution.search(Execution.java:500)\n\tat com.yahoo.prelude.searcher.JuniperSearcher.search(JuniperSearcher.java:74)\n\tat com.yahoo.search.Searcher.process(Searcher.java:135)\n\tat com.yahoo.processing.execution.Execution.process(Execution.java:112)\n\tat com.yahoo.search.searchchain.Execution.search(Execution.java:500)\n\tat com.yahoo.search.yql.FieldFilter.search(FieldFilter.java:35)\n\tat com.yahoo.search.Searcher.process(Searcher.java:135)\n\tat com.yahoo.processing.execution.Execution.process(Execution.java:112)\n\tat com.yahoo.search.searchchain.Execution.search(Execution.java:500)\n\tat com.yahoo.search.yql.MinimalQueryInserter.search(MinimalQueryInserter.java:86)\n\tat com.yahoo.search.Searcher.process(Searcher.java:135)\n\tat com.yahoo.processing.execution.Execution.process(Execution.java:112)\n\tat com.yahoo.search.searchchain.Execution.search(Execution.java:500)\n\tat com.yahoo.prelude.searcher.FieldCollapsingSearcher.search(FieldCollapsingSearcher.java:95)\n\tat com.yahoo.search.Searcher.process(Searcher.java:135)\n\tat com.yahoo.processing.execution.Execution.process(Execution.java:112)\n\tat com.yahoo.search.searchchain.Execution.search(Execution.java:500)\n\tat com.yahoo.prelude.querytransform.PhrasingSearcher.search(PhrasingSearcher.java:60)\n\tat com.yahoo.search.Searcher.process(Searcher.java:135)\n\tat com.yahoo.processing.execution.Execution.process(Execution.java:112)\n\tat com.yahoo.search.searchchain.Execution.search(Execution.java:500)\n\tat com.yahoo.prelude.statistics.StatisticsSearcher.search(StatisticsSearcher.java:235)\n\tat com.yahoo.search.Searcher.process(Searcher.java:135)\n\tat com.yahoo.processing.execution.Execution.process(Execution.java:112)\n\tat com.yahoo.search.searchchain.Execution.search(Execution.java:500)\n\tat com.yahoo.search.handler.SearchHandler.searchAndFill(SearchHandler.java:358)\n\tat com.yahoo.search.handler.SearchHandler.search(SearchHandler.java:403)\n\tat com.yahoo.search.handler.SearchHandler.handleBody(SearchHandler.java:279)\n\tat com.yahoo.search.handler.SearchHandler.handle(SearchHandler.java:188)\n\tat com.yahoo.container.jdisc.ThreadedHttpRequestHandler.handle(ThreadedHttpRequestHandler.java:82)\n\tat com.yahoo.container.jdisc.ThreadedHttpRequestHandler.handleRequest(ThreadedHttpRequestHandler.java:92)\n\tat com.yahoo.container.jdisc.ThreadedRequestHandler$RequestTask.processRequest(ThreadedRequestHandler.java:190)\n\tat com.yahoo.container.jdisc.ThreadedRequestHandler$RequestTask.run(ThreadedRequestHandler.java:184)\n\tat java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1136)\n\tat java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:635)\n\tat java.base/java.lang.Thread.run(Thread.java:840)\n"
            }
        ]
    }
}
```

The error is `Failed: No suitable groups to dispatch query. Rejected: []`.
It seems that even more config is needed.

In the Vespa container log there is the same exception.

Questions:
- How to add the missing config?
- Or is there a hack to work around this?
- How does the default `<search/>` is created or set up?
