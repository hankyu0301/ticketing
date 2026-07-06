#!/bin/sh
set -eu

readonly sentinel_config="/data/sentinel.conf"

validate_simple_value() {
  value_name="$1"
  value="$2"

  if ! printf '%s' "$value" | grep -Eq '^[A-Za-z0-9._-]+$'; then
    echo "${value_name} may contain only letters, numbers, dots, underscores, and hyphens." >&2
    exit 1
  fi
}

validate_number() {
  value_name="$1"
  value="$2"

  if ! printf '%s' "$value" | grep -Eq '^[1-9][0-9]*$'; then
    echo "${value_name} must be a positive integer." >&2
    exit 1
  fi
}

validate_simple_value "REDIS_MASTER_NAME" "$REDIS_MASTER_NAME"
validate_simple_value "REDIS_MASTER_HOST" "$REDIS_MASTER_HOST"
validate_simple_value "REDIS_PASSWORD" "$REDIS_PASSWORD"
validate_simple_value "SENTINEL_PASSWORD" "$SENTINEL_PASSWORD"
validate_simple_value "SENTINEL_ANNOUNCE_HOST" "$SENTINEL_ANNOUNCE_HOST"
validate_number "REDIS_MASTER_PORT" "$REDIS_MASTER_PORT"
validate_number "SENTINEL_ANNOUNCE_PORT" "$SENTINEL_ANNOUNCE_PORT"
validate_number "SENTINEL_QUORUM" "$SENTINEL_QUORUM"
validate_number "SENTINEL_DOWN_AFTER_MILLISECONDS" "$SENTINEL_DOWN_AFTER_MILLISECONDS"
validate_number "SENTINEL_FAILOVER_TIMEOUT" "$SENTINEL_FAILOVER_TIMEOUT"
validate_number "SENTINEL_PARALLEL_SYNCS" "$SENTINEL_PARALLEL_SYNCS"

if [ ! -f "$sentinel_config" ]; then
  umask 077

  cat > "$sentinel_config" <<EOF
port 26379
bind 0.0.0.0
protected-mode no
dir /data
requirepass $SENTINEL_PASSWORD
sentinel resolve-hostnames yes
sentinel announce-hostnames yes
sentinel monitor $REDIS_MASTER_NAME $REDIS_MASTER_HOST $REDIS_MASTER_PORT $SENTINEL_QUORUM
sentinel auth-pass $REDIS_MASTER_NAME $REDIS_PASSWORD
sentinel down-after-milliseconds $REDIS_MASTER_NAME $SENTINEL_DOWN_AFTER_MILLISECONDS
sentinel failover-timeout $REDIS_MASTER_NAME $SENTINEL_FAILOVER_TIMEOUT
sentinel parallel-syncs $REDIS_MASTER_NAME $SENTINEL_PARALLEL_SYNCS
sentinel announce-ip $SENTINEL_ANNOUNCE_HOST
sentinel announce-port $SENTINEL_ANNOUNCE_PORT
EOF
fi

exec redis-server "$sentinel_config" --sentinel
