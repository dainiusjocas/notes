package ai.vespa.example.album.systemtests;

import ai.vespa.cloud.ApplicationId;
import ai.vespa.hosted.cd.Endpoint;
import ai.vespa.hosted.cd.TestRuntime;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.thisptr.jackson.jq.BuiltinFunctionLoader;
import net.thisptr.jackson.jq.JsonQuery;
import net.thisptr.jackson.jq.Scope;
import net.thisptr.jackson.jq.Version;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.stream.Collectors;

import static java.net.http.HttpRequest.BodyPublishers.ofString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

public class VespaTester {
    private static final String NAMESPACE = "namespace";

    private static final Endpoint endpoint;
    private static final ObjectMapper mapper;
    private static final DefaultPrettyPrinter printer;
    private static final Scope rootScope = Scope.newEmptyScope();

    static {
        TestRuntime testRuntime = TestRuntime.get();
        endpoint = testRuntime.deploymentToTest().endpoint("default");
        mapper = new ObjectMapper();
        printer = new DefaultPrettyPrinter();
        var indenter = new DefaultIndenter("  ", DefaultIndenter.SYS_LF);
        printer.indentObjectsWith(indenter);
        printer.indentArraysWith(indenter);
        BuiltinFunctionLoader.getInstance().loadFunctions(Version.LATEST, rootScope);
    }

    private final Map<String, JsonNode> captures;
    private final Map<Object, Object> overridesForNextRequest;
    private final List<String> contentClusters;
    private final String namespace;
    private final Session session;
    private final boolean verbose;

    private VespaTester(Builder builder) {
        contentClusters = builder.contentClusters;
        captures = builder.captures;
        namespace = builder.namespace;
        verbose = builder.verbose;
        session = new Session();
        overridesForNextRequest = new HashMap<>();
    }

    // Overrides for the next request.
    public VespaTester with(Object... overrides) {
        overridesForNextRequest.putAll(Utils.toMap(overrides));
        return this;
    }
    public VespaTester with(String jsonString) {
        var vals = JSON.toObject(JSON.parse(jsonString), Map.class);
        if (vals instanceof Map<?, ?> map) {
            overridesForNextRequest.putAll(map);
        } else {
            throw new IllegalArgumentException("Invalid JSON string: must be a map");
        }
        return this;
    }

    public VespaTester deleteAll(String... schemas) {
        // Call delete all docs in schemas from all content clusters
        for (var schema : schemas) {
            for (var cluster : contentClusters) {
                HTTP.DELETE(session, "/document/v1/", Map.of(
                        "selection", String.format("%s AND id.namespace == \"%s\"", schema, namespace),
                        "cluster", cluster,
                        "timeout", "5s"));
            }
        }
        return this;
    }

    public VespaTester delete(Object docPath) {
        HTTP.DELETE(session, docPath.toString());
        return this;
    }

    // Expects at least one deletion to succeed.
    public VespaTester expectDeleted() {
        Set<Integer> deletionStatuses = session.requestResponses
                .stream()
                .map(RequestResponse::response)
                .map(HttpResponse::statusCode)
                .collect(Collectors.toSet());
        Utils.verify(deletionStatuses, hasItem(200), "Expected at least one deletions to succeed");
        return this;
    }

    public VespaTester fetch(Object docPath) {
        HTTP.GET(session, docPath.toString());
        return this;
    }

    public VespaTester feed(String schema, String id, Object... fields) {
        var baseBody = new HashMap<>(Utils.toMap(fields));
        baseBody.putAll(overridesForNextRequest);
        overridesForNextRequest.clear();
        Map<Object, Object> document = Map.of("fields", baseBody);
        var documentPath = String.format("/document/v1/%s/%s/docid/%s", namespace, schema, id);
        HTTP.POST(session, documentPath, document);
        return this;
    }

    public VespaTester search(Object... queryParameters) {
        var baseBody = new HashMap<>(Utils.toMap(queryParameters));
        baseBody.putAll(overridesForNextRequest);
        overridesForNextRequest.clear();
        HTTP.POST(session, "/search/", baseBody);
        return this;
    }

    public VespaTester status(int expected) {
        return status(equalTo(expected));
    }

    public VespaTester status(Matcher matcher) {
        Utils.verify(session.respStatus(), matcher, "");
        return this;
    }

    public VespaTester header(String key, String value) {
        Utils.verify(session.headerMap(), hasEntry(equalTo(key), contains(value)), Printer.allDebug(session.requestResponses));
        return this;
    }

