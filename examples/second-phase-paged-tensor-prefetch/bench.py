#!/usr/bin/env python3
# Sequential query benchmark against a local Vespa: per query optionally evict paged-attribute pages first
# (cold), then run one query with a random bucket filter and measure latency + proton major faults.
import argparse, http.client, json, random, statistics, sys, threading, time
import evict as ev

def majflt(pid):
    with open(f"/proc/{pid}/stat") as f:
        return int(f.read().rsplit(")", 1)[1].split()[9])  # field 12 overall

def minflt(pid):
    with open(f"/proc/{pid}/stat") as f:
        return int(f.read().rsplit(")", 1)[1].split()[7])  # field 10 overall

def query(conn, body):
    conn.request("POST", "/search/", body=json.dumps(body), headers={"Content-Type": "application/json"})
    resp = conn.getresponse()
    data = json.loads(resp.read())
    if resp.status != 200 or "errors" in data.get("root", {}):
        raise RuntimeError(f"query failed: {resp.status} {json.dumps(data)[:500]}")
    return data

def pct(values, p):
    s = sorted(values)
    return s[min(len(s) - 1, int(p * len(s)))]

def make_body(a, rnd, q, trace=False):
    body = {"yql": f"select id from doc where bucket = {rnd.randrange(100)}",
            "ranking": {"profile": a.profile, "rerankCount": a.rerank},
            "input.query(q)": q, "hits": 10, "presentation.summary": "minimal",
            "presentation.timing": True, "timeout": "20s"}
    if trace:
        body["trace.level"] = 4
    return body

def report(a, mode, lat, search, faults_per_query, extra=""):
    print(f"{a.profile:9s} rerank={a.rerank:5d} {mode:13s} n={len(lat):5d}: "
          f"latency p50 {pct(lat, .5):7.2f} p90 {pct(lat, .9):7.2f} p99 {pct(lat, .99):7.2f} ms | "
          f"searchtime p50 {pct(search, .5):6.1f} p99 {pct(search, .99):6.1f} ms | "
          f"major faults/query {faults_per_query:7.1f}{extra}", flush=True)

def sequential(a, rnd, q, pid):
    conn = http.client.HTTPConnection("localhost", 8080, timeout=60)
    for _ in range(a.warmup):
        query(conn, make_body(a, rnd, q))
    lat, search, faults = [], [], []
    for i in range(a.queries):
        if a.cold:
            ev.evict(pid)
        trace = a.trace and i == 0
        body = make_body(a, rnd, q, trace)
        f0 = majflt(pid)
        t0 = time.perf_counter()
        data = query(conn, body)
        lat.append((time.perf_counter() - t0) * 1000)
        faults.append(majflt(pid) - f0)
        search.append(data["timing"]["searchtime"] * 1000)
        if trace:
            for part in json.dumps(data.get("trace", {})).split('"'):
                if "Prefetch" in part:
                    print("  trace:", part)
    report(a, "cold" if a.cold else "warm", lat, search, statistics.mean(faults))

def concurrent(a, q, pid):
    lat, search = [], []
    lock = threading.Lock()
    stop = time.perf_counter() + a.warmup_seconds + a.duration
    measure_from = time.perf_counter() + a.warmup_seconds
    evictions = [0]

    def client(seed):
        rnd = random.Random(seed)
        conn = http.client.HTTPConnection("localhost", 8080, timeout=60)
        while time.perf_counter() < stop:
            t0 = time.perf_counter()
            data = query(conn, make_body(a, rnd, q))
            if t0 >= measure_from:
                with lock:
                    lat.append((time.perf_counter() - t0) * 1000)
                    search.append(data["timing"]["searchtime"] * 1000)

    def evictor():
        while time.perf_counter() < stop:
            ev.evict(pid)
            evictions[0] += 1
            time.sleep(a.evict_interval)

    threads = [threading.Thread(target=client, args=(a.seed * 1000 + i,)) for i in range(a.clients)]
    if a.evict_interval > 0:
        threads.append(threading.Thread(target=evictor))
    for t in threads:
        t.start()
    time.sleep(a.warmup_seconds)
    f0, m0 = majflt(pid), minflt(pid)
    for t in threads:
        t.join()
    faults = majflt(pid) - f0
    minor = (minflt(pid) - m0) / max(1, len(lat))
    mode = f"c={a.clients}" + (f" evict/{a.evict_interval}s" if a.evict_interval > 0 else " warm")
    report(a, mode, lat, search, faults / max(1, len(lat)),
           f" | minor faults/query {minor:7.1f} | {len(lat) / a.duration:6.1f} qps")

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--profile", required=True)
    ap.add_argument("--rerank", type=int, default=100)
    ap.add_argument("--queries", type=int, default=100)
    ap.add_argument("--warmup", type=int, default=20, help="sequential: unmeasured queries first")
    ap.add_argument("--cold", action="store_true", help="sequential: evict before every query")
    ap.add_argument("--clients", type=int, default=0, help="concurrent mode with this many clients")
    ap.add_argument("--duration", type=float, default=30)
    ap.add_argument("--warmup-seconds", type=float, default=5)
    ap.add_argument("--evict-interval", type=float, default=0, help="concurrent: evict every N seconds")
    ap.add_argument("--seed", type=int, default=1)
    ap.add_argument("--trace", action="store_true", help="print the prefetch trace events of the first query")
    a = ap.parse_args()
    rnd = random.Random(a.seed)
    q = [round(rnd.uniform(-1, 1), 3) for _ in range(768)]
    pid = ev.proton_pid()
    if a.clients > 0:
        concurrent(a, q, pid)
    else:
        sequential(a, rnd, q, pid)

if __name__ == "__main__":
    main()
