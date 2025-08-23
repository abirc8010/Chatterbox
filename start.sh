#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_PID=""

cleanup() {
  if [[ -n "$BACKEND_PID" ]] && kill -0 "$BACKEND_PID" 2>/dev/null; then
    echo "Stopping Chatterbox backend..."
    kill "$BACKEND_PID" 2>/dev/null || true
  fi
}
trap cleanup EXIT INT TERM

cd "$ROOT_DIR"

if ! docker info >/dev/null 2>&1; then
  echo "Docker is installed but its daemon is not running. Start Docker Desktop/Engine, then rerun ./start.sh."
  exit 1
fi

echo "Starting PostgreSQL..."
docker compose up -d postgres

backend_ready=false
backend_health=$(curl -fsS http://localhost:8080/api/v1/health 2>/dev/null || true)
if [[ -n "$backend_health" ]]; then
  upload_auth_status=$(curl -sS -o /dev/null -w '%{http_code}' \
    -X POST -F "file=@$ROOT_DIR/start.sh" http://localhost:8080/api/v1/media/upload || true)
  if [[ "$upload_auth_status" == "401" && "$backend_health" == *'"mediaStorage":"cloudinary"'* && "$backend_health" == *'"mediaUploadAuth":"jwt-header-v8"'* ]]; then
    echo "Reusing the current Cloudinary-enabled Chatterbox backend at http://localhost:8080"
    backend_ready=true
  elif [[ "$upload_auth_status" == "403" || "$backend_health" != *'"mediaStorage":"cloudinary"'* || "$backend_health" != *'"mediaUploadAuth":"jwt-header-v8"'* ]]; then
    echo "An outdated Chatterbox backend is still running; restarting it..."
    backend_pids=$(ss -ltnp 'sport = :8080' | grep -oE 'pid=[0-9]+' | cut -d= -f2 | sort -u || true)
    if [[ -z "$backend_pids" ]]; then
      echo "Could not identify the old backend process. Stop the process on port 8080, then rerun ./start.sh."
      exit 1
    fi
    for pid in $backend_pids; do
      process_command=$(ps -ww -p "$pid" -o args= 2>/dev/null || true)
      if [[ "$process_command" != *com.chatterbox.chat.ChatterboxApplication* ]]; then
        echo "Port 8080 is not owned by a recognizable Chatterbox backend; refusing to stop it."
        exit 1
      fi
      kill "$pid"
    done
    for attempt in {1..15}; do
      if ! curl -fsS http://localhost:8080/api/v1/health >/dev/null 2>&1; then
        break
      fi
      sleep 1
    done
  else
    echo "A server is responding on port 8080, but its upload authorization check returned $upload_auth_status."
    echo "Stop that server and rerun ./start.sh."
    exit 1
  fi
fi

if [[ "$backend_ready" != true ]]; then
  echo "Starting Spring Boot backend..."
  (
    mvn -f "$ROOT_DIR/backend/pom.xml" -B org.springframework.boot:spring-boot-maven-plugin:3.3.5:run
  ) &
  BACKEND_PID=$!

  echo "Waiting for backend health check..."
  for attempt in {1..60}; do
    if curl -fsS http://localhost:8080/api/v1/health >/dev/null 2>&1; then
      echo "Backend is ready on http://localhost:8080"
      break
    fi
    if ! kill -0 "$BACKEND_PID" 2>/dev/null; then
      echo "Backend stopped before becoming ready. Check the Maven output above."
      exit 1
    fi
    sleep 1
  done

  if ! curl -fsS http://localhost:8080/api/v1/health >/dev/null 2>&1; then
    echo "Backend did not become ready within 60 seconds."
    exit 1
  fi
fi

docker compose exec -T postgres psql -U postgres -d chatterbox -c \
  "UPDATE conversations SET user_one_unread = COALESCE(user_one_unread, 0), user_two_unread = COALESCE(user_two_unread, 0);" \
  >/dev/null
echo "Database is ready and unread counters are normalized."

echo "Starting Chatterbox frontend..."
echo "Vite will print the exact frontend URL below."
cd "$ROOT_DIR/frontend"
npm run dev -- --host 0.0.0.0