    public VespaTester header(Matcher matcher) {
        Utils.verify(session.headerMap(), matcher, Printer.allDebug(session.requestResponses));
        return this;
    }

    public VespaTester hitsCount(long expectedHitsCount) {
        return hitCount(equalTo(expectedHitsCount));
    }

    public VespaTester hitCount(Matcher matcher) {
        return body("/root/fields/totalCount", matcher);
    }

    public VespaTester expectKeys(String jq, List<String> expectedKeys) {
        Iterable keyMatchers = expectedKeys.stream().map(Matchers::hasKey).toList();
        return body(jq, allOf(keyMatchers));
    }

    public VespaTester expectSome(String jq) {
        body(jq, notNullValue());
        return this;
    }

    public VespaTester expectEquals(String jq1, String jq2) {
        return body(jq1, equalTo(JSON.toObject(JSON.at(session.lastResponseBody(), jq2))));
    }

    public VespaTester expectNotEquals(String jq1, String jq2) {
        return body(jq1, not(equalTo(JSON.toObject(JSON.at(session.lastResponseBody(), jq2)))));
    }

    public VespaTester body(String jq, Matcher matcher) {
        var jsonNode = JSON.at(session.lastResponseBody(), jq);
        var value = JSON.toObject(jsonNode);
        Utils.verify(value, matcher, jq);
        return this;
    }

    public VespaTester body(String jq, Object expected) {
        var jsonNode = JSON.at(session.lastResponseBody(), jq);
        var actual = JSON.toObject(jsonNode, expected.getClass());
        Utils.verify(actual, equalTo(expected), jq);
        return this;
    }

    public VespaTester capture(String id) {
        return capture(id, ".");
    }

    public VespaTester capture(String id, String jq) {
        captures.put(id, JSON.at(JSON.parse(session.lastRequestResponse().response().body()), jq));
        return this;
    }

    public VespaTester and() {
        return this;
    }

    public JsonNode body(String jq) {
        return JSON.at(session.lastResponseBody(), jq);
    }

    public JsonNode body() {
        return body(".");
    }

    // Pretty prints response JSON. Useful for debugging, add it into the chain.
    public VespaTester printResponse(String jq) {
        Printer.print(JSON.pp(session.lastRequestResponse().response.body(), jq));
        return this;
    }

    public VespaTester printResponse() {
        return printResponse(".");
    }

    public VespaTester printHeader() {
        Printer.print(session.headerMap());
        return this;
    }

    public VespaTester printRequest() {
        Printer.print(Printer.ppRequest(session.lastRequestResponse()));
        return this;
    }

    // Prints requests in Curl format and pretty printed responses in order.
    public VespaTester debug() {
        Printer.print(Printer.allDebug(session.requestResponses));
        return this;
    }

    public static class Builder {
        private final List<String> contentClusters;
        private Map<String, JsonNode> captures;
        private String namespace = NAMESPACE;
        private boolean verbose = false;

        public Builder(List<String> contentClusters) {
            this.contentClusters = contentClusters;
        }

        public Builder captures(Map<String, JsonNode> captures) {
            this.captures = captures;
            return this;
        }

        public Builder namespace(String namespace) {
            this.namespace = namespace;
            return this;
        }

        public Builder verbose(boolean verbose) {
            this.verbose = verbose;
            return this;
        }

        public VespaTester build() {
            return new VespaTester(this);
        }
    }

    private class Session {
        private final List<RequestResponse> requestResponses;

        Session() {
            requestResponses = new ArrayList<>();
        }

        RequestResponse lastRequestResponse() {
            return requestResponses.get(requestResponses.size() - 1);
        }

        JsonNode lastResponseBody() {
            return JSON.parse(lastRequestResponse().response().body());
        }

        private Map<String, List<String>> headerMap() {
            return session.lastRequestResponse().response.headers().map();
        }

        public int respStatus() {
            return lastRequestResponse().response.statusCode();
        }

        public boolean isEmpty() {
            return requestResponses.isEmpty();
        }

        public void add(String requestBody, HttpResponse<String> response) {
            if (verbose) {
                Printer.print(Printer.debugMsg(new RequestResponse(requestBody, response)));
            }
            requestResponses.add(new RequestResponse(requestBody, response));
        }
    }

    private record RequestResponse(String requestBody, HttpResponse<String> response) {
    }

