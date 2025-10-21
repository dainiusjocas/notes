package lt.jocas.examples;


import ai.vespa.cloud.ZoneInfo;
import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.container.core.ContainerHttpConfig;
import com.yahoo.container.handler.threadpool.ContainerThreadPool;
import com.yahoo.jdisc.Metric;
import com.yahoo.language.process.Embedder;
import com.yahoo.search.handler.SearchHandler;
import com.yahoo.search.query.profile.compiled.CompiledQueryProfileRegistry;
import com.yahoo.search.searchchain.ExecutionFactory;

public class NoWarmupSearchHandler extends SearchHandler {

    public NoWarmupSearchHandler(Metric metric, ContainerThreadPool threadpool, CompiledQueryProfileRegistry queryProfileRegistry, ContainerHttpConfig config, ComponentRegistry<Embedder> embedders, ExecutionFactory executionFactory, ZoneInfo zoneInfo) {
        super(metric, threadpool, queryProfileRegistry, config, embedders, executionFactory, zoneInfo);
    }

    @Override
    private void warmup() {
//        try {
//            handle(HttpRequest.createTestRequest("/search/" +
//                            "?timeout=2s" +
//                            "&ranking.profile=unranked" +
//                            "&warmup=true" +
//                            "&metrics.ignore=true" +
//                            "&yql=select+*+from+sources+*+where+true+limit+0;",
//                    com.yahoo.jdisc.http.HttpRequest.Method.GET,
//                    nullInputStream()));
//        }
//        catch (RuntimeException e) {
//            log.log(Level.INFO, "Exception warming up search handler", e);
//        }
    }
}
