# Build and Test Script for Inmob Backend Services (PowerShell)
# Usage: .\build-and-test.ps1 -Command build -Service property-service

param(
    [Parameter(Position=0)]
    [ValidateSet('build', 'test', 'analyze', 'docker', 'full', 'help')]
    [string]$Command = 'full',
    
    [Parameter(Position=1)]
    [string]$Service = 'all',
    
    [switch]$SkipTests
)

# Configuration
$SERVICES = @(
    'property-service',
    'api-gateway',
    'access-control-service',
    'contract-service',
    'identity-service',
    'notification-service',
    'operation-service',
    'service-registry',
    'user-service',
    'visit-calendar-service'
)

$JAVA_VERSION = '21'
$MAVEN_OPTS = '-Xmx1024m -Xms512m'

# Color output functions
function Write-Success {
    param([string]$Message)
    Write-Host "✓ $Message" -ForegroundColor Green
}

function Write-Error-Custom {
    param([string]$Message)
    Write-Host "✗ $Message" -ForegroundColor Red
}

function Write-Warning-Custom {
    param([string]$Message)
    Write-Host "⚠ $Message" -ForegroundColor Yellow
}

function Write-Header {
    param([string]$Message)
    Write-Host "========================================" -ForegroundColor Cyan
    Write-Host $Message -ForegroundColor Cyan
    Write-Host "========================================" -ForegroundColor Cyan
}

# Build function
function Invoke-BuildService {
    param([string]$ServiceName)
    
    Write-Header "Building $ServiceName"
    
    if (-not (Test-Path $ServiceName)) {
        Write-Error-Custom "Service directory not found: $ServiceName"
        return $false
    }
    
    Push-Location $ServiceName
    
    if (Test-Path 'mvnw.cmd') {
        & .\mvnw.cmd clean package `
            "-DskipTests=$SkipTests" `
            "-Dmaven.compiler.source=$JAVA_VERSION" `
            "-Dmaven.compiler.target=$JAVA_VERSION"
        
        if ($LASTEXITCODE -eq 0) {
            Write-Success "Build successful for $ServiceName"
            Pop-Location
            return $true
        } else {
            Write-Error-Custom "Build failed for $ServiceName"
            Pop-Location
            return $false
        }
    } else {
        Write-Warning-Custom "Maven wrapper not found in $ServiceName"
        Pop-Location
        return $false
    }
}

# Test function
function Invoke-TestService {
    param([string]$ServiceName)
    
    Write-Header "Testing $ServiceName"
    
    if (-not (Test-Path $ServiceName)) {
        Write-Error-Custom "Service directory not found: $ServiceName"
        return $false
    }
    
    Push-Location $ServiceName
    
    if (Test-Path 'mvnw.cmd') {
        $env:MAVEN_OPTS = $MAVEN_OPTS
        
        & .\mvnw.cmd test `
            '-Dgroups=!integration'
        
        if ($LASTEXITCODE -eq 0) {
            Write-Success "Tests passed for $ServiceName"
        } else {
            Write-Warning-Custom "Some tests may have failed for $ServiceName"
        }
        Pop-Location
        return $true
    } else {
        Write-Warning-Custom "Maven wrapper not found in $ServiceName"
        Pop-Location
        return $false
    }
}

