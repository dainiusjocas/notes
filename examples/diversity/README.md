# Investigate diversity

```shell
docker run --rm --detach \
  --name vespa \
  --hostname vespa \
  --publish 127.0.0.1:8080:8080 \
  --publish 127.0.0.1:19071:19071 \
  --publish 127.0.0.1:19050:19050 \
  --publish 127.0.0.1:19092:19092 \
  vespaengine/vespa:8
```

```shell
vespa deploy -t local
```

```shell
echo '{"id": "id:doc:doc::1", "fields": {"myattr": "1", "mytext": "test"}}' | vespa feed -
echo '{"id": "id:doc:doc::2", "fields": {"myattr": "2", "mytext": "test"}}' | vespa feed -
echo '{"id": "id:doc:doc::3", "fields": {"myattr": "1", "mytext": "test"}}' | vespa feed -
echo '{"id": "id:doc:doc::4", "fields": {"myattr": "2", "mytext": "test"}}' | vespa feed -
echo '{"id": "id:doc:doc::5", "fields": {"myattr": "1", "mytext": "test"}}' | vespa feed -

```

```shell

```

```shell
vespa query 'select * from sources * where true'
```

```shell
# group by attribute value and get count for each group
vespa query 'select * from sources * where true limit 0 | all(group(myattr) each(output(count())))' 
```

```shell
# just the count of matches to be grouped
vespa query 'select * from sources * where true limit 0 | all( max(3) output(count()))' | jq
```

```shell
# Group just 4 best matches
vespa query 'select * from sources * where true limit 0 
| all(
    max(4) 
    all(
      group(myattr)
      each(output(count()))
    )
)' 
```

```shell
# do two groupings
vespa query 'select * from sources * where true limit 0 
| all(
    all(
      group(myattr % 100)  each(output(count()))
    )
    all(
      group(myattr)
      each(output(count()))
    )
)' 
```

```shell
# group by attribute value and get top 1 doc with summary for each group
clear
vespa query 'select * from sources * where true limit 1 
| all(
    group(myattr)
    max(1) 
    order(-max(relevance()))
    each(
      max(1)
      each(output(summary()))
    )
)' \
'ranking=dummy' | jq
```



```shell
# Use continuation
clear
vespa query 'select * from sources * where true limit 1 
| { "continuations":["","BGAAABEBCBC"] }all(
    group(myattr)
    max(1) 
    order(-max(relevance()))
    each(
      max(1)
      each(output(summary()))
    )
)' \
'ranking=dummy' | jq
```



```shell
# It is very much possible to do multiple grouping
clear
vespa query 'select * from sources * where true limit 1 
| all(
    group(myattr)
    max(1) 
    order(-max(relevance()))
    each(
      max(1)
      each(output(summary()))
    )
)
| all(group(myattr) each(output(count())))' \
'ranking=dummy' | jq
```