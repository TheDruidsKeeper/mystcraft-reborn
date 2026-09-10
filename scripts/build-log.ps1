# Same as build.ps1 but captures the full log to build-docker.log for sharing.
param(
    [string]$Tasks = "build"
)
Set-Location (Join-Path $PSScriptRoot "..")
$env:DOCKER_BUILDKIT = "1"
docker buildx build --progress=plain --build-arg "GRADLE_TASKS=$Tasks" --target export --output "type=local,dest=out" . 2>&1 | Tee-Object -FilePath build-docker.log
Write-Host "Log written to build-docker.log (exit code $LASTEXITCODE)"
