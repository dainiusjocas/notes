package lt.jocas.examples;

import com.yahoo.prelude.fastsearch.PartialSummaryHandler;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.searchchain.Execution;

import java.util.Set;

import static com.yahoo.prelude.fastsearch.PartialSummaryHandler.PRESENTATION;

public class SkipFillSearcher extends Searcher {

    @Override
    public Result search(Query query, Execution execution) {
        return execution.search(query);
    }

    private static final Set<String> IGNORED_SUMMARY_FIELDS = Set.of("matchfeatures");

    private boolean isFillIgnorable(String summaryClass, Result result) {
        return PRESENTATION.equals(summaryClass) &&
                result.getQuery().getPresentation().getSummaryFields().equals(IGNORED_SUMMARY_FIELDS);
    }

    @Override
    public void fill(Result result, String summaryClass, Execution execution) {
        var adjustedSummaryClass = summaryClass;
        if (isFillIgnorable(summaryClass, result)) {
            // The `default` class works here because later Searcher class Vespa
            // checks that only some fields are needed
            // https://github.com/vespa-engine/vespa/blob/5dbf6c9d7feecd452dff7d494c540b0d31cf02bc/container-search/src/main/java/com/yahoo/prelude/fastsearch/PartialSummaryHandler.java#L239-L248
            // and creates synthetic names for which it checks
            // https://github.com/vespa-engine/vespa/blob/74e5c519e3d392b8ef34ced618fb90c7910adeb9/container-search/src/main/java/com/yahoo/search/Searcher.java#L164
            // whether they are already fetched,
            // thus declaring that for `.fill()` there is nothing to do.
            adjustedSummaryClass = PartialSummaryHandler.DEFAULT_CLASS;
        }
        execution.fill(result, adjustedSummaryClass);
    }
}
