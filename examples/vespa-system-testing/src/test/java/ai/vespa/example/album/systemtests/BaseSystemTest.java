package ai.vespa.example.album.systemtests;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BaseSystemTest {
    private VespaTester testerAfterWhen = null;
    private final Map<String, JsonNode> captures = new HashMap<>();

    private VespaTester tester() {
        return new VespaTester.Builder(contentClusters())
                .captures(captures)
                .namespace(namespace())
                .verbose(verbose())
                .build();
    }

    protected VespaTester given(String... comments) {
        var tester = tester();
        if (scenario() && comments.length > 0) {
            String callerName = StackWalker.getInstance()
                    .walk(frames -> frames
                            .skip(1)
                            .findFirst()
                            .map(StackWalker.StackFrame::getMethodName)
                            .orElse("Unknown"));
            System.out.println("Scenario Outline: '" +
                    this.getClass().getSimpleName() +
                    ":" + callerName + "'\n  given: "
                    + String.join(" ", comments));
        }
        return tester;
    }

    protected VespaTester when(String... comments) {
        if (scenario() && comments.length > 0) {
            System.out.println("  when: " + String.join(" ", comments));
        }
        testerAfterWhen = tester();
        return testerAfterWhen;
    }

    protected VespaTester then(String... comments) {
        if (testerAfterWhen == null) {
            throw new IllegalStateException("`then` should be preceded by `when`");
        }
        if (scenario() && comments.length > 0) {
            System.out.println("  then: " + String.join(" ", comments));
        }
        return testerAfterWhen;
    }

    protected Object take(String id) {
        return take(id, "");
    }

    protected Object take(String id, String jsonPath) {
        return VespaTester.JSON.take(captures, id, jsonPath);
    }

    // Should be overridden by subclasses to configure for the application
    protected List<String> contentClusters() {
        return List.of("content");
    }
    protected String namespace() { return "namespace"; }
    protected boolean verbose() { return false; }
    protected boolean scenario() { return true; }
}
