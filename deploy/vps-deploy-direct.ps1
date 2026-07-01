# Direct VPS deploy: build locally, SCP to staging, apply on server.
# Does NOT use GitHub Releases. See .cursor/rules/vps-deploy.mdc
#
# Optional: -VerifyClickHouse runs deploy/vps-clickhouse-verify.sh after rollout.
param(
    [Parameter(Mandatory = $true)]
    [string]$Version,

    [string]$RemoteHost = "root@ispf.iot-solutions.ru",
    [string]$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path,
    [switch]$SkipBuild,
    [switch]$SkipTests,
    [switch]$SkipDriverPacks,
    [ValidateSet('permissive', 'all')]
    [string]$DriverPackProfile = 'permissive',
    [switch]$KeepServiceRunning,
    [switch]$VerifyClickHouse
)

$ErrorActionPreference = "Stop"

$staging = "/opt/ispf/staging/$Version"
$jarOut = Join-Path $RepoRoot "packages\ispf-server\build\libs\ispf-server-$Version.jar"
$packsDir = Join-Path $RepoRoot "build\driver-packs"
$packsFilteredDir = Join-Path $RepoRoot "build\driver-packs-deploy"
$packsTar = Join-Path $RepoRoot "build\driver-packs.tar.gz"
$webRoot = Join-Path $RepoRoot "apps\web-console"
$distDir = Join-Path $webRoot "dist"
$zipPath = Join-Path $webRoot "web-console.zip"
$serviceName = "ispf-server"
$progressPollMs = 500

function Test-DriverPackIncludedInProfile {
    param(
        [string]$LicenseType,
        [string]$Profile
    )
    if ($Profile -eq 'all') { return $true }
    $patterns = @('^GPL', '^LGPL', '^MPL', '^LicenseRef-StepFunc')
    foreach ($pattern in $patterns) {
        if ($LicenseType -match $pattern) { return $false }
    }
    return $true
}

function New-FilteredDriverPacksDir {
    param(
        [string]$SourceDir,
        [string]$TargetDir,
        [string]$Profile
    )

    if (Test-Path $TargetDir) {
        Remove-Item -LiteralPath $TargetDir -Recurse -Force
    }
    New-Item -ItemType Directory -Path $TargetDir -Force | Out-Null

    $catalogPath = Join-Path $RepoRoot "gradle\driver-packs.json"
    $catalog = Get-Content -LiteralPath $catalogPath -Raw | ConvertFrom-Json
    $included = 0
    $skipped = 0

    Get-ChildItem -LiteralPath $SourceDir -Directory | ForEach-Object {
        $packId = $_.Name
        $entry = $catalog.$packId
        $licenseType = if ($entry -and $entry.licenseType) { [string]$entry.licenseType } else { 'Apache-2.0' }
        if (-not (Test-DriverPackIncludedInProfile -LicenseType $licenseType -Profile $Profile)) {
            Write-Host "    skip pack $packId ($licenseType)"
            $skipped++
            return
        }
        Copy-Item -LiteralPath $_.FullName -Destination (Join-Path $TargetDir $packId) -Recurse -Force
        $included++
    }

    if ($included -eq 0) {
        throw "No driver packs selected for profile '$Profile' (skipped $skipped)"
    }

    Write-Host "    profile '$Profile': $included pack(s), skipped $skipped copyleft/restricted"
    return $TargetDir
}

function Assert-WebConsoleLegalBundle {
    param([string]$DistDir)

    $required = @(
        (Join-Path $DistDir "legal\LICENSE"),
        (Join-Path $DistDir "legal\NOTICE"),
        (Join-Path $DistDir "legal\THIRD_PARTY_NOTICES.md")
    )
    foreach ($path in $required) {
        if (-not (Test-Path -LiteralPath $path)) {
            throw "Missing legal file in web-console dist: $path (run npm run build)"
        }
    }
}

function Invoke-Remote([string]$Command) {
    # Remote command must be one ssh argument (Windows OpenSSH splits unquoted args on spaces).
    & ssh -T -o BatchMode=yes -o ConnectTimeout=15 -o LogLevel=ERROR $RemoteHost "$Command"
    if ($LASTEXITCODE -ne 0) { throw "Remote command failed: $Command" }
}

function Invoke-SshQuiet {
    param(
        [string]$RemoteHost,
        [string]$RemoteCommand
    )
    $prevEap = $ErrorActionPreference
    $ErrorActionPreference = 'SilentlyContinue'
    try {
        $out = & ssh -T -o BatchMode=yes -o ConnectTimeout=15 -o LogLevel=ERROR $RemoteHost $RemoteCommand 2>$null
        return @{
            ExitCode = $LASTEXITCODE
            Output   = $out
        }
    } finally {
        $ErrorActionPreference = $prevEap
    }
}

