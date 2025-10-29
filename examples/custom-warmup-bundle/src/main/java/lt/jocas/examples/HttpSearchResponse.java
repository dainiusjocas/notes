package lt.jocas.examples;

import ai.vespa.metrics.ContainerMetrics;
import com.yahoo.collections.ListMap;
import com.yahoo.container.handler.Timing;
import com.yahoo.container.jdisc.ExtendedResponse;
import com.yahoo.container.logging.HitCounts;
import com.yahoo.jdisc.HeaderFields;
import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.handler.CompletionHandler;
import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.processing.rendering.AsynchronousSectionedRenderer;
import com.yahoo.processing.rendering.Renderer;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.handler.SearchResponse;
import com.yahoo.search.result.Hit;
import com.yahoo.yolean.trace.TraceNode;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class HttpSearchResponse extends ExtendedResponse {

    private final Result result;
    private final Query query;
    private final Renderer<Result> rendererCopy;
    private final Metric metric;
    private final Timing timing;
    private final HitCounts hitCounts;
    private final TraceNode trace;

    public HttpSearchResponse(int status, Result result, Query query, Renderer<Result> renderer) {
        this(status, result, query, renderer, null, null);
    }

    public HttpSearchResponse(int status, Result result, Query query, Renderer<Result> renderer, TraceNode trace, Metric metric) {
        super(status);
        this.query = query;
        this.result = result;
        this.rendererCopy = renderer;
        this.metric = metric;
        this.timing = SearchResponse.createTiming(query, result);
        this.hitCounts = SearchResponse.createHitCounts(query, result);
        this.trace = trace;
        populateHeaders(headers(), result.getHeaders(false));
    }

    /**
     * Copy custom HTTP headers from the search result over to the HTTP response.
     *
     * @param outputHeaders the headers which will be sent to a client
     * @param searchHeaders the headers from the search result, or null
     */
    private static void populateHeaders(HeaderFields outputHeaders,
                                        ListMap<String, String> searchHeaders) {
        if (searchHeaders == null) {
            return;
        }
        for (Map.Entry<String, List<String>> header : searchHeaders.entrySet()) {
            for (String value : header.getValue()) {
                outputHeaders.add(header.getKey(), value);
            }
        }
    }

    @Override
    public void render(OutputStream output, ContentChannel networkChannel, CompletionHandler handler) throws IOException {
        if (rendererCopy instanceof AsynchronousSectionedRenderer<Result> renderer) {
            renderer.setNetworkWiring(networkChannel, handler);
        }
        try {
            try {
                long nanoStart = System.nanoTime();
                CompletableFuture<Boolean> promise = asyncRender(output);
                if (metric != null) {
                    promise.whenComplete((__, ___) -> new RendererLatencyReporter(nanoStart).run());
                }
            } finally {
                if (!(rendererCopy instanceof AsynchronousSectionedRenderer)) {
                    output.flush();
                }
            }
        } finally {
            if (networkChannel != null && !(rendererCopy instanceof AsynchronousSectionedRenderer)) {
                networkChannel.close(handler);
            }
        }
    }

    public CompletableFuture<Boolean> asyncRender(OutputStream stream) {
        return asyncRender(result, query, rendererCopy, stream);
    }

    public static CompletableFuture<Boolean> asyncRender(Result result,
                                                         Query query,
                                                         Renderer<Result> renderer,
                                                         OutputStream stream) {
        trimHits(result);
        removeEmptySummaryFeatureFields(result);
        return renderer.renderResponse(stream, result, query.getModel().getExecution(), query);
    }

    static void trimHits(Result result) {
        if (result.getConcreteHitCount() > result.hits().getQuery().getHits()) {
            result.hits().trim(0, result.hits().getQuery().getHits());
        }
    }

    // Remove (the empty) summary feature field if not requested.
    static void removeEmptySummaryFeatureFields(Result result) {
        // TODO: Move to some searcher in Vespa backend search chains
        if ( ! result.hits().getQuery().getRanking().getListFeatures())
            for (Iterator<Hit> i = result.hits().unorderedIterator(); i.hasNext();)
                i.next().removeField(Hit.RANKFEATURES_FIELD);
    }

    static final String RENDERER_DIMENSION = "renderer";
    static final String MIME_DIMENSION = "mime";
    static final String RENDER_LATENCY_METRIC = ContainerMetrics.JDISC_RENDER_LATENCY.baseName();

    private class RendererLatencyReporter implements Runnable {

        final long nanoStart;

        RendererLatencyReporter(long nanoStart) { this.nanoStart = nanoStart; }


        @Override
        public void run() {
            long latencyNanos = System.nanoTime() - nanoStart;
            Metric.Context ctx = metric.createContext(Map.of(
                    RENDERER_DIMENSION, rendererCopy.getClassName(),
                    MIME_DIMENSION, rendererCopy.getMimeType()));
            metric.set(RENDER_LATENCY_METRIC, latencyNanos, ctx);
        }
    }
}
