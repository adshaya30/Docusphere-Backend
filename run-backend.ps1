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

# Cleanup existing processes on ports 8080 and 5000
Write-Host "Cleaning up existing processes..." -ForegroundColor Yellow
$ports = @(8080, 5000)
foreach ($port in $ports) {
    $pids = netstat -ano | findstr :$port | ForEach-Object { $_.Split(' ', [System.StringSplitOptions]::RemoveEmptyEntries)[-1] } | Select-Object -Unique
    foreach ($p in $pids) {
        if ($p -gt 0) {
            Stop-Process -Id $p -Force -ErrorAction SilentlyContinue
        }
    }
}

# Start the Python OCR Service
Write-Host "Starting Python OCR Service on port 5000..." -ForegroundColor Cyan
if (Test-Path ".\venv\Scripts\python.exe") {
    Start-Process -FilePath ".\venv\Scripts\python.exe" -ArgumentList "`"scripts/ocr_server.py`"" -WindowStyle Hidden
} else {
    Write-Host "Python venv not found. Please ensure it exists." -ForegroundColor Red
}

# Run the Spring Boot backend
Write-Host "Starting Spring Boot Backend..." -ForegroundColor Green
if (Test-Path ".\mvnw.cmd") {
    .\mvnw.cmd spring-boot:run -DskipTests
} else {
    mvn spring-boot:run -DskipTests
}
