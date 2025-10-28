# vespa-graalvm

Let's try running Vespa on GraalVM 25

```shell
docker build -f graalvm25.Dockerfile -t vespa-graalvm-25 .
# Run a shell inside 
docker run --rm -it --entrypoint=/bin/bash vespa-graalvm-25 -c 'java -version'

# Run the Vespa on top of GraalVM in the background 
docker run --rm --detach \
  --name vespa-graalvm \
  --hostname vespa-container \
  --publish 0.0.0.0:8080:8080 \
  --publish 0.0.0.0:19092:19092 \
  --publish 0.0.0.0:19050:19050 \
  --publish 0.0.0.0:19071:19071 \
  vespa-graalvm-25
```

Deploy application package:
```shell
vespa deploy -t local
```

Check logs:
```shell
docker logs vespa-graalvm
```

Feed and query docs:
```shell
echo '{"id":"id:lucene:lucene::1","fields":{"mytext": "vespa"}}' | vespa feed -
#{
#  "feeder.operation.count": 1,
#  "feeder.seconds": 0.182,
#  "feeder.ok.count": 1,
#  "feeder.ok.rate": 1.000,
#  "feeder.error.count": 0,
#  "feeder.inflight.count": 0,
#  "http.request.count": 1,
#  "http.request.bytes": 30,
#  "http.request.MBps": 0.000,
#  "http.exception.count": 0,
#  "http.response.count": 1,
#  "http.response.bytes": 74,
#  "http.response.MBps": 0.000,
#  "http.response.error.count": 0,
#  "http.response.latency.millis.min": 181,
#  "http.response.latency.millis.avg": 181,
#  "http.response.latency.millis.max": 181,
#  "http.response.code.counts": {
#    "200": 1
#  }
#}

vespa query 'select * from sources * where true limit 1' 'ranking=unranked' 'timeout=5s' -t local --verbose
#{
#    "root": {
#        "id": "toplevel",
#        "relevance": 1.0,
#        "fields": {
#            "totalCount": 1
#        },
#        "coverage": {
#            "coverage": 100,
#            "documents": 1,
#            "full": true,
#            "nodes": 1,
#            "results": 1,
#            "resultsFull": 1
#        },
#        "children": [
#            {
#                "id": "id:lucene:lucene::1",
#                "relevance": 0.0,
#                "source": "content",
#                "fields": {
#                    "sddocname": "lucene",
#                    "documentid": "id:lucene:lucene::1",
#                    "mytext": "vespa"
#                }
#            }
#        ]
#    }
#}
```

All good.
Let's try running with ZGC.
