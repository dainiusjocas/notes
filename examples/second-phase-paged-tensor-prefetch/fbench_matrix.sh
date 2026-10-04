#!/bin/bash
# Concurrent load with vespa-fbench (8 clients, 20 s), warm and with a background evictor.
# Run as root in a privileged exec (eviction needs CAP_SYS_NICE + ptrace access to proton).
cd "$(dirname "$0")"
[ -f queries_base_100.txt ] || python3 make_fbench_queries.py
SWAP=$(ls /home/djocas/vespa/var/db/vespa/search/cluster.bench/n0/swapdirs/*/swapfile)
PID=$(pgrep -f "sbin/vespa-proton-bin ")
run() { # profile rerank mode
  local p=$1 r=$2 mode=$3 evictor=""
  cat $SWAP > /dev/null
  if [ "$mode" != warm ]; then
    ( end=$((SECONDS + 22)); while [ $SECONDS -lt $end ]; do python3 evict.py > /dev/null; sleep $mode; done ) &
    evictor=$!
  fi
  local f0=$(awk '{print $12}' /proc/$PID/stat)
  /home/djocas/vespa/bin/vespa-fbench -n 8 -c 0 -s 20 -q queries_${p}_${r}.txt localhost 8080 > fb.out 2>&1
  local f1=$(awk '{print $12}' /proc/$PID/stat)
  [ -n "$evictor" ] && wait $evictor
  grep -q "successful requests" fb.out || { cat fb.out; exit 1; }
  local n=$(awk '/successful requests/ {print $3}' fb.out)
  printf "%-8s rerank=%-4s %-11s qps %7.0f | avg %6s | p50 %6s p90 %6s p99 %6s p99.9 %6s ms | major faults/query %6.1f\n" \
    $p $r "$( [ $mode = warm ] && echo warm || echo evict/${mode}s )" \
    $(awk '/actual query rate/ {print $4}' fb.out) \
    $(awk '/average response time/ {print $4}' fb.out) \
    $(awk '/^50 +percentile/ {print $3}' fb.out) $(awk '/^90 +percentile/ {print $3}' fb.out) \
    $(awk '/^99 +percentile/ {print $3}' fb.out) $(awk '/^99.9 +percentile/ {print $3}' fb.out) \
    $(awk -v a=$f0 -v b=$f1 -v n=$n "BEGIN {print (b - a) / n}")
}
for mode in warm 0.25 2; do
  for r in 100 1000; do
    for p in base prefetch; do
      run $p $r $mode
    done
  done
done
