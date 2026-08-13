chcp 65001 | Out-Null

Write-Host "Starting infrastructure..." -ForegroundColor Cyan
docker-compose up -d
Write-Host "Waiting 10s for infra to be ready..." -ForegroundColor Yellow
Start-Sleep -Seconds 10

$root = $PSScriptRoot

$servers = @(
    @{ name = "admin";        module = ":admin:bootRun";        color = "Green"   },
    @{ name = "api";          module = ":api:bootRun";          color = "Blue"    },
    @{ name = "payment";      module = ":payment:bootRun";      color = "Magenta" },
    @{ name = "notification"; module = ":notification:bootRun"; color = "Yellow"  }
)

foreach ($s in $servers) {
    Write-Host "Starting $($s.name)..." -ForegroundColor $s.color
    Start-Process powershell -ArgumentList "-NoExit", "-Command",
        "chcp 65001; Set-Location '$root'; `$env:JAVA_TOOL_OPTIONS='-Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8'; Write-Host '[$($s.name)]' -ForegroundColor $($s.color); .\gradlew $($s.module)"
    Start-Sleep -Seconds 3
}

Write-Host ""
Write-Host "All servers starting. Swagger URLs:" -ForegroundColor Cyan
Write-Host "  api          http://localhost:8080/swagger-ui/index.html"
Write-Host "  admin        http://localhost:8081/swagger-ui/index.html"
Write-Host "  notification http://localhost:8083/swagger-ui/index.html"
