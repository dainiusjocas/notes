#!/bin/bash
# Run as root in a privileged exec (eviction needs CAP_SYS_NICE + ptrace access to proton).
set -e
cd "$(dirname "$0")"
SWAP=$(ls /home/djocas/vespa/var/db/vespa/search/cluster.bench/n0/swapdirs/*/swapfile)
for round in 1 2; do
  echo "=== round $round: sequential, cold (evict before every query)"
  for rerank in 100 1000; do
    for profile in base prefetch; do
      python3 bench.py --profile $profile --rerank $rerank --queries 200 --cold --seed $round
    done
  done
  echo "=== round $round: sequential, warm (swapfile in page cache)"
  cat $SWAP > /dev/null
  for rerank in 100 1000; do
    for profile in base prefetch; do
      python3 bench.py --profile $profile --rerank $rerank --queries 500 --seed $round
    done
  done
  echo "=== round $round: concurrent, 8 clients, 30 s"
  for rerank in 100 1000; do
    for profile in base prefetch; do
      cat $SWAP > /dev/null
      python3 bench.py --profile $profile --rerank $rerank --clients 8 --duration 30 --seed $round
      python3 bench.py --profile $profile --rerank $rerank --clients 8 --duration 30 --evict-interval 0.25 --seed $round
    done
  done
done
