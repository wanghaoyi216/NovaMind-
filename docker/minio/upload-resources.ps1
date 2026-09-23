$ErrorActionPreference = "Stop"

$container = $env:MINIO_CONTAINER
if ([string]::IsNullOrWhiteSpace($container)) {
  $container = "novamind-minio"
}

$endpoint = $env:MINIO_ENDPOINT
if ([string]::IsNullOrWhiteSpace($endpoint)) {
  $endpoint = "http://localhost:9000"
}

$accessKey = $env:MINIO_ACCESS_KEY
if ([string]::IsNullOrWhiteSpace($accessKey)) {
  $accessKey = "minioadmin"
}

$secretKey = $env:MINIO_SECRET_KEY
if ([string]::IsNullOrWhiteSpace($secretKey)) {
  $secretKey = "minioadmin"
}

$bucket = $env:MINIO_ASSET_BUCKET
if ([string]::IsNullOrWhiteSpace($bucket)) {
  $bucket = "novamind-assets"
}

$root = Resolve-Path (Join-Path $PSScriptRoot "..\..\Resources")
$workDir = "/tmp/novamind-resources"

docker exec $container sh -c "rm -rf $workDir && mkdir -p $workDir" | Out-Null
docker cp "$root\." "${container}:$workDir" | Out-Null

docker exec $container mc alias set local $endpoint $accessKey $secretKey | Out-Null
docker exec $container mc mb --ignore-existing "local/$bucket" | Out-Null
docker exec $container mc anonymous set download "local/$bucket" | Out-Null
docker exec $container mc mirror --overwrite $workDir "local/$bucket" | Out-Null

Write-Host "Uploaded Resources to $endpoint/$bucket"
