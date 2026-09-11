# Headless smoke test: builds the mod, boots the NeoForge dedicated server with it installed, and runs the
# in-game self check (SelfCheck.java). Always exports out\smoke.log — including on failure, so the server
# stack traces survive.
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

$status = "out\smoke-status.txt"
if (Test-Path $status) {
    $result = (Get-Content $status -Raw).Trim()
    if ($result -eq "PASSED") {
        Write-Host "`nSMOKE PASSED - server loaded and the self check passed. Full server log: out\smoke.log"
    } else {
        Write-Host "`nSMOKE FAILED ($result) - full server log: out\smoke.log, build output: smoke-docker.log"
    }
} else {
    Write-Host "`nSMOKE FAILED before the server ran - see smoke-docker.log"
}
Get-ChildItem out
