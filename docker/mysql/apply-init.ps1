$ErrorActionPreference = "Stop"

$container = $env:MYSQL_CONTAINER
if ([string]::IsNullOrWhiteSpace($container)) {
  $container = "novamind-mysql"
}

$password = $env:MYSQL_ROOT_PASSWORD
if ([string]::IsNullOrWhiteSpace($password)) {
  $password = "novamind321bca"
}

$scripts = Get-ChildItem -Path (Join-Path $PSScriptRoot "init") -Filter "*.sql" -File | Sort-Object Name
foreach ($script in $scripts) {
  Get-Content -Path $script.FullName -Raw | docker exec -i $container mysql -uroot "-p$password"
  Write-Host "Applied $($script.FullName) to $container"
}
