$services = @(
    "access-control-service",
    "api-gateway",
    "contract-service",
    "identity-service",
    "notification-service",
    "operation-service",
    "property-service",
    "service-registry",
    "user-service",
    "visit-calendar-service"
)

$root = $PSScriptRoot

foreach ($service in $services) {
    $path = Join-Path $root $service
    Write-Host "`n==> Formatting: $service" -ForegroundColor Cyan
    Push-Location $path
    mvn spotless:apply -o
    Pop-Location
}

Write-Host "`nDone! All services formatted." -ForegroundColor Green
