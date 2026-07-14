#!/bin/bash
set -euo pipefail

readonly database_hosts=(
  "mariadb-primary"
  "mariadb-replica-1"
  "mariadb-replica-2"
)

validate_user_name() {
  local user_name="$1"

  if [[ ! "$user_name" =~ ^[A-Za-z0-9_]+$ ]]; then
    echo "MaxScale user names may contain only letters, numbers, and underscores." >&2
    exit 1
  fi
}

escape_sql_literal() {
  local value="$1"
  value="${value//\\/\\\\}"
  value="${value//\'/\'\'}"
  printf '%s' "$value"
}

validate_user_name "$MAXSCALE_MONITOR_USER"
validate_user_name "$MAXSCALE_SERVICE_USER"

readonly root_password="$MARIADB_HA_ROOT_PASSWORD"
readonly monitor_user="$MAXSCALE_MONITOR_USER"
readonly monitor_password="$(escape_sql_literal "$MAXSCALE_MONITOR_PASSWORD")"
readonly service_user="$MAXSCALE_SERVICE_USER"
readonly service_password="$(escape_sql_literal "$MAXSCALE_SERVICE_PASSWORD")"

for database_host in "${database_hosts[@]}"; do
  echo "Bootstrapping MaxScale users on ${database_host}."

  MYSQL_PWD="$root_password" mariadb \
    --protocol=tcp \
    --host="$database_host" \
    --user=root <<SQL
SET SESSION sql_log_bin = 0;

CREATE USER IF NOT EXISTS '${monitor_user}'@'%' IDENTIFIED BY '${monitor_password}';
ALTER USER '${monitor_user}'@'%' IDENTIFIED BY '${monitor_password}';

CREATE USER IF NOT EXISTS '${service_user}'@'%' IDENTIFIED BY '${service_password}';
ALTER USER '${service_user}'@'%' IDENTIFIED BY '${service_password}';
SQL

  if MYSQL_PWD="$root_password" mariadb \
    --protocol=tcp \
    --host="$database_host" \
    --user=root \
    --batch \
    --skip-column-names \
    --execute="SHOW GRANTS FOR '${monitor_user}'@'%';" |
    grep -q "REPLICATION SLAVE"; then
    MYSQL_PWD="$root_password" mariadb \
      --protocol=tcp \
      --host="$database_host" \
      --user=root <<SQL
SET SESSION sql_log_bin = 0;

REVOKE REPLICATION SLAVE ON *.* FROM '${monitor_user}'@'%';
SQL
  fi

  MYSQL_PWD="$root_password" mariadb \
    --protocol=tcp \
    --host="$database_host" \
    --user=root <<SQL
SET SESSION sql_log_bin = 0;

GRANT SUPER, RELOAD, PROCESS, SHOW DATABASES, EVENT, REPLICA MONITOR, REPLICATION CLIENT, READ_ONLY ADMIN
  ON *.* TO '${monitor_user}'@'%';
GRANT SELECT ON mysql.user TO '${monitor_user}'@'%';
GRANT SELECT ON mysql.global_priv TO '${monitor_user}'@'%';
GRANT SELECT ON mysql.roles_mapping TO '${monitor_user}'@'%';

GRANT SHOW DATABASES ON *.* TO '${service_user}'@'%';
GRANT SELECT ON mysql.user TO '${service_user}'@'%';
GRANT SELECT ON mysql.db TO '${service_user}'@'%';
GRANT SELECT ON mysql.tables_priv TO '${service_user}'@'%';
GRANT SELECT ON mysql.columns_priv TO '${service_user}'@'%';
GRANT SELECT ON mysql.procs_priv TO '${service_user}'@'%';
GRANT SELECT ON mysql.proxies_priv TO '${service_user}'@'%';
GRANT SELECT ON mysql.global_priv TO '${service_user}'@'%';
GRANT SELECT ON mysql.roles_mapping TO '${service_user}'@'%';

FLUSH PRIVILEGES;
SQL
done

echo "MaxScale users were bootstrapped on all MariaDB nodes."
