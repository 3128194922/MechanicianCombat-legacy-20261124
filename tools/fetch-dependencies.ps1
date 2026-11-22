param([switch]$IncludeCombatRoll)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$destination = Join-Path $projectRoot 'libs'
New-Item -ItemType Directory -Force -Path $destination | Out-Null
# Exact Modrinth release IDs, all from dependencies already used by the reference project.
$releases = [ordered]@{
    geckolib = 'aC5KMoNg'
    playeranimator = 'xe2EVE6q'
}
if ($IncludeCombatRoll) {
    $releases['combat_roll'] = '532t65Zz'
    $releases['cloth_config'] = 't8TXrZvZ'
}
$locked = @()
foreach ($name in $releases.Keys) {
    $metadata = Invoke-RestMethod -Uri ('https://api.modrinth.com/v2/version/' + $releases[$name])
    if ('forge' -notin $metadata.loaders -or '1.20.1' -notin $metadata.game_versions) {
        throw "Release does not declare Forge 1.20.1: $name"
    }
    $artifact = @($metadata.files | Where-Object primary)[0]
    if ([IO.Path]::GetFileName($artifact.filename) -ne $artifact.filename) { throw 'Invalid artifact filename' }
    $target = Join-Path $destination $artifact.filename
    if (!(Test-Path -LiteralPath $target)) { Invoke-WebRequest -Uri $artifact.url -OutFile $target }
    $hash = (Get-FileHash -LiteralPath $target -Algorithm SHA512).Hash.ToLowerInvariant()
    if ($hash -ne $artifact.hashes.sha512) { throw "Hash mismatch: $target" }
    $locked += [ordered]@{
        name = $name; version = $metadata.version_number; releaseId = $metadata.id
        scope = switch ($name) { 'combat_roll' { 'optional integration' } 'cloth_config' { 'required only when Combat Roll is installed' } default { 'required' } }
        filename = $artifact.filename; url = $artifact.url; sha512 = $hash
        sha256 = (Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash.ToLowerInvariant()
        declaredDependencies = $metadata.dependencies
    }
    Write-Output "Verified $name $($metadata.version_number)"
}
$curiosFilename = 'curios-forge-5.11.1+1.20.1.jar'
$curiosUrl = 'https://maven.theillusivec4.top/top/theillusivec4/curios/curios-forge/5.11.1%2B1.20.1/curios-forge-5.11.1%2B1.20.1.jar'
$curiosSha512 = 'b6dffc8d2da3e79203b6db7bd3970de7363cabfdd0139b475cf77b049419ea19dd857c74e5450728a1606c2c54957fb9ff0a50381311771e8507b5f05048bb46'
$curiosTarget = Join-Path $destination $curiosFilename
if (!(Test-Path -LiteralPath $curiosTarget)) { Invoke-WebRequest -Uri $curiosUrl -OutFile $curiosTarget }
if ((Get-FileHash -LiteralPath $curiosTarget -Algorithm SHA512).Hash.ToLowerInvariant() -ne $curiosSha512) { throw 'Hash mismatch: Curios' }
$locked += [ordered]@{
    name = 'curios'; version = '5.11.1+1.20.1'; releaseId = $null; filename = $curiosFilename
    url = $curiosUrl; sha512 = $curiosSha512
    sha256 = (Get-FileHash -LiteralPath $curiosTarget -Algorithm SHA256).Hash.ToLowerInvariant()
    declaredDependencies = @()
}
$locked | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $projectRoot 'docs/DEPENDENCIES.json') -Encoding utf8
