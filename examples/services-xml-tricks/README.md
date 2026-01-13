# Vespa services.xml tricks

Let’s set up Vespa so that `doc1`, `doc2`, and `doc3` are in the content cluster.

## Take 1: run vanilla Vespa docker 

```shell
docker run \
  --detach \
  --rm \
  --name vespa-demo \
  --publish 0.0.0.0:8080:8080 \
  --publish 0.0.0.0:19050:19050 \
  --publish 0.0.0.0:19071:19071 \
  vespaengine/vespa:8.625.17
```

```shell
vespa deploy -t local
```

```shell
curl http://localhost:19071/config/v1/vespa.config.search.indexschema/content/search/cluster.content/
#=> {"children":[],"configs":["http://localhost:19071/config/v1/vespa.config.search.indexschema/content/search/cluster.content/doc2"]}% 
```
Only the `doc2` is present.

```shell
docker stop vespa-demo
```

# Take 2: specify the `dev` environment

```shell
docker run -e VESPA_ENVIRONMENT=dev \
  --detach \
  --rm \
  --name vespa-demo \
  --publish 0.0.0.0:8080:8080 \
  --publish 0.0.0.0:19050:19050 \
  --publish 0.0.0.0:19071:19071 \
  vespaengine/vespa:8.625.17
```

```shell
vespa deploy -t local
```

```shell
curl http://localhost:19071/config/v1/vespa.config.search.indexschema/content/search/cluster.content/
# => {"children":[],"configs":["http://localhost:19071/config/v1/vespa.config.search.indexschema/content/search/cluster.content/doc2","http://localhost:19071/config/v1/vespa.config.search.indexschema/content/search/cluster.content/doc3"]}% 
```

`doc2` and `doc3` are present.

It seems that if we do services.xml variants the default somehow no longer applies.

#=> {"children":[],"configs":["http://localhost:19071/config/v1/vespa.config.search.indexschema/content/search/cluster.content/doc2"]}%
#vespa query 'select * from sources * where true' 'model.restrict=doc1,doc2,oc3'
## Not present
#vespa query 'select * from sources * where true' 'model.restrict=doc2'
## yes
#vespa query 'select * from sources * where true' 'model.restrict=doc3'
## no
```
