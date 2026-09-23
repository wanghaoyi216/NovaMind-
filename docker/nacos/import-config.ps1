$ErrorActionPreference = "Stop"

$nacos = $env:NACOS_ADDR
if ([string]::IsNullOrWhiteSpace($nacos)) {
  $nacos = "http://localhost:8848"
}

$configDir = Join-Path $PSScriptRoot "config"

# 导入 YAML 配置
$yamlFiles = Get-ChildItem -Path $configDir -Filter "*.yaml" -File
foreach ($file in $yamlFiles) {
  $body = @{
    dataId = $file.Name
    group  = "DEFAULT_GROUP"
    type   = "yaml"
    content = Get-Content -Path $file.FullName -Raw
  }
  Invoke-RestMethod -Method Post -Uri "$nacos/nacos/v1/cs/configs" -Body $body | Out-Null
  Write-Host "Published YAML $($file.Name)"
}

# 导入 TXT 配置（AIGC 提示词）
$txtFiles = Get-ChildItem -Path $configDir -Filter "*.txt" -File
foreach ($file in $txtFiles) {
  $body = @{
    dataId = $file.Name
    group  = "DEFAULT_GROUP"
    type   = "text"
    content = Get-Content -Path $file.FullName -Raw
  }
  Invoke-RestMethod -Method Post -Uri "$nacos/nacos/v1/cs/configs" -Body $body | Out-Null
  Write-Host "Published TXT  $($file.Name)"
}

Write-Host "Nacos config import complete: $($yamlFiles.Count) yaml + $($txtFiles.Count) txt"