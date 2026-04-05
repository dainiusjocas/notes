# Inner Rank Profile Naming Conflict

When two separate rank profiles have inner rank-profiles that have the same name, the application fails to deploy.
Given that inner rank profiles have full name `containing-profile-name.inner-profile-name`, such a naming conflict seems redundant.

Why such a behavior is not a bug? Docs say:
```text
An inner rank profile, useful for grouping related profiles, especially when defined in separate .profile files. This behaves just like a top level rank profile, except that:

    The full name of the profile to use in queries will be containing-profile-name.inner-profile-name.
    The profile must explicitly inherit the containing profile.
```

The `behaves just like a top level rank profile` part says that name must be unique.
However, one can argue that the top level rank profile already ensures the uniqueness of the name.

Letting inner rank profiles to have the same name would allow for some naming conventions, e.g. `debug` or `test`. 
Such profiles could expose detailed ranking features via match-features or text tokens.
Such information could be used in system tests or debugging problems in running systems.

## Reproduction

```shell
docker run \
  --detach \
  --rm \
  --name vespa-demo \
  --publish 0.0.0.0:8080:8080 \
  --publish 0.0.0.0:19050:19050 \
  --publish 0.0.0.0:19071:19071 \
  --publish 0.0.0.0:19092:19092 \
  vespaengine/vespa:8.657.27
```

```shell
vespa deploy -t local
```
Application fails to deploy with an error message:

```text
Uploading application package... failed
Error: invalid application package (status 400)
Invalid application:
schema 'doc' error:
already has rank-profile debug
```
