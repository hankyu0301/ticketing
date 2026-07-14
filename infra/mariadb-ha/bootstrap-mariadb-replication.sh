#!/bin/bash
set -euo pipefail

readonly database_hosts=(
  "mariadb-primary"
  "mariadb-replica-1"
  "mariadb-replica-2"
)

readonly initial_master="mariadb-primary"
readonly root_password="$MARIADB_HA_ROOT_PASSWORD"
readonly replication_user="$MARIADB_REPLICATION_USER"
readonly replication_password="$MARIADB_REPLICATION_PASSWORD"
readonly replication_password_sql="$(printf '%s' "$replication_password" | sed "s/\\\\/\\\\\\\\/g; s/'/''/g")"

declare -A replication_sources=()
declare -A replica_io_running=()
declare -A replica_sql_running=()
master_candidates=()

validate_user_name() {
  local user_name="$1"

  if [[ ! "$user_name" =~ ^[A-Za-z0-9_]+$ ]]; then
    echo "Replication user name may contain only letters, numbers, and underscores." >&2
    exit 1
  fi
}

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

replica_status_field() {
  local database_host="$1"
  local field_name="$2"

  query_database "$database_host" --execute="SHOW REPLICA STATUS\\G" |
    awk -F': ' -v field_name="$field_name" \
      '$1 ~ "^[[:space:]]*" field_name "$" { print $2; exit }'
}

load_replication_status() {
  local database_host="$1"

  replication_sources["$database_host"]="$(replica_status_field "$database_host" "Master_Host")"
  replica_io_running["$database_host"]="$(replica_status_field "$database_host" "Slave_IO_Running")"
  replica_sql_running["$database_host"]="$(replica_status_field "$database_host" "Slave_SQL_Running")"
}

create_replication_user() {
  local database_host="$1"

  echo "${database_host}: ensuring replication user exists."

  query_database "$database_host" <<SQL
SET SESSION sql_log_bin = 0;

CREATE USER IF NOT EXISTS '${replication_user}'@'%' IDENTIFIED BY '${replication_password_sql}';
ALTER USER '${replication_user}'@'%' IDENTIFIED BY '${replication_password_sql}';
GRANT REPLICATION SLAVE, REPLICATION CLIENT ON *.* TO '${replication_user}'@'%';

FLUSH PRIVILEGES;
SQL
}

wait_until_replication_is_running() {
  local replica_host="$1"
  local expected_master="$2"

  for _ in {1..30}; do
    load_replication_status "$replica_host"

    if [[ "${replication_sources[$replica_host]}" == "$expected_master" \
      && "${replica_io_running[$replica_host]}" == "Yes" \
      && "${replica_sql_running[$replica_host]}" == "Yes" ]]; then
      echo "${replica_host}: replication is running from ${expected_master}."
      return 0
    fi

    sleep 2
  done

  echo "${replica_host}: replication did not become healthy." >&2
  echo "  Master_Host=${replication_sources[$replica_host]:-none}" >&2
  echo "  Slave_IO_Running=${replica_io_running[$replica_host]:-none}" >&2
  echo "  Slave_SQL_Running=${replica_sql_running[$replica_host]:-none}" >&2
  query_database "$replica_host" --execute="SHOW REPLICA STATUS\\G" >&2 || true
  exit 1
}

configure_replica_from_master() {
  local replica_host="$1"
  local master_host="$2"

  echo "${replica_host}: configuring replication from ${master_host}."
  query_database "$replica_host" --execute="STOP SLAVE;" || true

  query_database "$replica_host" <<SQL
SET GLOBAL read_only = OFF;
CHANGE MASTER TO
  MASTER_HOST='${master_host}',
  MASTER_USER='${replication_user}',
  MASTER_PASSWORD='${replication_password_sql}',
  MASTER_USE_GTID=slave_pos;
START SLAVE;
SET GLOBAL read_only = ON;
SQL

  wait_until_replication_is_running "$replica_host" "$master_host"
}

ensure_existing_replication_is_running() {
  local replica_host="$1"
  local expected_master="$2"

  if [[ "${replica_io_running[$replica_host]}" == "Yes" \
    && "${replica_sql_running[$replica_host]}" == "Yes" ]]; then
    echo "${replica_host}: existing replication from ${expected_master} is already healthy."
    return 0
  fi

  echo "${replica_host}: existing replication from ${expected_master} is stopped; starting it."
  query_database "$replica_host" --execute="START SLAVE;"
  wait_until_replication_is_running "$replica_host" "$expected_master"
}

apply_read_only_roles() {
  local selected_master="$1"

  for database_host in "${database_hosts[@]}"; do
    if [[ "$database_host" == "$selected_master" ]]; then
      query_database "$database_host" --execute="SET GLOBAL read_only = OFF;"
      echo "${database_host}: read_only=OFF."
    else
      query_database "$database_host" --execute="SET GLOBAL read_only = ON;"
      echo "${database_host}: read_only=ON."
    fi
  done
}

validate_user_name "$replication_user"

for database_host in "${database_hosts[@]}"; do
  create_replication_user "$database_host"
done

for database_host in "${database_hosts[@]}"; do
  load_replication_status "$database_host"

  if [[ -z "${replication_sources[$database_host]}" ]]; then
    master_candidates+=("$database_host")
    echo "${database_host}: no upstream replication source; Master candidate."
  else
    echo "${database_host}: replicates from ${replication_sources[$database_host]}."
  fi
done

if [[ "${#master_candidates[@]}" -eq "${#database_hosts[@]}" ]]; then
  echo "No replication topology exists. Bootstrapping initial topology with ${initial_master} as Master."

  apply_read_only_roles "$initial_master"

  for database_host in "${database_hosts[@]}"; do
    if [[ "$database_host" != "$initial_master" ]]; then
      configure_replica_from_master "$database_host" "$initial_master"
    fi
  done

  echo "MariaDB replication bootstrapped. Current Master: ${initial_master}."
  exit 0
fi

if [[ "${#master_candidates[@]}" -ne 1 ]]; then
  echo "Expected either a fresh topology or exactly one Master candidate, found ${#master_candidates[@]}: ${master_candidates[*]:-none}." >&2
  echo "Replication bootstrap stopped to avoid selecting a stale node or creating split brain." >&2
  exit 1
fi

readonly current_master="${master_candidates[0]}"
echo "Existing replication topology detected. Current Master: ${current_master}."

for database_host in "${database_hosts[@]}"; do
  if [[ "$database_host" == "$current_master" ]]; then
    continue
  fi

  if [[ "${replication_sources[$database_host]}" != "$current_master" ]]; then
    echo "${database_host} replicates from ${replication_sources[$database_host]:-none}, not ${current_master}." >&2
    echo "Replication bootstrap stopped because the replication topology is not a single tree." >&2
    exit 1
  fi

  ensure_existing_replication_is_running "$database_host" "$current_master"
done

apply_read_only_roles "$current_master"
echo "MariaDB replication topology is already valid. Current Master: ${current_master}."
