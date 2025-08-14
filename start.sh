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

echo "Starting PostgreSQL..."
docker compose up -d postgres

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

docker compose exec -T postgres psql -U postgres -d chatterbox -c \
  "UPDATE conversations SET user_one_unread = COALESCE(user_one_unread, 0), user_two_unread = COALESCE(user_two_unread, 0);" \
  >/dev/null
echo "Database is ready and unread counters are normalized."

echo "Starting Chatterbox frontend..."
echo "Vite will print the exact frontend URL below."
cd "$ROOT_DIR/frontend"
npm run dev -- --host 0.0.0.0
