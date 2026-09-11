# Headless smoke test: builds the mod, boots the NeoForge dedicated server with it installed and
# requires the server to finish loading. Writes out\smoke.log and smoke-docker.log.
# Usage: .\scripts\smoke.ps1 [-Seconds 420]
param(
    [int]$Seconds = 420
)
Set-Location (Join-Path $PSScriptRoot "..")
$env:DOCKER_BUILDKIT = "1"
docker buildx build `
    --progress=plain `
    --build-arg "SMOKE_SECONDS=$Seconds" `
    --target smoke-export `
    --output "type=local,dest=out" `
    . 2>&1 | Tee-Object -FilePath smoke-docker.log
if ($LASTEXITCODE -eq 0) {
    Write-Host "`nSMOKE PASSED - server loaded the mod successfully. Full server log: out\smoke.log"
} else {
    Write-Host "`nSMOKE FAILED (exit $LASTEXITCODE) - see smoke-docker.log for the extracted errors."
}
Get-ChildItem out
