# Silent Append Failure

```shell
container run --cpus 4 --memory 4g --rm --detach \
  --name vespa \
  --publish 127.0.0.1:8080:8080 \
  --publish 127.0.0.1:19071:19071 \
  --publish 127.0.0.1:19050:19050 \
  vespaengine/vespa:8.738.17
```

```shell
vespa deploy -w 60
```

```shell
echo '
{
  "id": "id:doc:doc::1",
  "fields": {
    "chunks": [
      {"text": "zero", "sentiment": 0},
      {"text": "one", "sentiment": 10},
      {"text": "two", "sentiment": 20}
    ]
  }
}' \
| jq -c | vespa feed -
```

```shell
vespa visit --field-set="[all]" | jq
```
Returns:
```json
{
  "id": "id:doc:doc::1",
  "fields": {
    "embeddings": {
      "type": "tensor<float>(offset{},x[1])",
      "blocks": {
        "0": [
          -0.49840474128723145
        ],
        "1": [
          -0.45267194509506226
        ],
        "2": [
          -0.35940104722976685
        ]
      }
    },
    "chunks": [
      {
        "text": "zero",
        "sentiment": 0
      },
      {
        "text": "one",
        "sentiment": 10
      },
      {
        "text": "two",
        "sentiment": 20
      }
    ],
    "text": [
      "zero",
      "one",
      "two"
    ]
  }
}
```

All, got both synthetic fields `embeddings` and `text` has exactly three values.

Now, let's append one more chunk.
Use curl for full visibility
```shell
curl -v -X PUT -H "Content-Type:application/json" --data '
  {
  "create": true,
  "update": "id:doc:doc::1",
  "fields": {
    "chunks": {
      "add": [
        {"text": "three", "sentiment": 20}
      ]
    }
  }
}' \
  http://localhost:8080/document/v1/doc/doc/docid/1
```
which responds with:
```text
* Host localhost:8080 was resolved.
* IPv6: ::1
* IPv4: 127.0.0.1
*   Trying [::1]:8080...
* connect to ::1 port 8080 from ::1 port 51552 failed: Connection refused
*   Trying 127.0.0.1:8080...
* Connected to localhost (127.0.0.1) port 8080
> PUT /document/v1/doc/doc/docid/1 HTTP/1.1
> Host: localhost:8080
> User-Agent: curl/8.7.1
> Accept: */*
> Content-Type:application/json
> Content-Length: 159
> 
* upload completely sent off: 159 bytes
< HTTP/1.1 200 OK
< Date: Wed, 19 Aug 2026 19:21:17 GMT
< Vary: Accept-Encoding
< Content-Type: application/json; charset=UTF-8
< Transfer-Encoding: chunked
< 
* Connection #0 to host localhost left intact
{"pathId":"/document/v1/doc/doc/docid/1","id":"id:doc:doc::1"}% 
```
Just 200, nothing in headers.

And visit once again:
```shell
vespa visit --field-set="[all]" | jq
```

```json
{
  "id": "id:doc:doc::1",
  "fields": {
    "embeddings": {
      "type": "tensor<float>(offset{},x[1])",
      "blocks": {
        "0": [
          -0.49840474128723145
        ],
        "1": [
          -0.45267194509506226
        ],
        "2": [
          -0.35940104722976685
        ]
      }
    },
    "chunks": [
      {
        "text": "zero",
        "sentiment": 0
      },
      {
        "text": "one",
        "sentiment": 10
      },
      {
        "text": "two",
        "sentiment": 20
      },
      {
        "text": "three",
        "sentiment": 20
      }
    ],
    "text": [
      "zero",
      "one",
      "two",
      "three"
    ]
  }
}
```

See that `text` field has four values.
While `embeddings` tensor has only 3.


## Helpers

Delete all docs:
```shell
vespa visit --field-set="[id]" | jq -c '{"remove": .id}' | vespa feed -
```
