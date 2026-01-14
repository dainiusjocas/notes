# Vespa services.xml tricks

Let’s set up Vespa so that `doc1`, `doc2`, and `doc3` are in the content cluster.

Using [variants](https://docs.vespa.ai/en/operations/deployment-variants.html#services.xml-variants).

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

## Trick local vespa into an instance name

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
vespa config set application default.default.test
vespa config set target local

curl --header "Content-Type: application/zip" \
--data-binary @../application.zip \
"http://localhost:19071/application/v2/tenant/default/application/default/instance/my-instance-name/prepareandactivate"

curl --header "Content-Type: application/zip" \
--data-binary @../application.zip \
"http://localhost:19071/application/v4/tenant/default/application/default/instance/default/deploy/"


```shell
zip -r ../application.zip . 

curl -X POST \
     --header "Content-Type:application/zip" \
     --data-binary @../application.zip \
     "http://localhost:19071/application/v2/tenant/default/session"
     
#=> {"log":[],"tenant":"default","session-id":"2","prepared":"http://localhost:19071/application/v2/tenant/default/session/2/prepared","content":"http://localhost:19071/application/v2/tenant/default/session/2/content/","message":"Session 2 for tenant 'default' created."}% 
     
curl -X PUT \
     "http://localhost:19071/application/v2/tenant/default/session/2/prepared?applicationName=default&instance=demo"
#=> {"log":[],"tenant":"default","session-id":"2","activate":"http://localhost:19071/application/v2/tenant/default/session/2/active","message":"Session 2 for tenant 'default' prepared.","configChangeActions":{"restart":[],"refeed":[],"reindex":[]}}% 

curl -X PUT \
     "http://localhost:19071/application/v2/tenant/default/session/2/active"
#=> {"deploy":{"from":"unknown","timestamp":1768378663140,"internalRedeploy":false},"application":{"id":"default:default:default","checksum":"b7cee46b82e2c57dd2430f25661f6e65","generation":2,"previousActiveGeneration":0},"tenant":"default","session-id":"2","message":"Session 2 for tenant 'default' activated.","url":"http://localhost:19071/application/v2/tenant/default/application/default/environment/prod/region/default/instance/demo"}%   

curl "http://localhost:8080/ApplicationStatus"



curl -X POST \
     --header "Content-Type:application/zip" \
     --data-binary @application.zip \
     "http://localhost:19071/application/v2/tenant/default/session"
     
#=> {"log":[],"tenant":"default","session-id":"3","prepared":"http://localhost:19071/application/v2/tenant/default/session/3/prepared","content":"http://localhost:19071/application/v2/tenant/default/session/3/content/","message":"Session 3 for tenant 'default' created."}%                                                                    
 
     
curl -X PUT \
     "http://localhost:19071/application/v2/tenant/default/session/3/prepared?applicationName=default&instance=demo"
#=> {"log":[],"tenant":"default","session-id":"3","activate":"http://localhost:19071/application/v2/tenant/default/session/3/active","message":"Session 3 for tenant 'default' prepared.","configChangeActions":{"restart":[],"refeed":[],"reindex":[]}}%                                                                                            

curl -X PUT \
     "http://localhost:19071/application/v2/tenant/default/session/3/active"
#=> {"deploy":{"from":"unknown","timestamp":1768378827050,"internalRedeploy":false},"application":{"id":"default:default:default","checksum":"55f0eb1750f433129cd51c93c75ef028","generation":3,"previousActiveGeneration":0},"tenant":"default","session-id":"3","message":"Session 3 for tenant 'default' activated.","url":"http://localhost:19071/application/v2/tenant/default/application/default/environment/prod/region/default/instance/demo"}%                                                                                                                                                                                                                                                

curl "http://localhost:8080/ApplicationStatus"
```


```shell
vespa query 'select * from sources content  where true' -t http://localhost:8080
```

However, with curl we can work.
Unfortunately, the instance variant is not picked up.

```shell
curl -H Content-Type:application/json -d '{"fields": {"my_id": 2}}' \
http://localhost:8080/document/v1/doc2/doc2/docid/1

curl -H "Content-Type: application/json" \
--data '{"yql" : "select * from sources * where true", "model.restrict":"doc2"}' \
http://localhost:8080/search/ | jq .

curl -H Content-Type:application/json -d '{"id": "id:doc4:doc4::1", "fields": {"my_id": 2}}' \
http://localhost:8080/document/v1/doc4/doc4/docid/1

curl -H "Content-Type: application/json" \
--data '{"yql" : "select * from sources * where true", "model.restrict":"doc4"}' \
http://localhost:8080/search/ | jq .
```
