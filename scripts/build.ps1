# Build the mod jar inside Docker and copy it to .\out
# Usage: .\scripts\build.ps1 [-Tasks "build"]
param(
    [string]$Tasks = "build"
)
$ErrorActionPreference = "Stop"
Set-Location (Join-Path $PSScriptRoot "..")
$env:DOCKER_BUILDKIT = "1"
docker buildx build `
    --progress=plain `
    --build-arg "GRADLE_TASKS=$Tasks" `
    --target export `
    --output "type=local,dest=out" `
    .
if ($LASTEXITCODE -ne 0) { throw "docker build failed with exit code $LASTEXITCODE" }
Write-Host "Built jars:"
Get-ChildItem out
