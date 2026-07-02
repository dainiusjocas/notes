# Vespa test-and-set idempotency repro — array<int> + condition

Minimal application package plus a curl script that proves the
self-invalidating-condition pattern and demonstrates the two failure
modes discussed (`!=` existential trap, single-quoted string literal).

## Layout

    app/
      services.xml            single-node container + content cluster
      schemas/multichunk.sd   counter (int) + processed_ids (array<int>)
    repro.sh                  curl sequence with expected statuses

## Run

    docker run --detach --name vespa-tas \
      --publish 8080:8080 --publish 19071:19071 vespaengine/vespa

    vespa config set target local
    vespa deploy --wait 300 app

    ./repro.sh
    # or against another endpoint:
    VESPA_ENDPOINT=https://... ./repro.sh

Without Vespa CLI, deploy with:

    (cd app && zip -r - .) > app.zip
    curl -X POST --data-binary @app.zip -H 'Content-Type: application/zip' \
      http://localhost:19071/application/v2/tenant/default/prepareandactivate

## Expected results

| Step | Condition                                     | Array before | HTTP | Applied? |
|------|-----------------------------------------------|--------------|------|----------|
| A1   | `not (multichunk.processed_ids == 100)`       | []           | 200  | yes      |
| A2   | same, exact replay                            | [100]        | 412  | no       |
| B1   | `not (multichunk.processed_ids == 200)`       | [100]        | 200  | yes      |
| B2   | replay of A                                   | [100,200]    | 412  | no       |
| B3   | replay of B                                   | [100,200]    | 412  | no       |
| C    | `multichunk.processed_ids != 100`             | [100,200]    | 200  | yes (BUG in condition, not in Vespa) |
| D    | `not (multichunk.processed_ids == '100')`     | [100,200,100]| 4xx  | rejected |

Final state: `counter == 3`, `processed_ids == [100, 200, 100]`.

Step C is the "works only when the array contains 1 value" symptom:
`!=` over a multivalue field means "some element differs", which is
false only while the array's sole content equals the tested value.
Step B2/B3 returning 412 with a 2-element array shows the `not (==)`
form keeps its containment semantics at any cardinality.

Notes:
- 412 carries `TEST_AND_SET_CONDITION_FAILED` in the message body;
  treat it as "already applied" in feed logic, never retry it.
- The `add` on array<int> does NOT deduplicate (see duplicate 100 after
  step C); a correct condition prevents duplicates, but if you want
  belt-and-suspenders storage-level dedup, use weightedset<int>.
- Conditions must prefix fields with the document type name
  (`multichunk.processed_ids`), and string literals, if you ever use a
  string-typed marker field, must be double-quoted (`\"` inside JSON).
