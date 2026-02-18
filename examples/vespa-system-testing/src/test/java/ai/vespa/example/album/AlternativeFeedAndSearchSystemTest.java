package ai.vespa.example.album;

import ai.vespa.example.album.systemtests.BaseSystemTest;
import ai.vespa.hosted.cd.SystemTest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.hamcrest.Matchers.*;

@SystemTest
public class AlternativeFeedAndSearchSystemTest extends BaseSystemTest {

    @Test
    void feedAndSearch() {
        var yql = "SELECT * FROM music WHERE artist CONTAINS 'coldplay'";
        given("all documents are deleted from the cluster")
                .deleteAll("music")
                .expectDeleted();
        when("searching for music")
                .search("yql", yql, "timeout", "10s");
        then("no documents are found")
                .status(200)
                .body("/root/fields/totalCount", equalTo(0L));

        when("a document is fed and searched")
                .feed("music", "test1",
                        "artist", "Coldplay")
                .status(200)
                .and()
                .search("yql", yql, "timeout", "10s");
        then("1 document is found and the artist is Coldplay")
                .body("/root/fields/totalCount", equalTo(1L),
                        "/root/children/0/fields/artist", containsString("Coldplay"));
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