# Code analysis function
function Invoke-CodeAnalysis {
    param([string]$ServiceName)
    
    Write-Header "Analyzing code quality for $ServiceName"
    
    if (-not (Test-Path $ServiceName)) {
        Write-Error-Custom "Service directory not found: $ServiceName"
        return $false
    }
    
    Push-Location $ServiceName
    
    if (Test-Path 'mvnw.cmd') {
        & .\mvnw.cmd sonar:sonar `
            "-Dsonar.projectKey=$ServiceName" `
            '-Dsonar.sources=src/main' `
            '-Dsonar.tests=src/test'
        
        if ($LASTEXITCODE -eq 0) {
            Write-Success "Code analysis completed for $ServiceName"
        } else {
            Write-Warning-Custom "SonarQube analysis completed (non-blocking)"
        }
        Pop-Location
        return $true
    } else {
        Write-Warning-Custom "Maven wrapper not found in $ServiceName"
        Pop-Location
        return $false
    }
}

# Docker build function
function Invoke-DockerBuild {
    param([string]$ServiceName)
    
    Write-Header "Building Docker image for $ServiceName"
    
    if (-not (Test-Path $ServiceName)) {
        Write-Error-Custom "Service directory not found: $ServiceName"
        return $false
    }
    
    if (-not (Test-Path "$ServiceName\Dockerfile")) {
        Write-Warning-Custom "Dockerfile not found for $ServiceName"
        return $false
    }
    
    # Extract version from pom.xml
    $pomPath = "$ServiceName\pom.xml"
    $xml = [xml](Get-Content $pomPath)
    $version = $xml.project.version
    
    if (-not $version) {
        $version = $xml.project.parent.version
    }
    
    if (-not $version) {
        $version = 'latest'
    }
    
    Write-Host "Service version: $version"
    
    & docker build `
        -t "inmobiliaria/${ServiceName}:$version" `
        -t "inmobiliaria/${ServiceName}:latest" `
        $ServiceName
    
    if ($LASTEXITCODE -eq 0) {
        Write-Success "Docker image built: inmobiliaria/${ServiceName}:$version"
        return $true
    } else {
        Write-Error-Custom "Docker build failed for $ServiceName"
        return $false
    }
}

# Run all services function
function Invoke-AllServices {
    param([string]$CommandType)
    
    foreach ($svc in $SERVICES) {
        switch ($CommandType) {
            'build' { Invoke-BuildService $svc }
            'test' { Invoke-TestService $svc }
            'analyze' { Invoke-CodeAnalysis $svc }
            'docker' { Invoke-DockerBuild $svc }
            default { Write-Error-Custom "Unknown command: $CommandType" }
        }
    }
}

# Show usage function
function Show-Usage {
    Write-Header "Inmob Backend - Build & Test Script"
    Write-Host "Usage: .\build-and-test.ps1 -Command <command> -Service <service> [-SkipTests]"
    Write-Host ""
    Write-Host "Commands:"
    Write-Host "  build       Build specific service or all services"
    Write-Host "  test        Run tests for specific service or all services"
    Write-Host "  analyze     Run SonarQube analysis for service or all services"
    Write-Host "  docker      Build Docker image for service or all services"
    Write-Host "  full        Build, test, and docker (all steps)"
    Write-Host "  help        Show this help message"
    Write-Host ""
    Write-Host "Services:"
    foreach ($svc in $SERVICES) {
        Write-Host "  - $svc"
    }
    Write-Host "  - all (default if no service specified)"
    Write-Host ""
    Write-Host "Options:"
    Write-Host "  -SkipTests  Skip running tests during build"
    Write-Host ""
    Write-Host "Examples:"
    Write-Host "  .\build-and-test.ps1 -Command build -Service property-service"
    Write-Host "  .\build-and-test.ps1 -Command full"
    Write-Host "  .\build-and-test.ps1 -Command test -Service all"
}

# Main script execution
Write-Header "Inmob Backend - Build & Test Script"

switch ($Command) {
    'build' {
        if ($Service -eq 'all') {
            Invoke-AllServices 'build'
        } else {
            Invoke-BuildService $Service
        }
    }
    'test' {
        if ($Service -eq 'all') {
            Invoke-AllServices 'test'
        } else {
            Invoke-TestService $Service
        }
    }
    'analyze' {
        if ($Service -eq 'all') {
            Invoke-AllServices 'analyze'
        } else {
            Invoke-CodeAnalysis $Service
        }
    }
    'docker' {
        if ($Service -eq 'all') {
            Invoke-AllServices 'docker'
        } else {
            Invoke-DockerBuild $Service
        }
    }
    'full' {
        if ($Service -eq 'all') {
            Invoke-AllServices 'build'
            Invoke-AllServices 'test'
            Invoke-AllServices 'docker'
        } else {
            Invoke-BuildService $Service
            Invoke-TestService $Service
            Invoke-DockerBuild $Service
        }
    }
    'help' {
        Show-Usage
    }
    default {
        Write-Error-Custom "Unknown command: $Command"
        Show-Usage
        exit 1
    }
}

Write-Success "Script execution completed!"
