
<!-- Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root. -->

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="https://assets.vespa.ai/logos/Vespa-logo-green-RGB.svg">
  <source media="(prefers-color-scheme: light)" srcset="https://assets.vespa.ai/logos/Vespa-logo-dark-RGB.svg">
  <img alt="#Vespa" width="200" src="https://assets.vespa.ai/logos/Vespa-logo-dark-RGB.svg" style="margin-bottom: 25px;">
</picture>

# The problem

Vespa drops `summaryfeatures` field when custom searchers latest calls for a summary class that has `omit-summary-features` specidied.
The expected behaviour would be to add fields fetched with the summary and not to drop `summaryfeatures` field.

## Steps to reproduce

```shell
docker run \
  --detach \
  --rm \
  --name vespa-demo \
  --publish 0.0.0.0:8080:8080 \
  --publish 0.0.0.0:19050:19050 \
  --publish 0.0.0.0:19071:19071 \
  --publish 0.0.0.0:19092:19092 \
  vespaengine/vespa:8.637.17
```

```shell
echo '{"id": "id:music:music::1", "fields": {"artist": "demo", "album": "demo", "year": 2026}}' | vespa feed - -t local
```

```shell
mvn clean package && vespa deploy -w 60 -t local
```

When `demo1` summary is requested it includes the `summaryfeatures` field:
```shell
vespa query \
  'yql=select * from sources * where true' \
  'ranking.profile=rank_albums' \
  'searchChain=vespa' \
  'presentation.summary=demo1'
```
```json
{
    "root": {
        "id": "toplevel",
        "relevance": 1.0,
        "fields": {
            "totalCount": 1
        },
        "coverage": {
            "coverage": 100,
            "documents": 1,
            "full": true,
            "nodes": 1,
            "results": 1,
            "resultsFull": 1
        },
        "children": [
            {
                "id": "index:music/0/c4ca42388c5df0c643567eaa",
                "relevance": 0.0,
                "source": "music",
                "fields": {
                    "sddocname": "music",
                    "artist": "demo",
                    "summaryfeatures": {
                        "attribute(year)": 2026.0,
                        "vespa.summaryFeatures.cached": 0.0
                    }
                }
            }
        ]
    }
}
```

When `demo2` summary is requested it doesn't include the `summaryfeatures` field:
```shell
vespa query \
  'yql=select * from sources * where true' \
  'ranking.profile=rank_albums' \
  'searchChain=vespa' \
  'presentation.summary=demo2'
```
```json
{
    "root": {
        "id": "toplevel",
        "relevance": 1.0,
        "fields": {
            "totalCount": 1
        },
        "coverage": {
            "coverage": 100,
            "documents": 1,
            "full": true,
            "nodes": 1,
            "results": 1,
            "resultsFull": 1
        },
        "children": [
            {
                "id": "index:music/0/c4ca42388c5df0c643567eaa",
                "relevance": 0.0,
                "source": "music",
                "fields": {
                    "sddocname": "music",
                    "album": "demo"
                }
            }
        ]
    }
}
```

With the modified `demo` search chain which first `.fill(demo1)`, then `.fill(demo2)` then `summaryfeatures` field is not present in the result:
```shell
vespa query \
  'yql=select * from sources * where true' \
  'ranking.profile=rank_albums' \
  'searchChain=demo' \
  'presentation.summary=demo1' \
  'demo.demo2=true' \
  'trace.level=1'
```

And the final result doesn't have `summaryfeatures` field even though `presentation.summary=demo1` which includes summary features:
```json
{
    "trace": {
        "children": [
            {
                "message": "No query profile is used"
            },
            {
                "children": [
                    {
                        "message": "DemoSearcher Before fetching from content nodes"
                    },
                    {
                        "children": [
                            {
                                "message": "music.num0 search to dispatch: query=[TRUE] timeout=9999ms offset=0 hits=10 rankprofile[rank_albums] groupingSessionCache=true sessionId=5ce1721e-72bf-4409-974f-a85c2eab0fe3.1770805204920.24.rank_albums restrict=[music]"
                            },
                            {
                                "message": "music.num0 dispatch response: Result (1 of total 1 hits)"
                            },
                            {
                                "message": "music.num0 fill to dispatch: query=[TRUE] timeout=9999ms offset=0 hits=10 rankprofile[rank_albums] groupingSessionCache=true sessionId=5ce1721e-72bf-4409-974f-a85c2eab0fe3.1770805204920.24.rank_albums restrict=[music] summary='demo1'"
                            },
                            {
                                "message": "music.num0 fill to dispatch: query=[TRUE] timeout=9999ms offset=0 hits=10 rankprofile[rank_albums] groupingSessionCache=true sessionId=5ce1721e-72bf-4409-974f-a85c2eab0fe3.1770805204920.24.rank_albums restrict=[music] summary='demo2'"
                            }
                        ]
                    },
                    {
                        "message": "Fetching demo1 summary class"
                    },
                    {
                        "message": "Fetching demo2 summary class"
                    }
                ]
            }
        ]
    },
    "root": {
        "id": "toplevel",
        "relevance": 1.0,
        "fields": {
            "totalCount": 1
        },
        "coverage": {
            "coverage": 100,
            "documents": 1,
            "full": true,
            "nodes": 1,
            "results": 1,
            "resultsFull": 1
        },
        "children": [
            {
                "id": "index:music/0/c4ca42388c5df0c643567eaa",
                "relevance": 0.0,
                "source": "music",
                "fields": {
                    "sddocname": "music",
                    "album": "demo",
                    "artist": "demo"
                }
            }
        ]
    }
}
```

