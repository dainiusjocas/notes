#!/usr/bin/env python3
# Exact warm-cache cost of the prefetch call, from the per-attribute trace event emitted by proton.
import json, random, re, statistics, sys
import bench
rerank, n = int(sys.argv[1]), int(sys.argv[2])
rnd = random.Random(7)
q = [round(rnd.uniform(-1, 1), 3) for _ in range(768)]
class A: profile = "prefetch"
A.rerank = rerank
conn = bench.http.client.HTTPConnection("localhost", 8080, timeout=60)
times, ranges = [], []
for _ in range(n):
    data = bench.query(conn, bench.make_body(A, rnd, q, trace=True))
    m = re.search(r"Prefetched attribute 'emb' for (\d+) hits: (\d+) bytes in (\d+) ranges, ([0-9.]+) ms", json.dumps(data))
    times.append(float(m.group(4)) * 1000)
    ranges.append(int(m.group(3)))
print(f"rerank={rerank}: prefetch call p50 {bench.pct(times, .5):.0f} us, p90 {bench.pct(times, .9):.0f} us, "
      f"p99 {bench.pct(times, .99):.0f} us, ranges/query {statistics.mean(ranges):.0f}")
