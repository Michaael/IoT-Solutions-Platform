#!/bin/bash
set -euo pipefail

INSTALL_ROOT="${ISPF_INSTALL_ROOT:-/opt/ispf}"
ENV_FILE="$INSTALL_ROOT/ispf-server.env"
COMPOSE_FILE="$INSTALL_ROOT/docker-compose.postgres.yml"
H2_BACKUP_DIR="$INSTALL_ROOT/data/h2-backup-$(date +%Y%m%d-%H%M%S)"
SERVICE_FILE=/etc/systemd/system/ispf-server.service

echo "=== ISPF: switch to PostgreSQL ==="

if ! command -v docker >/dev/null 2>&1; then
  echo "Docker is required" >&2
  exit 1
fi

mkdir -p "$INSTALL_ROOT/data" "$INSTALL_ROOT/staging" "$INSTALL_ROOT/bin"

if [ -f "$INSTALL_ROOT/data/ispf-local.mv.db" ]; then
  echo "=== Backing up H2 files to $H2_BACKUP_DIR ==="
  mkdir -p "$H2_BACKUP_DIR"
  cp -a "$INSTALL_ROOT/data"/ispf-local.* "$H2_BACKUP_DIR/" 2>/dev/null || true
fi

if [ ! -f "$ENV_FILE" ]; then
  DB_PASSWORD="$(openssl rand -base64 24 | tr -dc 'A-Za-z0-9' | head -c 24)"
  cat > "$ENV_FILE" <<EOF
ISPF_DB_URL=jdbc:postgresql://127.0.0.1:5432/ispf
ISPF_DB_USER=ispf
ISPF_DB_PASSWORD=${DB_PASSWORD}
SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:5432/ispf
SPRING_DATASOURCE_USERNAME=ispf
SPRING_DATASOURCE_PASSWORD=${DB_PASSWORD}
SPRING_DATASOURCE_DRIVER_CLASS_NAME=org.postgresql.Driver
SPRING_JPA_HIBERNATE_DDL_AUTO=validate
ISPF_ENVIRONMENT=prod
ISPF_UPDATE_CHECK_ENABLED=true
ISPF_UPDATE_APPLY_ENABLED=true
ISPF_UPDATE_STAGING_DIR=${INSTALL_ROOT}/staging
ISPF_UPDATE_APPLY_SCRIPT=${INSTALL_ROOT}/bin/apply-platform-update.sh
EOF
  chmod 600 "$ENV_FILE"
  echo "Created $ENV_FILE"
else
  echo "Using existing $ENV_FILE"
fi

# shellcheck disable=SC1090
set -a
source "$ENV_FILE"
set +a

cat > "$COMPOSE_FILE" <<'EOF'
services:
  postgres:
    image: timescale/timescaledb:latest-pg16
    container_name: ispf-postgres
    restart: unless-stopped
    env_file:
      - ispf-server.env
    environment:
      POSTGRES_DB: ispf
      POSTGRES_USER: ispf
      POSTGRES_PASSWORD: ${ISPF_DB_PASSWORD}
    ports:
      - "127.0.0.1:5432:5432"
    volumes:
      - ispf_pg_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ispf -d ispf"]
      interval: 5s
      timeout: 5s
      retries: 12

volumes:
  ispf_pg_data:
EOF

echo "=== Starting PostgreSQL (TimescaleDB) ==="
COMPOSE_CMD=(docker-compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE")
"${COMPOSE_CMD[@]}" up -d

echo "=== Waiting for PostgreSQL ==="
for i in $(seq 1 60); do
  if "${COMPOSE_CMD[@]}" exec -T postgres pg_isready -U ispf -d ispf >/dev/null 2>&1; then
    echo "PostgreSQL is ready"
    break
  fi
  if [ "$i" -eq 60 ]; then
    echo "PostgreSQL did not become ready in time" >&2
    "${COMPOSE_CMD[@]}" logs --tail 50 postgres >&2 || true
    exit 1
  fi
  sleep 2
done

if [ -f deploy/apply-platform-update.sh ]; then
  install -m 755 deploy/apply-platform-update.sh "$INSTALL_ROOT/bin/apply-platform-update.sh"
elif [ ! -x "$INSTALL_ROOT/bin/apply-platform-update.sh" ]; then
  echo "Warning: apply-platform-update.sh not found in $INSTALL_ROOT/bin" >&2
fi

cat > "$SERVICE_FILE" <<EOF
[Unit]
Description=ISPF Platform Server
After=network.target docker.service
Wants=docker.service

[Service]
Type=simple
User=root
WorkingDirectory=${INSTALL_ROOT}
EnvironmentFile=${ENV_FILE}
Environment=ISPF_SERVER_PORT=8080
ExecStart=/usr/bin/java -jar ${INSTALL_ROOT}/ispf-server.jar --spring.profiles.active=local
Restart=always
RestartSec=10
SuccessExitStatus=143

[Install]
WantedBy=multi-user.target
EOF

systemctl daemon-reload
systemctl enable ispf-server docker 2>/dev/null || true
systemctl enable ispf-server

echo "=== Restarting ISPF server (Flyway migrations on PostgreSQL) ==="
systemctl restart ispf-server

for i in $(seq 1 90); do
  if curl -sf http://127.0.0.1:8080/actuator/health >/dev/null 2>&1; then
    echo "ISPF health OK"
    break
  fi
  if [ "$i" -eq 90 ]; then
    echo "ISPF failed to start" >&2
    journalctl -u ispf-server -n 80 --no-pager >&2 || true
    exit 1
  fi
  sleep 2
done

echo "=== Verification ==="
curl -sf http://127.0.0.1:8080/actuator/health
echo
curl -sf http://127.0.0.1/api/v1/info
echo
"${COMPOSE_CMD[@]}" ps
echo "=== Done: PostgreSQL at 127.0.0.1:5432, H2 backup in $H2_BACKUP_DIR (if any) ==="
