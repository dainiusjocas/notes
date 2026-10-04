#!/usr/bin/env python3
# Writes JSONL puts to stdout: id, random bucket 0..99, random score, 768-dim float embedding.
# Embeddings are drawn from a pool of precomputed random vectors (values don't matter for I/O).
import json, random, sys
n = int(sys.argv[1]) if len(sys.argv) > 1 else 500000
rnd = random.Random(42)
pool = [json.dumps([round(rnd.uniform(-1, 1), 3) for _ in range(768)]) for _ in range(1000)]
out = sys.stdout
for i in range(n):
    out.write('{"put":"id:bench:doc::%d","fields":{"id":%d,"bucket":%d,"score":%.5f,"emb":{"values":%s}}}\n'
              % (i, i, rnd.randrange(100), rnd.random(), pool[rnd.randrange(len(pool))]))
