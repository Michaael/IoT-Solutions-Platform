#!/bin/bash
set -euo pipefail

echo "=== Updating ISPF ==="
mkdir -p /opt/ispf/data /opt/ispf/web-console

if [ -f /tmp/ispf-server.jar ]; then
  mv -f /tmp/ispf-server.jar /opt/ispf/ispf-server.jar
fi

rm -rf /opt/ispf/web-console/*
SRC=/tmp/ispf-web-console-dist-new/dist
if [ ! -d "$SRC" ]; then
  SRC=/tmp/ispf-web-console-dist-new
fi
if [ ! -d "$SRC" ]; then
  SRC=/tmp/ispf-web-console-dist
fi
cp -a "$SRC"/. /opt/ispf/web-console/
chmod -R a+rX /opt/ispf/web-console
find /opt/ispf/web-console -type d -exec chmod 755 {} +

if [ -f /tmp/snmpd-ispf.conf ]; then
  cp /tmp/snmpd-ispf.conf /etc/snmp/snmpd.conf.d/ispf.conf
  systemctl restart snmpd 2>/dev/null || true
fi

systemctl restart ispf-server
sleep 25
systemctl is-active ispf-server
curl -sf http://127.0.0.1:8080/actuator/health
echo ""
curl -sf -o /dev/null -w "Web UI HTTP %{http_code}\n" http://127.0.0.1/
curl -sf http://127.0.0.1/api/v1/info
echo ""
