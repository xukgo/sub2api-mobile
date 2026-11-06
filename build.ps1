param(
    [ValidateSet('debug', 'release', 'test', 'all')]
    [string]$Task = 'all'
)

$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot

if (-not $env:JAVA_HOME) {
    $java = Get-ChildItem "$env:USERPROFILE\.gradle\jdks" -Filter java.exe -Recurse -ErrorAction SilentlyContinue |
        Where-Object { $_.FullName -match '\\bin\\java\.exe$' } |
        Select-Object -First 1
    if (-not $java) {
        $studioJava = 'C:\Program Files\Android\Android Studio\jbr\bin\java.exe'
        if (Test-Path $studioJava) { $java = Get-Item $studioJava }
    }
    if (-not $java) { throw 'JDK 17 was not found. Set JAVA_HOME.' }
    $env:JAVA_HOME = Split-Path (Split-Path $java.FullName)
}

if (-not $env:ANDROID_HOME) {
    $env:ANDROID_HOME = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
}
if (-not (Test-Path $env:ANDROID_HOME)) { throw 'Android SDK was not found. Set ANDROID_HOME.' }

$tasks = @(switch ($Task) {
    'debug' { @(':app:assembleDebug') }
    'release' { @(':app:assembleRelease') }
    'test' { @(':app:testDebugUnitTest') }
    default { @(':app:testDebugUnitTest', ':app:assembleDebug') }
})

# Gradle's Windows test worker cannot load classes from a non-ASCII classpath.
$drive = @('R:', 'Q:', 'P:', 'O:') | Where-Object { -not (Test-Path "$_\") } | Select-Object -First 1
if (-not $drive) { throw 'No temporary drive letter is available.' }

subst.exe $drive $root | Out-Null
try {
    Push-Location "$drive\"
    & .\gradlew.bat @tasks --no-daemon
    if ($LASTEXITCODE -ne 0) { throw "Gradle failed with exit code $LASTEXITCODE" }
    Pop-Location
} finally {
    if ((Get-Location).Path -like "$drive*") { Pop-Location }
    subst.exe $drive /D | Out-Null
}

Write-Host "APK: $root\app\build\outputs\apk\debug\app-debug.apk"
