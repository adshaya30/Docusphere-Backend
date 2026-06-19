# Set working directory to script location
Set-Location $PSScriptRoot

# Read the .env file and set environment variables
Get-Content .env | ForEach-Object {
    $line = $_.Trim()
    if ($line -and $line -match '^([^#][^=]*)=(.*)$') {
        $name = $matches[1].Trim()
        $value = $matches[2].Trim()
        # Remove surrounding quotes if they exist
        $value = $value -replace '^["'']|["'']$', ''
        if ($name) {
            [Environment]::SetEnvironmentVariable($name, $value, "Process")
        }
    }
}

Write-Host "Env vars loaded. GEMINI_API_KEY set: $(-not [string]::IsNullOrEmpty($env:GEMINI_API_KEY))" -ForegroundColor Cyan

# Use a local .m2 directory (avoids C:\Users\User\.m2 permission issues)
$localM2Repo = "$PSScriptRoot\.m2-local\repository"
New-Item -ItemType Directory -Path $localM2Repo -Force | Out-Null

# Cleanup existing processes on ports 8080 and 5000
Write-Host "Cleaning up existing processes..." -ForegroundColor Yellow
$ports = @(8080, 5000)
foreach ($port in $ports) {
    $pids = netstat -ano | findstr ":$port " | ForEach-Object {
        $_.Split(' ', [System.StringSplitOptions]::RemoveEmptyEntries)[-1]
    } | Select-Object -Unique
    foreach ($p in $pids) {
        if ($p -match '^\d+$' -and [int]$p -gt 0) {
            Stop-Process -Id ([int]$p) -Force -ErrorAction SilentlyContinue
        }
    }
}

# Full path to mvn.cmd — avoids PATH inheritance issues in child processes
$MVN = "D:\SOFTWERE\Maven\apache-maven-3.9.12\bin\mvn.cmd"

Write-Host "Starting Spring Boot Backend on port 8080..." -ForegroundColor Green
& $MVN spring-boot:run -DskipTests "-Dmaven.repo.local=$localM2Repo"
