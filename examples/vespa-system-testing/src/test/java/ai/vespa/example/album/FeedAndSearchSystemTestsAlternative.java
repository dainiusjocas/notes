package ai.vespa.example.album;

import ai.vespa.example.album.systemtests.BaseSystemTest;
import org.assertj.core.util.Lists;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.hamcrest.Matchers.*;

public class FeedAndSearchSystemTestsAlternative extends BaseSystemTest {
    @Test
    void feedAndSearch() {
        var yql = "SELECT * FROM music WHERE artist CONTAINS 'coldplay'";
        given("All documents are deleted from the cluster")
                .deleteAll("music")
                .expectDeleted();
        when("searching for music")
                .search("yql", yql, "timeout", "10s");
        then("no documents are found")
                .status(200)
                .body("/root/fields/totalCount", equalTo(0));

        when("a document is fed and searched")
                .feed("music", "test1",
                        "artist", "Coldplay")
                .status(200)
                .and()
                .search("yql", yql, "timeout", "10s");
        then("1 document is found")
                .body("/root/fields/totalCount", equalTo(1));
    }

    @Override
    protected String namespace() {
        return "mynamespace";
    }

    @Override
    protected List<String> contentClusters() {
        return List.of("music");
    }
}
