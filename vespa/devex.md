# Vespa DevEx: bridging the gap between the laptop and production   

## TL;DR

Leveraging Vespa `services.xml` [variants](https://docs.vespa.ai/en/operations/deployment-variants.html#services.xml-variants) with the [instance](https://docs.vespa.ai/en/learn/glossary.html#instance) is a neat way to improve [Developer Experience](https://en.wikipedia.org/wiki/Developer_experience).

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

And when deployed as the `dev` self-hosted Vespa instance with:
```shell
curl -X POST -s \
  --header "Content-Type:application/zip" \
  --data-binary @application.zip \
  "http://localhost:19071/application/v2/tenant/default/prepareandactivate?instance=dev"
```
deploys the application as if only the `deploy:instance=dev` variant was in the `services.xml`.

For a full example see [here](https://github.com/dainiusjocas/notes/tree/main/examples/services-xml-tricks/single-node/).

## Intro

This post presents an option how to increase both the velocity and confidence of Vespa application development.
The combination of `services.xml` variants and the `instance` part of the [application ID](https://github.com/vespa-engine/vespa/blob/cdcf0c2eddde976dae18f09034664f460984814f/config-provisioning/src/main/java/com/yahoo/config/provision/ApplicationId.java#L17) allows conditional config overrides based on the deployment target all within a single `services.xml` file.
Also, describes how to address development against Vespa Cloud or self-hosted Vespa. 

## Developer experience (DevEx)

I've seen many different ways how teams attempt to manage Vespa applications: ranging from different applications with copy-pasted files, targeting only the Vespa Cloud, deploy application from a volume mounted to the Vespa docker container, all the way to scripts that stitch files correctly based on the provided deployment target. 
All of them introduce some compromises: either duplicating config, brittle scripts, a shared deployment that all tests run against...
The underlying reason is that Vespa doesn't really provide a consistent DevEx story.

Ideally, we'd like to have a single source of truth and have overrides only where they are needed.
Also, how to set up the CI/CD to run integration tests?
Let's see how far we can go with the current Vespa capabilities.

To the Vespa credit, there is a huge repository of [examples](https://github.com/vespa-engine/sample-apps).
There are plenty of working applications. But they either target Vespa Cloud or are self-hosted single node deployments.
However, simply studying the examples doesn't lead to generalized knowledge of how to manage Vespa applications.
You have to discover for yourself, e.g. how to manage an application written in PyVespa that contains custom [Java components](https://github.com/vespa-engine/pyvespa/issues/1184).

### Some Baselines

Once the Vespa docker image is downloaded:
- on a laptop it takes ~30 seconds for Vespa to be ready to deploy an application;
- then for the first deployment to converge an additional ~20 seconds until the endpoints become available for feeding and querying.
- then the following deployments take ~3 seconds to be applied.
- if you also build maven artifacts, add a some seconds to the mix.
- Running a [System Test](https://docs.vespa.ai/en/applications/testing.html) itself takes ~2 seconds.

This means that we could run an integration tests step in the CI / CD pipeline against a live Vespa application in ~1-2 minutes.
That is an acceptable time to prevent a context change and not to lose the flow.
Running parallel CI/CD jobs to test several different targets/configurations is OK.

For a Vespa Cloud `dev` environment instance to become ready, it takes ~10 minutes.
This is a bit too long for a CI/CD pipeline intended to run on each push to the repository.

So, for quick prototyping and CI/CD self-hosted Vespa beats Vespa Cloud dev environment.

## Self-hosted Vespa

The most significant difference between development on a laptop and production deployments is the number of nodes.
On top of that, typically we want them to meaningfully differ **ONLY** in the number of nodes.

This can be elegantly solved by combining variants on instance with pre-processing.
The rule of thumb:
- the `default` instance is for the laptop and CI/CD;
- a named instance variant for dev/staging/production/etc. environments.

A proper example is [here](https://github.com/dainiusjocas/notes/tree/main/examples/services-xml-tricks/multi-host/)

Currently, `vespa deploy` for self-hosted Vespa doesn't support [instance](https://github.com/vespa-engine/vespa/issues/35630) parameter.
But the underlying Vespa configserver [endpoint](https://github.com/vespa-engine/vespa/blob/f96ac8ce9fdd48606707293bfa042c313dfbbf65/client/go/internal/vespa/deploy.go) supports it.
Therefore, for now we should use `curl` to deploy the application to a self-hosted Vespa.

Furthermore, to have even fewer files in the repository, we can put all hosts into a single `hosts.xml` file.
Also, `hosts.xml` support [variants](https://github.com/vespa-engine/vespa/blob/b051f52487b6b7fc6ba16ecc6b769d3efaa57385/config-application-package/src/main/java/com/yahoo/config/model/application/provider/ApplicationPackagePreprocessor.java#L89-L90).

## Mixed Vespa Cloud and self-hosted deployments

We can leverate the fact that when deploying to Vespa Cloud the `hosts.xml` file and configs about specific `nodes` are ignored.
Conversely, self-hosted Vespa ignores the `deployment.xml` and the `nodes` config specific for the Vespa Cloud: it is treated as a single node deployment.
E.g., we can take an application deployed from Vespa Cloud and deploy it to a self-hosted single node Vespa instance without any changes, and most likely it is going to work.

Of course, more complicated setups like multiple content clusters wouldn't work.

## Vespa Cloud

Vespa Cloud supports [deployment variants](https://docs.vespa.ai/en/operations/deployment-variants.html#deployment-variants).
Vespa CLI also suports passing `instance` and other parameters to the `deploy` command.
Also, typically a Vespa Cloud app can be deployed to a self-hosted Vespa single node instance (e.g. in CI/CD) without any changes.
Nothing to invent here.

Several problems remain in the CI/CD story:
- if dev instances are ephemeral, they take ~10 minutes to provision.
- if we use warm Vespa Cloud dev instances, they are shared between branches and pipelines must be coordinated not to interfere with each other.

## Vespa Cloud gotchas

Vespa Cloud offers an integrated CI/CD pipeline.
It runs system and staging tests for you before deploying to the production environment.
All nice, however, it takes about 10-15 minutes for these tests to run because it provisions fresh infrastructure.

For system and staging tests Vespa Cloud provisions a scaled-down deployment.
But if you have more than 1 container or content cluster, then it runs 1 node per cluster.
By setting variants you can run just 1 node for everything and reduce costs.

## Tricks to speed up CI/CD cycles

Instead of using a vanilla Vespa Docker image, we can create a Docker image that already has some previous application versions deployed.
This shaves about ~20 seconds off the time required to run the tests.

There are a couple of complications:
1. Where to store the image?
2. What if to deploy the current application version requires an entry in the `validation-overrides.xml`?

(1) is a regular [DevOps](https://en.wikipedia.org/wiki/DevOps) question. 
Let's leave it to the reader to figure out how to solve it.

Failing to specify (2) means yet another dance of `observe failure -> fix -> commit -> push -> wait`: a waste of time.
As far as I know, there is no way to start Vespa that would allow disabling all validations.
To address this limitation, we can leverage a neat trick: provide a `validation-overrides.xml` file with all breaking changes allowed for today.
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

Hide the parameters under a [query profile](https://docs.vespa.ai/en/applications/testing.html#feature-switches-and-bucket-tests).
And query profilea support [variants](https://docs.vespa.ai/en/operations/deployment-variants.html#query-profile-variants) on the `instance` variable.
This way a query profile can have e.g., one timeout value in staging and another in `prod` without specifying anything on the application side.

## Yes, but how about variations on the feeding logic?

If variations are only about which custom [document processors](https://docs.vespa.ai/en/applications/document-processors.html#) should be run, then regular tricks work.
There is no way to declaratively express that feed requests agains `doc1` should be routed to `doc2` instance.
However, a custom document processor can be used to achieve this.
Then again introduce a variant on the `instance` variable in the `chain` config in `services.xml`.

## Yes, but why not use `environment` and other variables in variants?

The docs state that variants can be on `tags, instances, environments, clouds and regions`.
However:
- `tags` are Vespa Cloud specific, as they are taken only from `deployment.xml` files.
- `environments` is an [enum](https://github.com/vespa-engine/vespa/blob/3145c832b8bcc3e1870b0a207f20aaac7df5b1d8/config-provisioning/src/main/java/com/yahoo/config/provision/Environment.java#L11) with has only 5 values.
- `clouds` and `regions` are more suitable for Vespa Cloud.

This leaves `instances` as they are just some string identifiers and works with both Vespa Cloud and self-hosted deployments.

## A note on the system tests

If you have custom Vespa components, then there is really no reason not to use `@SystemTest` in your CI/CD.
Unless, of course, you can't stand any language that is hosted on JVM, e.g. Java, Kotlin, Clojure, maybe even GraalVM could be leveraged to write such tests in Python or JavaScript. 

## Yes, but how variants seem complicated, any gotchas?

Tradeoffs, yes.

When you have a running Vespa with an instance `A`, and you deploy an instance `B`, then Vespa rejects deployment with an error:
```shell
{"error-code":"INVALID_APPLICATION_PACKAGE","message":"Invalid application: 'default.default.B' tried to allocate 'localhost', but the host is already allocated to another application"}
```

Another not-so-obvious thing is that in case you want to have a variation in the list, my advice would be to have a variation on the list component instead of individual elements.

E.g., prefer this to:
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

And, implicitly, when a tag in the list has any variant specified, others have implicit `default`.
Which can produce unexpected results.

Multiple instances can be specified per tag.

## Yes, but how to check what is actually deployed?

No good answer here for now.
Vespa Maven plugin can do that, but it first calls Vespa Cloud and only then writes the resolved `services.xml` file to the disk.
```shell
mvn vespa:effectiveServices
```

## Acknowledgements

Special thanks go to Andread Erikson for his help making the `instance` work for the self-hosted Vespa.