    public static class JSON {
        static Object toObject(JsonNode jsonNode) {
            try {
                return switch (jsonNode.getNodeType()) {
                    case BINARY, STRING -> jsonNode.asText();
                    case BOOLEAN -> jsonNode.asBoolean();
                    case NULL, MISSING -> null;
                    case NUMBER -> {
                        if (jsonNode.isDouble()) {
                            yield jsonNode.asDouble();
                        } else {
                            yield jsonNode.asLong();
                        }
                    }
                    case OBJECT, POJO -> mapper.treeToValue(jsonNode, Map.class);
                    case ARRAY -> mapper.treeToValue(jsonNode, List.class);
                };
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }
        static Object toObject(JsonNode jsonNode, Class<?> klass) {
            try {
                return mapper.treeToValue(jsonNode, klass);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }

        static JsonNode parse(String json) {
            try {
                return mapper.readTree(json);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }

        static String pp(JsonNode node) {
            try {
                return mapper.writer(printer).writeValueAsString(node);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }

        static JsonNode at(JsonNode jsonNode, String jq) {
            return executeJq(jq, jsonNode);
        }

        private static JsonNode executeJq(String jqQuery, JsonNode input) {
            try {
                JsonQuery query = JsonQuery.compile(jqQuery, Version.LATEST);
                final List<JsonNode> out = new ArrayList<>();
                query.apply(Scope.newChildScope(rootScope), input, out::add);
                return out.isEmpty() ? null : out.get(0);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        static String pp(String json, String jq) {
            return pp(JSON.at(JSON.parse(json), jq));
        }

        static String pp(String json) {
            return pp(json, ".");
        }

        public static Object take(Map<String, JsonNode> captures, String id, String jq) {
            return toObject(JSON.at(captures.get(id), jq));
        }
    }

    private static class Utils {
        static <T> void verify(T value, Matcher<? super T> matcher, String... messages) {
            var reason = String.join("\n---------------\n", messages);
            assertThat(reason, value, matcher);
        }

        static Map<Object, Object> toMap(Object[] keyVals) {
            if (keyVals.length == 1 && keyVals[0] instanceof Map) {
                //noinspection unchecked
                return (Map<Object, Object>) keyVals[0];
            }
            if (keyVals.length % 2 != 0) {
                throw new IllegalArgumentException("Invalid entries: must be an even number of arguments (key-value pairs)");
            }
            Map<Object, Object> fields = new HashMap<>();
            for (int i = 0; i < keyVals.length; i += 2) {
                fields.put(keyVals[i], keyVals[i + 1]);
            }
            return fields;
        }
    }

    private static class Printer {
        static void print(Object o) {
            System.out.println(o);
        }

        // Constructs a string that is also usable as a Curl command
        static String ppRequest(RequestResponse rr) {
            StringBuilder headers = new StringBuilder();
            for (Map.Entry<String, List<String>> entry : rr.response.request().headers().map().entrySet()) {
                headers.append(String.format("\"%s: %s\"", entry.getKey(), entry.getValue().get(0)));
            }
            var method = String.format(" -X%s ", rr.response.request().method());
            var hdr = headers.toString().isBlank() ? "" : String.format(" -H '%s' ", headers);
            var data = rr.requestBody().isBlank() ? "" : String.format(" -d '%s' ", rr.requestBody());
            return "curl -s" + method +
                    hdr +
                    rr.response.uri().toString() +
                    data;
        }

        static String debugMsg(RequestResponse rr) {
            return Printer.ppRequest(rr) +
                    JSON.pp(rr.response.body()) +
                    "\nHTTP Status: " +
                    rr.response().statusCode();
        }

        static String allDebug(List<RequestResponse> requestResponses) {
            return requestResponses.stream()
                    .map(Printer::debugMsg)
                    .collect(Collectors.joining("\n----------------------\n"));
        }
    }

    private static class HTTP {
        static void GET(Session session, String path) {
            var resp = endpoint.send(endpoint.request(path).GET());
            session.add("", resp);
        }

        static void DELETE(Session session, String path) {
            DELETE(session, path, Map.of());
        }

        static void DELETE(Session session, String path, Map<String, String> query) {
            var resp = endpoint.send(endpoint.request(path, query).DELETE());
            session.add("", resp);
        }

        static void POST(Session session, String path, Map<Object, Object> body) {
            try {
                var document = mapper.writeValueAsString(body);
                var post = endpoint.request(path)
                        .header("Content-Type", "application/json")
                        .POST(ofString(document));
                var response = endpoint.send(post);
                session.add(document, response);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
