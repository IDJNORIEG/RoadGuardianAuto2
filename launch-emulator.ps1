# launch-emulator.ps1
param(
    [string]$AVDName = "RoadGuardian_Emulator"
)

Write-Host "🚀 Iniciando emulador Android..." -ForegroundColor Green

$emulatorPath = "$env:ANDROID_HOME\emulator\emulator.exe"

if (-not (Test-Path $emulatorPath)) {
    Write-Host "❌ Emulator no encontrado" -ForegroundColor Red
    exit 1
}

# Listar AVDs disponibles
Write-Host "📱 AVDs disponibles:" -ForegroundColor Cyan
& $emulatorPath -list-avds

# Iniciar emulador
Write-Host "⚡ Iniciando $AVDName..." -ForegroundColor Yellow
Start-Process $emulatorPath -ArgumentList "-avd $AVDName -no-snapshot-load" -WindowStyle Normal

Write-Host "✅ Emulador iniciado en nueva ventana" -ForegroundColor Green
Write-Host "⏳ Esperando que el dispositivo esté listo..." -ForegroundColor Yellow

# Esperar a que el dispositivo esté online
$timeout = 120
$elapsed = 0
do {
    Start-Sleep -Seconds 2
    $elapsed += 2
    $devices = adb devices | Select-String "emulator"
    if ($devices) {
        Write-Host "✅ Emulador listo!" -ForegroundColor Green
        
        # Instalar app automáticamente
        Write-Host "📲 Instalando RoadGuardian..." -ForegroundColor Cyan
        $apkPath = ".\app\build\outputs\apk\debug\app-debug.apk"
        
        if (Test-Path $apkPath) {
            adb install -r $apkPath
            Write-Host "✅ App instalada" -ForegroundColor Green
            
            # Lanzar app
            Write-Host "🚀 Iniciando app..." -ForegroundColor Cyan
            adb shell am start -n com.roadguardian.auto/.MainActivity
            
            Write-Host "📊 Mostrando logs..." -ForegroundColor Yellow
            adb logcat -c
            adb logcat | Select-String "RoadGuardian"
        } else {
            Write-Host "⚠️ APK no encontrado. Compila primero con: .\gradlew assembleDebug" -ForegroundColor Yellow
        }
        
        break
    }
} while ($elapsed -lt $timeout)

if ($elapsed -ge $timeout) {
    Write-Host "❌ Timeout esperando al emulador" -ForegroundColor Red
}