function Format-DeployByteSize {
    param([long]$Bytes)
    if ($Bytes -ge 1GB) { return ("{0:N2} GB" -f ($Bytes / 1GB)) }
    if ($Bytes -ge 1MB) { return ("{0:N2} MB" -f ($Bytes / 1MB)) }
    if ($Bytes -ge 1KB) { return ("{0:N2} KB" -f ($Bytes / 1KB)) }
    return "$Bytes B"
}

function Get-RemoteFileSize {
    param(
        [string]$RemoteHost,
        [string]$RemotePath
    )
    $escaped = $RemotePath.Replace("'", "'\\''")
    $remoteCmd = "if [ -f '$escaped' ]; then stat -c%s '$escaped'; else echo 0; fi"
    $result = Invoke-SshQuiet -RemoteHost $RemoteHost -RemoteCommand $remoteCmd
    if ($result.ExitCode -ne 0) {
        return [long]0
    }
    $lastNumeric = [long]0
    foreach ($line in @($result.Output)) {
        if ($null -eq $line) { continue }
        $trimmed = "$line".Trim()
        if ($trimmed -match '^\d+$') {
            $lastNumeric = [long]$trimmed
        }
    }
    return $lastNumeric
}

function Wait-RemoteFileSize {
    param(
        [string]$RemoteHost,
        [string]$RemotePath,
        [long]$ExpectedBytes,
        [int]$MaxAttempts = 15,
        [int]$DelayMs = 1000
    )
    for ($attempt = 1; $attempt -le $MaxAttempts; $attempt++) {
        $size = Get-RemoteFileSize -RemoteHost $RemoteHost -RemotePath $RemotePath
        if ($size -ge $ExpectedBytes) {
            return $size
        }
        Start-Sleep -Milliseconds $DelayMs
    }
    return (Get-RemoteFileSize -RemoteHost $RemoteHost -RemotePath $RemotePath)
}

function Format-DeployDuration {
    param([double]$Seconds)
    if ($Seconds -lt 0 -or [double]::IsNaN($Seconds) -or [double]::IsInfinity($Seconds)) {
        return "--:--"
    }
    $total = [int][math]::Ceiling($Seconds)
    $minutes = [int][math]::Floor($total / 60)
    $secs = [int]($total % 60)
    return ("{0:D2}:{1:D2}" -f $minutes, $secs)
}

function Stop-RemoteService {
    param([string]$Reason)
    Write-Host "==> Stopping $serviceName ($Reason)"
    Invoke-Remote "systemctl stop $serviceName 2>/dev/null || true"
}

function Send-FileWithProgress {
    param(
        [string]$LocalPath,
        [string]$RemotePath,
        [string]$RemoteHost,
        [int]$FileIndex,
        [int]$FileTotal,
        [long]$CompletedBytes,
        [long]$TotalBytes
    )

    if (-not (Test-Path -LiteralPath $LocalPath)) {
        throw "Missing upload file: $LocalPath"
    }

    $fileInfo = Get-Item -LiteralPath $LocalPath
    $fileName = $fileInfo.Name
    $fileBytes = [long]$fileInfo.Length
    $activity = "Uploading to VPS"

    $existingBytes = Get-RemoteFileSize -RemoteHost $RemoteHost -RemotePath $RemotePath
    $localHash = (Get-FileHash -LiteralPath $LocalPath -Algorithm SHA256).Hash.ToLowerInvariant()
    $remoteHash = ""
    $escapedForHash = $RemotePath.Replace("'", "'\\''")
    $hashResult = Invoke-SshQuiet -RemoteHost $RemoteHost -RemoteCommand "sha256sum '$escapedForHash' 2>/dev/null | awk '{print `$1}'"
    if ($hashResult.ExitCode -eq 0 -and $hashResult.Output) {
        $remoteHash = ("$($hashResult.Output)".Trim().Split([char]10)[0]).ToLowerInvariant()
    }
    if ($remoteHash -eq $localHash) {
        Write-Host "    OK $fileName ($(Format-DeployByteSize $fileBytes), unchanged)"
        return
    }

    $escapedRemote = $RemotePath.Replace("'", "'\\''")
    Invoke-Remote "rm -f '$escapedRemote'"

    $overallStartPct = if ($TotalBytes -gt 0) {
        [int][math]::Floor($CompletedBytes * 100 / $TotalBytes)
    } else {
        0
    }

    Write-Host "    -> $fileName ($(Format-DeployByteSize $fileBytes))"

    $proc = Start-Process -FilePath "scp" `
        -ArgumentList @("-o", "BatchMode=yes", $LocalPath, "${RemoteHost}:${RemotePath}") `
        -PassThru -NoNewWindow
    if (-not $proc) {
        throw "Failed to start scp for $fileName"
    }

    $sw = [System.Diagnostics.Stopwatch]::StartNew()

    while ($true) {
        $proc.Refresh()
        if ($proc.HasExited) {
            break
        }

        $elapsedLabel = Format-DeployDuration $sw.Elapsed.TotalSeconds
        $status = (
            "[$FileIndex/$FileTotal] $fileName  uploading... $elapsedLabel  |  " +
            "total $(Format-DeployByteSize $CompletedBytes) / $(Format-DeployByteSize $TotalBytes)"
        )

        Write-Progress -Activity $activity -Status $status -PercentComplete $overallStartPct
        Write-Host ("`r{0,-120}" -f $status) -NoNewline
        Start-Sleep -Milliseconds $progressPollMs
    }

    Write-Host ""
    Write-Progress -Activity $activity -Completed

    if (-not $proc.WaitForExit(7200000)) {
        try { $proc.Kill() } catch {}
        throw "scp timed out for $fileName"
    }
    $proc.Refresh()

    $finalRemote = Wait-RemoteFileSize -RemoteHost $RemoteHost -RemotePath $RemotePath -ExpectedBytes $fileBytes
    if ($finalRemote -lt $fileBytes) {
        $exitLabel = if ($null -eq $proc.ExitCode) { "unknown" } else { "$($proc.ExitCode)" }
        throw (
            "Upload incomplete for ${fileName} (scp exit $exitLabel): remote $(Format-DeployByteSize $finalRemote) " +
            "vs local $(Format-DeployByteSize $fileBytes)"
        )
    }

    Write-Host "    OK $fileName ($(Format-DeployByteSize $fileBytes))"
}

