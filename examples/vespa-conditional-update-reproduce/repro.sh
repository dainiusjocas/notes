#!/usr/bin/env bash
#
# Repro: idempotent conditional partial updates against array<int>.
#
# Demonstrates:
#   A. correct condition        not (multichunk.processed_ids == N)   -> 200 then 412 on replay
#   B. still correct with >1 element in the array                     -> 412 on replay of every guarded value
#   C. broken variant with !=   (existential trap)                    -> replays pass once array has >1 element
#   D. broken variant with single quotes                              -> rejected, not a valid string literal
#
# Prereq: app deployed and up, e.g.
#   docker run --detach --name vespa-tas --publish 8080:8080 --publish 19071:19071 vespaengine/vespa
#   vespa deploy --wait 300 app        # or: zip + curl to :19071/application/v2/tenant/default/prepareandactivate
#
set -u
EP="${VESPA_ENDPOINT:-http://localhost:8080}"
DOC="$EP/document/v1/test/multichunk/docid/doc1"

req() { # req <label> <curl args...> ; prints label, HTTP status, body
  local label="$1"; shift
  local out status
  out=$(curl -s -w '\n%{http_code}' "$@")
  status="${out##*$'\n'}"
  echo "== $label -> HTTP $status"
  echo "${out%$'\n'*}" | head -c 800; echo; echo
}

echo "### 0. clean slate + create document"
req "DELETE doc1"  -X DELETE "$DOC"
req "PUT doc1"     -X POST -H 'Content-Type: application/json' \
  --data '{"fields": {"counter": 0}}' "$DOC"

echo "### A. correct condition, first application of value 100  (expect 200)"
req "guarded add 100 (1st)" -X PUT -H 'Content-Type: application/json' --data '{
  "condition": "not (multichunk.processed_ids == 100)",
  "fields": {
    "counter":       { "increment": 1 },
    "processed_ids": { "add": [100] }
  }
}' "$DOC"

echo "### A. exact replay of the same operation  (expect 412 TEST_AND_SET_CONDITION_FAILED)"
req "guarded add 100 (replay)" -X PUT -H 'Content-Type: application/json' --data '{
  "condition": "not (multichunk.processed_ids == 100)",
  "fields": {
    "counter":       { "increment": 1 },
    "processed_ids": { "add": [100] }
  }
}' "$DOC"

echo "### B. second distinct value 200  (expect 200; array now has 2 elements)"
req "guarded add 200 (1st)" -X PUT -H 'Content-Type: application/json' --data '{
  "condition": "not (multichunk.processed_ids == 200)",
  "fields": {
    "counter":       { "increment": 1 },
    "processed_ids": { "add": [200] }
  }
}' "$DOC"

echo "### B. replay both guarded values against the 2-element array  (expect 412 twice)"
req "guarded add 100 (replay, 2-elem array)" -X PUT -H 'Content-Type: application/json' --data '{
  "condition": "not (multichunk.processed_ids == 100)",
  "fields": {
    "counter":       { "increment": 1 },
    "processed_ids": { "add": [100] }
  }
}' "$DOC"
req "guarded add 200 (replay, 2-elem array)" -X PUT -H 'Content-Type: application/json' --data '{
  "condition": "not (multichunk.processed_ids == 200)",
  "fields": {
    "counter":       { "increment": 1 },
    "processed_ids": { "add": [200] }
  }
}' "$DOC"

echo "### C. BROKEN variant: != is existentially quantified"
echo "###    array is [100, 200]; 200 != 100 exists -> condition TRUE -> write goes through  (expect 200, counter over-incremented)"
req "!= trap: add 100 again" -X PUT -H 'Content-Type: application/json' --data '{
  "condition": "multichunk.processed_ids != 100",
  "fields": {
    "counter":       { "increment": 1 },
    "processed_ids": { "add": [100] }
  }
}' "$DOC"

echo "### D. BROKEN variant: single-quoted literal is not valid selector syntax  (expect 4xx parse error)"
req "single-quote condition" -X PUT -H 'Content-Type: application/json' --data "{
  \"condition\": \"not (multichunk.processed_ids == '100')\",
  \"fields\": {
    \"counter\":       { \"increment\": 1 },
    \"processed_ids\": { \"add\": [100] }
  }
}" "$DOC"

echo "### Final document state"
echo "###   counter should be 3 (A once, B once, C's != trap once) -- the '4th' increment proves C is unsafe."
echo "###   processed_ids should be [100, 200, 100] -- duplicate 100 from the != trap (arrays don't dedup on add)."
req "GET doc1" "$DOC"
