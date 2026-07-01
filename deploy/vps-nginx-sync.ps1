# Sync nginx ISPF site config to prod VPS and reload nginx.
param(
    [string]$RemoteHost = "root@ispf.iot-solutions.ru"
)

$ErrorActionPreference = "Stop"
$nginxConf = Join-Path $PSScriptRoot "nginx-ispf.conf"
if (-not (Test-Path $nginxConf)) {
    throw "Missing $nginxConf"
}

Write-Host "==> Uploading nginx config to $RemoteHost"
scp $nginxConf "${RemoteHost}:/etc/nginx/conf.d/ispf.conf"
ssh $RemoteHost "nginx -t && systemctl reload nginx"
Write-Host "==> nginx reloaded"
