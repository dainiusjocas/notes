package ai.vespa.example.album;

import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.searchchain.Execution;

public class DemoSearcher extends Searcher {

    @Override
    public Result search(Query query, Execution execution) {
        // HERE IS THE CUSTOM CODE
        query.trace("DemoSearcher Before fetching from content nodes", 1);
        var result =  execution.search(query);
        query.trace("Fetching demo1 summary class", 1);
        execution.fill(result, "demo1");
        if (query.properties().getBoolean("demo.demo2", false)) {
            query.trace("Fetching demo2 summary class", 1);
            execution.fill(result, "demo2");
        }

        return result;
    }
}
