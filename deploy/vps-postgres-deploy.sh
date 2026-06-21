#!/bin/bash
set -euo pipefail
COMPOSE_FILE=/opt/ispf/docker-compose.postgres.yml
ENV_FILE=/opt/ispf/ispf-server.env

echo "=== Reset PostgreSQL volume (fresh Flyway run) ==="
docker-compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" down -v
docker-compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" up -d
for i in $(seq 1 60); do
  if docker-compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" exec -T postgres pg_isready -U ispf -d ispf >/dev/null 2>&1; then
    break
  fi
  sleep 2
done

echo "=== Build server from main ==="
cd /tmp
rm -rf ispf-build
git clone --depth 1 https://github.com/Michaael/IoT-Solutions-Platform.git ispf-build
cd ispf-build
chmod +x gradlew
./gradlew :packages:ispf-server:bootJar -x test --no-daemon
install -m 644 packages/ispf-server/build/libs/ispf-server.jar /opt/ispf/ispf-server.jar

echo "=== Build web console ==="
cd apps/web-console
npm ci
npm run build
rm -rf /opt/ispf/web-console/*
cp -a dist/. /opt/ispf/web-console/
chmod -R a+rX /opt/ispf/web-console

echo "=== Start ISPF ==="
systemctl restart ispf-server
for i in $(seq 1 90); do
  if curl -sf http://127.0.0.1:8080/actuator/health >/dev/null; then
    echo HEALTH_OK
    break
  fi
  if [ "$i" -eq 90 ]; then
    journalctl -u ispf-server -n 80 --no-pager
    exit 1
  fi
  sleep 2
done
curl -sf http://127.0.0.1/api/v1/info
echo
docker-compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" exec -T postgres psql -U ispf -d ispf -c "SELECT version, success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 5;"
