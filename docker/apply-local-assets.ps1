$ErrorActionPreference = "Stop"

$root = Resolve-Path (Join-Path $PSScriptRoot "..")
$mysqlContainer = $env:MYSQL_CONTAINER
if ([string]::IsNullOrWhiteSpace($mysqlContainer)) {
  $mysqlContainer = "novamind-mysql"
}

$mysqlPassword = $env:MYSQL_ROOT_PASSWORD
if ([string]::IsNullOrWhiteSpace($mysqlPassword)) {
  $mysqlPassword = "novamind321bca"
}

Write-Host "Uploading Resources to MinIO..."
& (Join-Path $PSScriptRoot "minio\upload-resources.ps1")

Write-Host "Applying local development seed data..."
Get-Content -Path (Join-Path $PSScriptRoot "mysql\init\03-local-dev-seed.sql") -Raw |
  docker exec -i $mysqlContainer mysql -uroot "-p$mysqlPassword"

Write-Host "Reloading nginx if the container is running..."
$nginxRunning = docker ps --format "{{.Names}}" | Select-String -SimpleMatch "novamind-nginx"
if ($nginxRunning) {
  docker exec novamind-nginx nginx -t
  docker exec novamind-nginx nginx -s reload
}

Write-Host "Local assets applied from $root"
