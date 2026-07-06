#!/bin/bash
set -euo pipefail

server_rows="$(maxctrl list servers --tsv 2>/dev/null)"

master_count="$(
  awk -F '\t' '$5 ~ /Master/ && $5 ~ /Running/ { count++ } END { print count + 0 }' \
    <<< "$server_rows"
)"
slave_count="$(
  awk -F '\t' '$5 ~ /Slave/ && $5 ~ /Running/ { count++ } END { print count + 0 }' \
    <<< "$server_rows"
)"

if [[ "$master_count" -ne 1 ]]; then
  echo "Expected exactly one running Master, found ${master_count}." >&2
  exit 1
fi

if [[ "$slave_count" -lt 1 ]]; then
  echo "Expected at least one running Slave, found ${slave_count}." >&2
  exit 1
fi

echo "MaxScale topology is ready: ${master_count} Master, ${slave_count} Slave(s)."
