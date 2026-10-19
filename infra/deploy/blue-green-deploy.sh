#!/usr/bin/env bash
# -e 명령 실패시 종료, -u 정의되지 않은 변수를 사용하면 종료, pipefail 파이프라인 중간 명령 실패도 전체 실패로 처리
set -euo pipefail

# 인자 개수 검사 ( 8개인지 확인 )
if [ "$#" -ne 8 ]; then
  echo "Usage: $0 APP_DIR PROJECT NEW_IMAGE HEALTH_PATH BLUE_PORT GREEN_PORT PUBLIC_URL DRAIN_SECONDS" >&2
  exit 1
fi

APP_DIR="$1"
PROJECT="$2"
NEW_IMAGE="$3"
HEALTH_PATH="$4"
BLUE_PORT="$5"
GREEN_PORT="$6"
PUBLIC_URL="$7"
DRAIN_SECONDS="$8"

# checkout과 분리된 runner 홈 아래 경로만 사용한다.
APP_DIR="$(realpath -m -- "$APP_DIR")"
SOURCE_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd -P)"
PUBLIC_PORT="${PUBLIC_PORT:-18000}"

case "$APP_DIR" in
  "$HOME"/*) ;;
  *)
    echo "Invalid APP_DIR: $APP_DIR" >&2
    exit 1
    ;;
esac

case "$APP_DIR/" in
  "$SOURCE_DIR/"*)
    echo "APP_DIR must be outside the checkout directory." >&2
    exit 1
    ;;
esac
case "$SOURCE_DIR/" in
  "$APP_DIR/"*)
    echo "APP_DIR must not contain the checkout directory." >&2
    exit 1
    ;;
esac

if ! printf '%s' "$PROJECT" | grep -Eq '^ticketing-local([_-][a-z0-9_-]+)?$'; then
  echo "Invalid compose project name: $PROJECT" >&2
  exit 1
fi

if ! printf '%s' "$NEW_IMAGE" | grep -Eq '^ghcr\.io/[a-z0-9._/-]+:[0-9a-f]{40}$'; then
  echo "Invalid image ref: $NEW_IMAGE" >&2
  exit 1
fi

if ! printf '%s' "$HEALTH_PATH" | grep -Eq '^/[A-Za-z0-9._~/?=&%-]*$'; then
  echo "Invalid health path: $HEALTH_PATH" >&2
  exit 1
fi

for port_name in BLUE_PORT GREEN_PORT PUBLIC_PORT; do
  port="${!port_name}"

  if ! printf '%s' "$port" | grep -Eq '^[0-9]{1,5}$' ||
     [ "$port" -lt 1 ] || [ "$port" -gt 65535 ]; then
    echo "Invalid $port_name: $port" >&2
    exit 1
  fi
done

if [ "$BLUE_PORT" = "$GREEN_PORT" ] ||
   [ "$BLUE_PORT" = "$PUBLIC_PORT" ] || [ "$GREEN_PORT" = "$PUBLIC_PORT" ]; then
  echo "BLUE_PORT, GREEN_PORT and PUBLIC_PORT must be different." >&2
  exit 1
fi

case "$PUBLIC_URL" in
  "http://127.0.0.1:$PUBLIC_PORT$HEALTH_PATH"|"http://localhost:$PUBLIC_PORT$HEALTH_PATH") ;;
  *)
    echo "Invalid public health URL: $PUBLIC_URL" >&2
    exit 1
    ;;
esac

if ! printf '%s' "$DRAIN_SECONDS" | grep -Eq '^[0-9]{1,3}$' ||
   [ "$DRAIN_SECONDS" -gt 300 ]; then
  echo "Invalid DRAIN_SECONDS: $DRAIN_SECONDS" >&2
  exit 1
fi

# 필수 프로그램 ( docker, curl, flock ) 확인
for command in docker curl flock; do
  if ! command -v "$command" >/dev/null 2>&1; then
    echo "Required command not found: $command" >&2
    exit 1
  fi
done

# 같은 배포 폴더를 사용하는 자동·수동 배포를 직렬화한다.
mkdir -p -- "$APP_DIR"
LOCK_FILE="$APP_DIR/.deploy.lock"
exec 9>"$LOCK_FILE"

if ! flock -n 9; then
  echo "Another deployment is already running." >&2
  exit 1
fi

COMPOSE_FILE="$APP_DIR/docker-compose.bluegreen.yml"
SCHEMA_FILE="$APP_DIR/src/main/resources/schema.sql"
NGINX_DIR="$APP_DIR/nginx"
UPSTREAM="$NGINX_DIR/00-active-upstream.conf"
SERVER="$NGINX_DIR/10-server.conf"

require_file() {
  local file="$1"
  local label="$2"

  if [ ! -f "$file" ]; then
    echo "Missing $label: $file" >&2
    exit 1
  fi
}

# 특정 색상의 컨테이너가 실행 중인지 확인
running() {
  local color="$1"
  local container_id

  container_id="$(
    docker ps -q \
      --filter "label=com.docker.compose.project=$PROJECT" \
      --filter "label=com.docker.compose.service=app-$color" \
      --filter status=running |
      head -n 1
  )"

  [ -n "$container_id" ]
}

# 현재 실행 중인 컨테이너의 이미지 확인 (docker inspect)
current_image() {
  local color="$1"
  local container_id

  container_id="$(
    docker ps -q \
      --filter "label=com.docker.compose.project=$PROJECT" \
      --filter "label=com.docker.compose.service=app-$color" \
      --filter status=running |
      head -n 1
  )"

  if [ -z "$container_id" ]; then
    echo "Running app-$color container not found." >&2
    return 1
  fi

  docker inspect --format '{{.Config.Image}}' "$container_id"
}

# 사용자 트래픽을 받는 색상 판단 ( nginx upstream 확인 -> 실행 중인 앱 판단 )
active_color() {
  if [ -f "$UPSTREAM" ]; then
    if grep -q 'app-blue:8080' "$UPSTREAM"; then
      if running blue; then
        printf 'blue\n'
        return 0
      fi

      echo "Upstream points to app-blue, but the container is not running." >&2
      return 1
    fi

    if grep -q 'app-green:8080' "$UPSTREAM"; then
      if running green; then
        printf 'green\n'
        return 0
      fi

      echo "Upstream points to app-green, but the container is not running." >&2
      return 1
    fi

    echo "Invalid upstream config: $UPSTREAM" >&2
    return 1
  fi

  if running blue || running green; then
    echo "An app is running, but no upstream config exists." >&2
    return 1
  fi

  printf 'none\n'
}

write_upstream() {
  local color="$1"

  cat > "$UPSTREAM.next" <<EOF
upstream ticketing_app_active {
    server app-$color:8080;
}
EOF

  mv -f "$UPSTREAM.next" "$UPSTREAM"
}

check_url() {
  local url="$1"
  local label="$2"
  local attempt

  for ((attempt = 1; attempt <= 30; attempt++)); do
    if curl \
      --fail \
      --silent \
      --show-error \
      --connect-timeout 2 \
      --max-time 5 \
      "$url" >/dev/null; then
      echo "$label is healthy"
      return 0
    fi

    sleep 2
  done

  echo "$label health check failed: $url" >&2
  return 1
}

compose() {
  APP_DIR="$APP_DIR" \
  BLUE_PORT="$BLUE_PORT" \
  GREEN_PORT="$GREEN_PORT" \
  PUBLIC_PORT="$PUBLIC_PORT" \
  APP_BLUE_IMAGE="$blue_image" \
  APP_GREEN_IMAGE="$green_image" \
    docker compose \
      --project-name "$PROJECT" \
      -f "$COMPOSE_FILE" \
      "$@"
}

direct_url() {
  local color="$1"

  if [ "$color" = blue ]; then
    printf 'http://127.0.0.1:%s%s\n' "$BLUE_PORT" "$HEALTH_PATH"
  else
    printf 'http://127.0.0.1:%s%s\n' "$GREEN_PORT" "$HEALTH_PATH"
  fi
}

cleanup_backups() {
  rm -f "$UPSTREAM.bak" "$SERVER.bak" "$UPSTREAM.next"
}

# nginx 설정 검사, nginx reload, public healthcheck 중 하나라도 실패하면 rollback_nginx()
rollback_nginx() {
  local rollback_failed=false

  if [ -f "$UPSTREAM.bak" ]; then
    cp "$UPSTREAM.bak" "$UPSTREAM" || return 1
  fi

  if [ -f "$SERVER.bak" ]; then
    cp "$SERVER.bak" "$SERVER" || return 1
  fi

  if ! compose exec -T nginx nginx -t; then
    echo "Restored Nginx configuration is invalid." >&2
    rollback_failed=true
  elif ! compose exec -T nginx nginx -s reload; then
    echo "Nginx rollback reload failed." >&2
    rollback_failed=true
  fi

  if [ "$rollback_failed" = true ] || ! check_url "$PUBLIC_URL" restored-public; then
    echo "Nginx rollback needs manual recovery; apps and backups are preserved." >&2
    return 1
  fi

  cleanup_backups
}

cleanup_first_deploy() {
  compose stop nginx app-blue >/dev/null 2>&1 || true
  rm -f "$UPSTREAM" "$SERVER" "$UPSTREAM.next" "$SERVER.next"
}

# 복구 중 남은 백업은 다음 배포에서 덮어쓰지 않는다.
if [ -f "$UPSTREAM.bak" ] || [ -f "$SERVER.bak" ]; then
  echo "Unresolved Nginx backups exist. Recover the previous deployment first." >&2
  exit 1
fi

require_file "$SOURCE_DIR/docker-compose.bluegreen.yml" "source Compose file"
require_file "$SOURCE_DIR/src/main/resources/schema.sql" "source schema.sql"
require_file "$SOURCE_DIR/infra/nginx/10-server.conf" "source Nginx config"

# checkout이 정리되어도 실행 중인 Nginx의 설정 경로는 유지된다.
mkdir -p -- "$NGINX_DIR" "$(dirname -- "$SCHEMA_FILE")"
cp "$SOURCE_DIR/docker-compose.bluegreen.yml" "$COMPOSE_FILE"
cp "$SOURCE_DIR/src/main/resources/schema.sql" "$SCHEMA_FILE"
cp "$SOURCE_DIR/infra/nginx/10-server.conf" "$SERVER.next"

active="$(active_color)"

case "$active" in
  blue)
    target=green
    blue_image="$(current_image blue)"
    green_image="$NEW_IMAGE"
    ;;
  green)
    target=blue
    blue_image="$NEW_IMAGE"
    green_image="$(current_image green)"
    ;;
  none)
    target=blue
    blue_image="$NEW_IMAGE"
    green_image="$NEW_IMAGE"
    ;;
  *)
    echo "Unexpected active color: $active" >&2
    exit 1
    ;;
esac

compose config --quiet

# 최초 배포 ( active = none ): 필요한 서비스 이미지 pull (mariadb, redis, rabbitmq, nginx, app-blue)
# 이미지 pull -> nginx 설정 준비 -> 인프라와 blue 앱 시작 -> public 검사
if [ "$active" = none ]; then
  compose pull --policy missing mariadb redis rabbitmq nginx app-blue

  mv -f "$SERVER.next" "$SERVER"
  write_upstream blue

  if ! compose up -d mariadb redis rabbitmq app-blue; then
    cleanup_first_deploy
    exit 1
  fi

  if ! check_url "$(direct_url blue)" app-blue; then
    cleanup_first_deploy
    exit 1
  fi

  if ! compose up -d --no-deps nginx; then
    cleanup_first_deploy
    exit 1
  fi

  if ! check_url "$PUBLIC_URL" public; then
    cleanup_first_deploy
    exit 1
  fi

  compose ps

  echo "Initial deployment completed: blue"
  exit 0
fi

# target 이미지 pull, up
compose pull --policy missing "app-$target"
if ! compose up -d --no-deps "app-$target"; then
  compose stop "app-$target" || true
  exit 1
fi

if ! check_url "$(direct_url "$target")" "app-$target"; then
  compose stop "app-$target" || true
  exit 1
fi

# nginx 전환. 기존 설정 백업, 새 서버 설정 적용, upstream 변경
# 실패시 rollback
cp "$UPSTREAM" "$UPSTREAM.bak"
cp "$SERVER" "$SERVER.bak"
mv -f "$SERVER.next" "$SERVER"
write_upstream "$target"

if ! compose exec -T nginx nginx -t ||
   ! compose exec -T nginx nginx -s reload ||
   ! check_url "$PUBLIC_URL" public; then
  if rollback_nginx; then
    compose stop "app-$target" || true
  fi
  exit 1
fi

# 전환 직후 정상, drain_seconds 뒤에도 정상 -> 배포 확정. 이전 앱 종료
if [ "$DRAIN_SECONDS" -gt 0 ]; then
  echo "Waiting ${DRAIN_SECONDS}s before stopping app-$active."
  sleep "$DRAIN_SECONDS"
fi

if ! check_url "$PUBLIC_URL" post-drain; then
  if rollback_nginx; then
    compose stop "app-$target" || true
  fi
  exit 1
fi

compose stop "app-$active" || true
cleanup_backups

compose ps

echo "Blue-Green deployment completed: $active -> $target"