To prove that let's turn off fetching `demo2`:
```shell
vespa query \
  'yql=select * from sources * where true' \
  'ranking.profile=rank_albums' \
  'searchChain=demo' \
  'presentation.summary=demo1' \
  'demo.demo2=false' \
  'trace.level=1'
```

```json
{
    "trace": {
        "children": [
            {
                "message": "No query profile is used"
            },
            {
                "children": [
                    {
                        "message": "DemoSearcher Before fetching from content nodes"
                    },
                    {
                        "children": [
                            {
                                "message": "music.num0 search to dispatch: query=[TRUE] timeout=9999ms offset=0 hits=10 rankprofile[rank_albums] groupingSessionCache=true sessionId=5ce1721e-72bf-4409-974f-a85c2eab0fe3.1770805289329.25.rank_albums restrict=[music]"
                            },
                            {
                                "message": "music.num0 dispatch response: Result (1 of total 1 hits)"
                            },
                            {
                                "message": "music.num0 fill to dispatch: query=[TRUE] timeout=9999ms offset=0 hits=10 rankprofile[rank_albums] groupingSessionCache=true sessionId=5ce1721e-72bf-4409-974f-a85c2eab0fe3.1770805289329.25.rank_albums restrict=[music] summary='demo1'"
                            }
                        ]
                    },
                    {
                        "message": "Fetching demo1 summary class"
                    }
                ]
            }
        ]
    },
    "root": {
        "id": "toplevel",
        "relevance": 1.0,
        "fields": {
            "totalCount": 1
        },
        "coverage": {
            "coverage": 100,
            "documents": 1,
            "full": true,
            "nodes": 1,
            "results": 1,
            "resultsFull": 1
        },
        "children": [
            {
                "id": "index:music/0/c4ca42388c5df0c643567eaa",
                "relevance": 0.0,
                "source": "music",
                "fields": {
                    "sddocname": "music",
                    "artist": "demo",
                    "summaryfeatures": {
                        "attribute(year)": 2026.0,
                        "vespa.summaryFeatures.cached": 0.0
                    }
                }
            }
        ]
    }
}
```

--------------------------------------------------------------------------
# Vespa sample applications - album recommendation, with Java components

Follow [Vespa getting started](https://docs.vespa.ai/en/basics/deploy-an-application) to deploy this.

## Introduction

Vespa applications can contain Java components which are run inside Vespa to implement the
functionality required by the application.
This sample application is the same as album-recommendation,
but with some Java components, and the maven setup to build them added to it.

The Java components added here are of the most common type, 
[searchers](https://docs.vespa.ai/en/searcher-development.html),
which can modify the query and result, issue multiple queries for ech request etc.
There are also many other component types,
such as [document processors](https://docs.vespa.ai/en/document-processing.html), 
which can modify document data as it is written to Vespa,
and [handlers](https://docs.vespa.ai/en/jdisc/developing-request-handlers.html),
which can be used to let Vespa expose custom service APIs.


## Query tracing
See [MetalSearcher::search()](src/main/java/ai/vespa/example/album/MetalSearcher.java)
for an example of tracing in custom Searcher code.


## Custom metrics
See [MetalSearcher](src/main/java/ai/vespa/example/album/MetalSearcher.java)
for an examples of a custom metric - a counter for each successful lookup.
[services.xml](src/main/application/services.xml) has an `admin` section mapping the metric
into a `consumer` that can be used in the [metrics APIs](https://docs.vespa.ai/en/operations/metrics.html).
Also see [MetalSearcherTest](src/test/java/ai/vespa/example/album/MetalSearcherTest.java)
for how to implement unit tests.

Run a query like:

    $ vespa query "select * from music where album contains 'metallica'" searchChain=metalchain

to see the custom metric in
<a href="http://localhost:19092/metrics/v1/values?consumer=my-metrics" data-proofer-ignore>
http://localhost:19092/metrics/v1/values?consumer=my-metrics</a>

This code uses a [Counter](https://github.com/vespa-engine/vespa/blob/master/container-disc/src/main/java/com/yahoo/metrics/simple/Counter.java) -
A [Gauge](https://github.com/vespa-engine/vespa/blob/master/container-disc/src/main/java/com/yahoo/metrics/simple/Gauge.java)
example, with a dimension could be like:

````
public class HitCountSearcher extends Searcher {
    private static final String LANGUAGE_DIMENSION_NAME = "query_language";
    private static final String EXAMPLE_METRIC_NAME = "example_hitcounts";
    private final Gauge hitCountMetric;

    public HitCountSearcher(MetricReceiver receiver) {
        this.hitCountMetric = receiver.declareGauge(EXAMPLE_METRIC_NAME, Optional.empty(),
                new MetricSettings.Builder().build());
    }

    @Override
    public Result search(Query query, Execution execution) {
        Result result = execution.search(query);
        hitCountMetric
                .sample(result.getTotalHitCount(),
                        hitCountMetric.builder()
                                .set(LANGUAGE_DIMENSION_NAME, query.getModel().getParsingLanguage().languageCode())
                                .build());
        return result;
    }
}
````

Also see [histograms](https://docs.vespa.ai/en/operations-selfhosted/monitoring.html#histograms).
