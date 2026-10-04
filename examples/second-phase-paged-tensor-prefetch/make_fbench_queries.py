#!/usr/bin/env python3
# Writes fbench GET query files (one URL per line) per profile/rerank combination.
import random, urllib.parse
rnd = random.Random(1)
q = "[" + ",".join(str(round(rnd.uniform(-1, 1), 3)) for _ in range(768)) + "]"
buckets = [rnd.randrange(100) for _ in range(2000)]
for profile in ("base", "prefetch"):
    for rerank in (100, 1000):
        with open(f"queries_{profile}_{rerank}.txt", "w") as f:
            for b in buckets:
                params = {"yql": f"select id from doc where bucket = {b}", "ranking.profile": profile,
                          "ranking.rerankCount": rerank, "input.query(q)": q, "hits": 10,
                          "presentation.summary": "minimal", "timeout": "20s"}
                f.write("/search/?" + urllib.parse.urlencode(params) + "\n")
