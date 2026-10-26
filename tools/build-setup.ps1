# Builds Lucense's installer (installer/) and puts the MSI inside it:
#
#     [installer][msi][version and upgrade code, a line each][their length: int32][msi length: int64]["LUCSETUP"]
#
# The version and upgrade code are read from the MSI itself, so the installer
# can never name a different release from the one it carries.
#
#     powershell -File tools/build-setup.ps1 -Msi <Lucense-x.y.z.msi> -Out <Lucense-x.y.z-Setup.exe> [-Preview]
#
# -Preview builds the installer without its request for administrator rights:
# for trying it out with a per-user test package.
param(
    [Parameter(Mandatory = $true)][string]$Msi,
    [Parameter(Mandatory = $true)][string]$Out,
    [switch]$Preview
)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$project = Join-Path $root 'installer'
$Msi = (Resolve-Path $Msi).Path

function Read-Property($database, [string]$name) {
    $view = $database.GetType().InvokeMember('OpenView', 'InvokeMethod', $null, $database, @("SELECT Value FROM Property WHERE Property='$name'"))
    $view.GetType().InvokeMember('Execute', 'InvokeMethod', $null, $view, $null) | Out-Null
    $record = $view.GetType().InvokeMember('Fetch', 'InvokeMethod', $null, $view, $null)
    if ($null -eq $record) { throw "The MSI has no $name." }
    $value = $record.GetType().InvokeMember('StringData', 'GetProperty', $null, $record, 1)
    $view.GetType().InvokeMember('Close', 'InvokeMethod', $null, $view, $null) | Out-Null
    return $value
}

$windowsInstaller = New-Object -ComObject WindowsInstaller.Installer
$database = $windowsInstaller.GetType().InvokeMember('OpenDatabase', 'InvokeMethod', $null, $windowsInstaller, @($Msi, 0))
$version = Read-Property $database 'ProductVersion'
$upgradeCode = Read-Property $database 'UpgradeCode'
[System.Runtime.InteropServices.Marshal]::ReleaseComObject($database) | Out-Null
[System.Runtime.InteropServices.Marshal]::ReleaseComObject($windowsInstaller) | Out-Null
Write-Host "Lucense $version, upgrade code $upgradeCode"

$arguments = @('build', $project, '-c', 'Release', '-nologo', '-v', 'q', "-p:Version=$version")
if ($Preview) { $arguments += '-p:Preview=true' }
& dotnet @arguments
if ($LASTEXITCODE -ne 0) { throw "The installer didn't build." }
$stub = if ($Preview) { Join-Path $project 'bin\Preview\Lucense-Setup.exe' } else { Join-Path $project 'bin\Release\Lucense-Setup.exe' }

$outDir = Split-Path $Out -Parent
if ($outDir) { New-Item -ItemType Directory -Force $outDir | Out-Null }
$meta = [System.Text.Encoding]::UTF8.GetBytes("$version`n$upgradeCode")
$output = [System.IO.File]::Create($Out)
try {
    foreach ($part in @($stub, $Msi)) {
        $input = [System.IO.File]::OpenRead($part)
        try { $input.CopyTo($output, 1MB) } finally { $input.Dispose() }
    }
    $output.Write($meta, 0, $meta.Length)
    $output.Write([System.BitConverter]::GetBytes([int32]$meta.Length), 0, 4)
    $output.Write([System.BitConverter]::GetBytes([int64](Get-Item $Msi).Length), 0, 8)
    $magic = [System.Text.Encoding]::ASCII.GetBytes('LUCSETUP')
    $output.Write($magic, 0, $magic.Length)
} finally {
    $output.Dispose()
}
Write-Host ("Wrote {0} ({1:N0} MB)" -f $Out, ((Get-Item $Out).Length / 1MB))