function Send-DeployUploads {
    param(
        [string]$RemoteHost,
        [array]$Uploads
    )

    if ($Uploads.Count -eq 0) {
        return
    }

    $totalBytes = [long]0
    foreach ($upload in $Uploads) {
        $totalBytes += [long](Get-Item -LiteralPath $upload.Local).Length
    }

    Write-Host "==> Uploading to $RemoteHost ($($Uploads.Count) files, $(Format-DeployByteSize $totalBytes) total)"

    $completedBytes = [long]0
    $index = 0
    foreach ($upload in $Uploads) {
        $index++
        Send-FileWithProgress `
            -LocalPath $upload.Local `
            -RemotePath $upload.Remote `
            -RemoteHost $RemoteHost `
            -FileIndex $index `
            -FileTotal $Uploads.Count `
            -CompletedBytes $completedBytes `
            -TotalBytes $totalBytes
        $completedBytes += [long](Get-Item -LiteralPath $upload.Local).Length
    }
}

if (-not $SkipBuild) {
    Write-Host "==> Building server jar $Version"
    Push-Location $RepoRoot
    try {
        if ($SkipTests) {
            & .\gradlew ":packages:ispf-server:bootJar" "syncAllDriverPacks" "-Pversion=$Version"
        } else {
            & .\gradlew ":packages:ispf-server:test" ":packages:ispf-server:bootJar" "syncAllDriverPacks" "-Pversion=$Version"
        }
        if ($LASTEXITCODE -ne 0) { throw "Gradle build failed" }
    } finally {
        Pop-Location
    }

    if (-not (Test-Path $jarOut)) {
        throw "Missing jar: $jarOut"
    }

    Write-Host "==> Building web console"
    Push-Location $webRoot
    try {
        if (-not (Test-Path "node_modules")) {
            npm ci
            if ($LASTEXITCODE -ne 0) { throw "npm ci failed" }
        }
        npm run build
        if ($LASTEXITCODE -ne 0) { throw "npm run build failed" }
    } finally {
        Pop-Location
    }

    if (-not (Test-Path $distDir)) {
        throw "Missing dist: $distDir"
    }

    if (-not (Test-Path $distDir)) {
        throw "Missing dist: $distDir"
    }
    Assert-WebConsoleLegalBundle -DistDir $distDir

    if (-not (Test-Path $packsDir)) {
        throw "Missing driver packs: $packsDir (run syncAllDriverPacks)"
    }

    Write-Host "==> Filtering driver packs (profile: $DriverPackProfile)"
    $packsForDeploy = New-FilteredDriverPacksDir -SourceDir $packsDir -TargetDir $packsFilteredDir -Profile $DriverPackProfile

    Write-Host "==> Packaging driver-packs.tar.gz"
    if (Test-Path $packsTar) { Remove-Item $packsTar -Force }
    Push-Location $packsForDeploy
    try {
        tar -c -z -f $packsTar *
        if ($LASTEXITCODE -ne 0) { throw "tar driver-packs failed" }
    } finally {
        Pop-Location
    }

    Write-Host "==> Packaging web-console.zip (tar, Unix paths)"
    Push-Location $distDir
    try {
        if (Test-Path $zipPath) { Remove-Item $zipPath -Force }
        tar -a -c -f $zipPath *
        if ($LASTEXITCODE -ne 0) { throw "tar packaging failed" }
    } finally {
        Pop-Location
    }
}

if (-not (Test-Path $jarOut)) { throw "Jar not found: $jarOut" }
if (-not (Test-Path $zipPath)) { throw "Zip not found: $zipPath" }
if (-not $SkipDriverPacks -and -not (Test-Path $packsTar)) {
    throw "Driver packs tar not found: $packsTar"
}

if (-not $KeepServiceRunning) {
    Stop-RemoteService -Reason "reduce disk I/O before upload"
}

Write-Host "==> Preparing staging on ${RemoteHost}:$staging"
Invoke-Remote "mkdir -p $staging"
$applyScript = Join-Path $PSScriptRoot "apply-platform-update.sh"
$flywayRepairScript = Join-Path $PSScriptRoot "vps-flyway-repair.sh"

$uploads = @(
    @{ Local = $applyScript; Remote = "/opt/ispf/bin/apply-platform-update.sh" }
    @{ Local = $flywayRepairScript; Remote = "/opt/ispf/bin/vps-flyway-repair.sh" }
    @{ Local = $jarOut; Remote = "$staging/ispf-server.jar" }
    @{ Local = $zipPath; Remote = "$staging/web-console.zip" }
)
if (-not $SkipDriverPacks) {
    $uploads += @{ Local = $packsTar; Remote = "$staging/driver-packs.tar.gz" }
} else {
    Write-Host "==> Skipping driver-packs upload (-SkipDriverPacks)"
}
Send-DeployUploads -RemoteHost $RemoteHost -Uploads $uploads

Invoke-Remote "chmod +x /opt/ispf/bin/apply-platform-update.sh /opt/ispf/bin/vps-flyway-repair.sh; sed -i 's/\r$//' /opt/ispf/bin/apply-platform-update.sh /opt/ispf/bin/vps-flyway-repair.sh"

Write-Host "==> Ensuring ISPF_BOOTSTRAP_FIXTURES_ENABLED=false on VPS (prod, no demo fixtures)"
Invoke-Remote "grep -q '^ISPF_BOOTSTRAP_FIXTURES_ENABLED=' /opt/ispf/ispf-server.env 2>/dev/null && sed -i 's/^ISPF_BOOTSTRAP_FIXTURES_ENABLED=.*/ISPF_BOOTSTRAP_FIXTURES_ENABLED=false/' /opt/ispf/ispf-server.env || echo 'ISPF_BOOTSTRAP_FIXTURES_ENABLED=false' >> /opt/ispf/ispf-server.env"

Write-Host "==> Applying update on VPS"
Invoke-Remote "bash /opt/ispf/bin/apply-platform-update.sh $staging"

Write-Host "==> Verifying"
$info = Invoke-Remote "curl -sf http://127.0.0.1:8080/api/v1/info"
Write-Host $info
if ($info -notmatch "`"version`":`"$Version`"") {
    Write-Warning "Version mismatch in /api/v1/info (expected $Version)"
}

try {
    $public = curl.exe -sf "https://ispf.iot-solutions.ru/api/v1/info"
    Write-Host "Public: $public"
} catch {
    Write-Warning "Public HTTPS check skipped: $_"
}

Write-Host "Deploy complete: $Version -> $RemoteHost"

Write-Host "==> Syncing nginx (agent API long timeouts)"
& (Join-Path $PSScriptRoot "vps-nginx-sync.ps1") -RemoteHost $RemoteHost

if ($VerifyClickHouse) {
    Write-Host "==> ClickHouse verification"
    $verifyScript = Join-Path $PSScriptRoot "vps-clickhouse-verify.sh"
    if (-not (Test-Path $verifyScript)) { throw "Missing $verifyScript" }
    Send-DeployUploads -RemoteHost $RemoteHost -Uploads @(
        @{ Local = $verifyScript; Remote = "/opt/ispf/vps-clickhouse-verify.sh" }
    )
    Invoke-Remote "chmod +x /opt/ispf/vps-clickhouse-verify.sh && sed -i 's/\r$//' /opt/ispf/vps-clickhouse-verify.sh && bash /opt/ispf/vps-clickhouse-verify.sh $Version"
}
