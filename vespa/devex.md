# Vespa DevEx: bridging the gap between laptop and production

## TL;DR

Leveraging Vespa `services.xml` [variants](https://docs.vespa.ai/en/operations/deployment-variants.html#services.xml-variants) with the `[instance](https://docs.vespa.ai/en/learn/glossary.html#instance)` is a neat way to improve DevEx.

## Just show me the code

E.g. we want to have `doc1` schema deployed in `prod` and `dev` instances, but `doc2` schema deployed only to `dev`.
Then `services.xml` variants can be:
```xml
<content id="content" version="1.0">
  <redundancy>1</redundancy>
  <documents deploy:instance="prod">
    <document type="doc1" mode="index"/>
  </documents>
  <documents deploy:instance="dev">
    <document type="doc1" mode="index"/>
    <document type="doc2" mode="index"/>
  </documents>
</content>
```

This command deploys the `prod` instance:
```shell
curl -X POST -s \
    --header "Content-Type:application/zip" \
    --data-binary @application.zip \
    "http://localhost:19071/application/v2/tenant/default/prepareandactivate?instance=prod"
```
It creates a Vespa application as if only the `deploy:instance="prod"` variant was specified in `services.xml`.

And when deployed to the `dev` self-hosted Vespa instance with:
```shell
curl -X POST -s \
  --header "Content-Type:application/zip" \
  --data-binary @application.zip \
  "http://localhost:19071/application/v2/tenant/default/prepareandactivate?instance=dev"
```
deploys the application as if only the `deploy:insatnce=prod` variant was specified.

For the full example see [here](https://github.com/dainiusjocas/notes/tree/main/examples/services-xml-tricks/single-node/).

## Abstract

This post aims to show how to increase both the velocity and confidence of Vespa application development.
The combination of `services.xml` variants and the `instance` part of the application ID allows conditional config overrides based on the deployment target in the single file.
Also, describes how to address development against Vespa Cloud or self-hosted Vespa. 

## DevEx

I've seen many different ways how teams attempt to manage Vespa applications: ranging from different applications, targeting only Vespa Cloud, deploy application from a volume mounted to the Vespa docker container, all the way to scripts that stitch files correctly based on the provided environment identifier.
Also, I hope that you run integration tests, don't you?

All of them introduce some compromises: either copying files, brittle scripts, a shared deployment that all tests run against.
Ideally, we'd like to have a single source of truth and have overrides only where they are needed.
Let's see how far we can go with current Vespa capabilities.

Currently, Vespa CLI doesn't support specifying [instance](https://github.com/vespa-engine/vespa/issues/35630).

### Some Baselines

Once the Docker image is downloaded, on a laptop, it takes ~30 seconds for Vespa to be ready to deploy an application and then for the first deployment to converge an additional ~20 seconds until the endpoints become available for feeding and querying.
Then the following deployments take ~3 seconds to be applied.
If you also build maven artifacts, add a some seconds to the mix.
This means that we could run an integration tests step in the CI / CD pipeline against a live Vespa application in ~1-2 minutes.
That is an acceptable time to prevent a context change and not to lose the flow.

Running parallel CI/CD jobs to test several different configurations is OK.

For a Vespa Cloud `dev` environment instance to become ready, it takes ~10 minutes.
This is a bit too long for a CI/CD pipeline intended to run on each push to the repository.

## Self-hosted Vespa

The biggest difference between local and production deployments is the number of nodes.
This can be solved elegantly by combining variants with pre-processing.

To have even fewer files in the repository, we can put all hosts into a single `hosts.xml` file.

## Mixed Vespa Cloud and self-hosted deployments

We can leverate the fact that when deploying to Vespa Cloud the `hosts.xml` file and configs about `nodes` are ignored.
Conversely, self-hosted Vespa ignores `deployment.xml` and the `nodes` config specific for the Vespa Cloud.
E.g. we can take an application deployed from Vespa Cloud and deploy it to a self-hosted single node Vespa instance without any changes, and most likely it is going to work.

### Tricks to speed up CD/CD

Instead of using Vanilla Vespa Docker, we could try leveraging a Docker image that already has some previous application versions deployed. This shaves about ~20 seconds off the time required to run the tests.

There are a couple of complications:
1. Where to store the image?
2. And what if the current application version requires an entry in the `validation-overrides.xml`? Fail

(1) is a regular DevOps question. I'll leave it to the reader to figure out how to solve it.

Failing to specify (2) means yet another dance of `observe failure -> fix -> commit -> push -> wait`: a waste of time.
As far as I know, there is no way to start Vespa that would allow disabling all validations.
To address it, we can leverage a neat trick: provide a `validation-overrides.xml` file with all breaking changes allowed for today.
This requires generating the file with today's date and a list of all [possible](https://github.com/vespa-engine/vespa/blob/54fb2825aef733095e59e9f197cf292e80c9263e/config-model-api/src/main/java/com/yahoo/config/application/api/ValidationId.java#L11) `allow` entries.
```shell
TODAY=$(date +"%Y-%m-%d") && echo \
"<validation-overrides>
  <allow until='$TODAY'>access-control</allow>
  <allow until='$TODAY'>certificate-removal</allow>
  <allow until='$TODAY'>cluster-size-reduction</allow>
  <allow until='$TODAY'>config-model-version-mismatch</allow>
  <allow until='$TODAY'>content-cluster-removal</allow>
  <allow until='$TODAY'>schema-removal</allow>
  <allow until='$TODAY'>data-plane-token-removal</allow>
  <allow until='$TODAY'>deployment-removal</allow>
  <allow until='$TODAY'>field-type-change</allow>
  <allow until='$TODAY'>global-document-change</allow>
  <allow until='$TODAY'>global-endpoint-change</allow>
  <allow until='$TODAY'>hnsw-settings-change</allow>
  <allow until='$TODAY'>indexing-change</allow>
  <allow until='$TODAY'>indexing-mode-change</allow>
  <allow until='$TODAY'>paged-setting-removal</allow>
  <allow until='$TODAY'>redundancy-increase</allow>
  <allow until='$TODAY'>redundancy-one</allow>
  <allow until='$TODAY'>resources-reduction</allow>
  <allow until='$TODAY'>skip-old-config-models</allow>
  <allow until='$TODAY'>tensor-type-change</allow>
  <allow until='$TODAY'>zone-endpoint-change</allow>
</validation-overrides>" > validation-overrides.xml
```

What if deployment still requires either a restart, reindexing or refeeding?
Reindexing or refeeding can be ignored if the base docker has no data.
As for the restarts, an additional script that checks the deployment response can initiate restart by executing a command in the docker. 


## What is missing?

`vespa-cli` doesn't pass the instance name on deployment to the self-hosted deployment endpoint.

## Gotchas

Sibling `services.xml` tags are resolved with the most specific taking the highest priority.
E.g. the snippet
```xml
<documents>
  <document type="doc1" mode="index"/>
  <document deploy:instance="demo" type="doc2" mode="index"/>
</documents>
```
when deploying to the `demo` instance in resolved as:
```shell
<documents>
  <document type="doc2" mode="index"/>
</documents>
```
The unexpected bit is that the `doc1` is not deployed!
Why? Because the `<document type="doc1" mode="index"/>` is treated as being taged with `deploy:instance=default` specified.
And given that `default!=demo`, `doc1` is not deployed.


## Yes, but how about variations in the query logic?

Hide the parameters under a query profile. And query profilea support variants on the `instance`.

TODO: examples.

## A note on the system tests

If you have custom Vespa components, then there is really no reason not to use `@SystemTest` in your CI/CD.
Unless, of course, you can't stand any language that is hosted on JVM, e.g. Java, Kotlin, Clojure, maybe even GraalVM could be leveraged to write such tests in Python or JavaScript. 

## Gotchas

When you have a running Vespa with a running instance `A`, and you deploy an instance `B`, then Vespa refuses it with an error:
```shell
{"error-code":"INVALID_APPLICATION_PACKAGE","message":"Invalid application: 'default.default.B' tried to allocate 'localhost', but the host is already allocated to another application"}
```

## Yes, but how

In case you want to have a variation in the list, my advice would be to have a variation on the list component istead of individual elements.

E.g. prefer this to:
```xml
<documents deploy:instance="demo">
  <document type="doc1" mode="index"/>
  <document type="doc2" mode="index"/>
</documents>
```

to
```xml
<documents>
  <document deploy:instance="demo" type="doc1" mode="index"/>
  <document deploy:instance="demo" type="doc2" mode="index"/>
</documents>
```
Because all child elements inherit deployment variants from their parent tag.

## Acknowledgements

Special thanks go to Andread Erikson for his help making the `instance` work for the self-hosted Vespa.

## Vespa Cloud gotchas

Vespa Cloud offers an integrated CI/CD pipeline.
It runs system and staging tests for you before deploying to the production environment.
All nice, however, it takes about 10-15 minutes for these tests to run because it provisions fresh infrastructure.

For system and staging tests Vespa Cloud provisions a scaled-down deployment.
But if you have more than 1 container or content cluster, then it runs 1 node per cluster.
By setting variants you can run just 1 node for everything and reduce costs. 
