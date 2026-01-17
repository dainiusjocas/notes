# Vespa DevEx: briging the gap between laptop and production

## TL;DR

Leveraging Vespa's `instance` can help the application's journey from a laptop to production.

## For the impatient

In the `services.xml` you can have variants like:
```shell

```

This setup when deployed to a local single node Vespa system with this command:
```shell

```
Properly creates a Vespa application as if only the `deploy:instance="local"` variant was specified.

And when deployed to the multi-node self-hosted Vespa with
```shell

```
deploys the application as if only the `deploy:insatnce=prod` variant was specified.

## Abstract

This post aims to show how to increase the velocity and confidence of the Vespa application development.
Let's see how we can leverage `services.xml` variants and the `instance` part of the application ID.
Also, describes how to address development against Vespa Cloud or self-hosted Vespa.


## DevEx

### Baselines

Once the Docker image is downloaded, on a laptop it takes ~30 seconds for Vespa to be ready to deploy an application and then for the first deployment an additional ~20 seconds for the endpoints to become available for feeding and querying.
Then the following deployments take ~3 seconds to be applied.

This means that we can run an integration tests step in the CI / CD pipeline against a live Vespa application in ~1-2 minutes.
That is an acceptable time to prevent a context change and not to lose the flow.

For a Vespa Cloud dev instance to become ready, it takes ~10 minutes.

Vespa Cloud offers an integrated CI/CD pipeline.
It runs system and staging tests for you before deploying to the production environment.
All nice, however, it takes about 10-15 minutes for these tests to run because it provisions fresh infrastructure.

## Self-hosted Vespa

The biggest difference between local and production deployments is the number of nodes.
This can be solved elegantly by combining variants with pre-processing.

To have even fewer files in the repository, we can put all hosts into a single `hosts.xml` file.

## Mixed Vespa Cloud and self-hosted deployments

We can leverate the fact that when deploying to Vespa Cloud the `hosts.xml` file and configs about `nodes` are ignored.
Conversely, self-hosted Vespa ignores `deployment.xml` and the `nodes` config specific for the Vespa Cloud.
E.g. we can take an application deployed from Vespa Cloud and deploy it to a self-hosted single node Vespa instance without any changes, and most likely it is going to work.

### Tricks to speed up CD/CD

Instead of using Vanilla vespa docker we could try leveraging docker image that already has some previous application versions deployed. This shaves about ~20 seconds off the time required to run the tests.

There are a couple of complications:
1. Where to store the image?
2. And what if the current application version requires an entry in the `validation-overrides.xml`? Fail

(1) is a regular DevOps question. I'll leave it to the reader to figure out how to solve it.

Failing to specify (2) means yet another dance of `observe failure -> fix -> commit -> push -> wait`: a waste of time.
As far as I know, there is no way to start Vespa that would allow disabling all validations.
To address it, we can leverage a neat trick: provide a `validation-overrides.xml` file with all breaking changes allowed for today.
This requires generating the file with today's date and a list of all possible `allow` entries.
TODO: shell command to create such a file.

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
