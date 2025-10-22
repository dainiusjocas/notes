package lt.jocas.examples;

import ai.vespa.cloud.ZoneInfo;
import com.yahoo.component.annotation.Inject;
import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.container.core.ContainerHttpConfig;
import com.yahoo.container.handler.threadpool.ContainerThreadPool;
import com.yahoo.jdisc.Metric;
import com.yahoo.language.process.Embedder;
import com.yahoo.search.handler.SearchHandler;
import com.yahoo.search.query.profile.compiled.CompiledQueryProfileRegistry;
import com.yahoo.search.searchchain.ExecutionFactory;

public class MySearchHandler extends SearchHandler {
    @Inject
    public MySearchHandler(Metric metric, ContainerThreadPool threadpool, CompiledQueryProfileRegistry queryProfileRegistry, ContainerHttpConfig config, ComponentRegistry<Embedder> embedders, ExecutionFactory executionFactory, ZoneInfo zoneInfo) {
        super(metric, threadpool, queryProfileRegistry, config, embedders, executionFactory, zoneInfo);
    }
}
