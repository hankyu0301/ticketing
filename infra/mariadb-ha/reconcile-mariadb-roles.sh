#!/bin/bash
set -euo pipefail

readonly database_hosts=(
  "mariadb-primary"
  "mariadb-replica-1"
  "mariadb-replica-2"
)

readonly root_password="$MARIADB_HA_ROOT_PASSWORD"
declare -A replication_sources=()
declare -A replica_io_running=()
declare -A replica_sql_running=()
master_candidates=()

query_database() {
  local database_host="$1"
  shift

  MYSQL_PWD="$root_password" mariadb \
    --protocol=tcp \
    --connect-timeout=5 \
    --host="$database_host" \
    --user=root \
    "$@"
}

replication_source_of() {
  local database_host="$1"

  replica_status_field "$database_host" "Master_Host"
}

replica_status_field() {
  local database_host="$1"
  local field_name="$2"

  query_database "$database_host" --execute="SHOW REPLICA STATUS\\G" |
    awk -F': ' -v field_name="$field_name" \
      '$1 ~ "^[[:space:]]*" field_name "$" { print $2; exit }'
}

for database_host in "${database_hosts[@]}"; do
  replication_source="$(replication_source_of "$database_host")"
  replication_sources["$database_host"]="$replication_source"
  replica_io_running["$database_host"]="$(replica_status_field "$database_host" "Slave_IO_Running")"
  replica_sql_running["$database_host"]="$(replica_status_field "$database_host" "Slave_SQL_Running")"

  if [[ -z "$replication_source" ]]; then
    master_candidates+=("$database_host")
    echo "${database_host}: no upstream replication source; Master candidate."
  else
    echo "${database_host}: replicates from ${replication_source}."
  fi
done

if [[ "${#master_candidates[@]}" -ne 1 ]]; then
  echo "Expected exactly one Master candidate, found ${#master_candidates[@]}: ${master_candidates[*]:-none}." >&2
  echo "Role reconciliation stopped to avoid selecting a stale node or creating split brain." >&2
  exit 1
fi

readonly current_master="${master_candidates[0]}"

for database_host in "${database_hosts[@]}"; do
  if [[ "$database_host" == "$current_master" ]]; then
    query_database "$database_host" --execute="SET GLOBAL read_only = OFF;"
    continue
  fi

  if [[ "${replication_sources[$database_host]}" != "$current_master" ]]; then
    echo "${database_host} replicates from ${replication_sources[$database_host]:-none}, not ${current_master}." >&2
    echo "Role reconciliation stopped because the replication topology is not a single tree." >&2
    exit 1
  fi

  if [[ "${replica_io_running[$database_host]}" != "Yes" \
    || "${replica_sql_running[$database_host]}" != "Yes" ]]; then
    echo "${database_host}: replication is not healthy." >&2
    echo "  Slave_IO_Running=${replica_io_running[$database_host]:-none}" >&2
    echo "  Slave_SQL_Running=${replica_sql_running[$database_host]:-none}" >&2
    echo "Role reconciliation stopped because a replica is not actively applying the replication stream." >&2
    exit 1
  fi

  query_database "$database_host" --execute="SET GLOBAL read_only = ON;"
done

for database_host in "${database_hosts[@]}"; do
  read_only_value="$(
    query_database "$database_host" \
      --batch \
      --skip-column-names \
      --execute="SELECT @@GLOBAL.read_only;"
  )"

  expected_value=1
  if [[ "$database_host" == "$current_master" ]]; then
    expected_value=0
  fi

  if [[ "$read_only_value" != "$expected_value" ]]; then
    echo "${database_host}: expected read_only=${expected_value}, found ${read_only_value}." >&2
    exit 1
  fi

  echo "${database_host}: read_only=${read_only_value}."
done

echo "MariaDB roles reconciled. Current Master: ${current_master}."
