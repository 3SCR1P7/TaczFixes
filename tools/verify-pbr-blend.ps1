param([string]$GradleCache = (Join-Path $env:USERPROFILE '.gradle/caches/forge_gradle'))
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$testOutput = Join-Path $projectRoot 'build/pbrBlendTest'
New-Item -ItemType Directory -Force -Path $testOutput | Out-Null
$minecraftJar = Join-Path $GradleCache 'minecraft_user_repo/net/minecraftforge/forge/1.20.1-47.4.10_mapped_parchment_2023.08.20-1.20.1/forge-1.20.1-47.4.10_mapped_parchment_2023.08.20-1.20.1.jar'
if (!(Test-Path -LiteralPath $minecraftJar)) { throw 'Build the project first to populate the mapped Minecraft cache.' }
$forgeJar = Join-Path $GradleCache 'maven_downloader/net/minecraftforge/forge/1.20.1-47.4.10/forge-1.20.1-47.4.10-universal.jar'
if (!(Test-Path -LiteralPath $forgeJar)) { throw 'Missing Forge universal dependency.' }
$jars = Get-ChildItem (Join-Path $GradleCache 'maven_downloader') -Recurse -Filter '*.jar' | Where-Object {
    $_.Name -match '^(gson-2.10|failureaccess-1.0.1|guava-31.1-jre|logging-1.1.1|fastutil-8.5.9|log4j-api-2.19.0|log4j-core-2.19.0|log4j-slf4j2-impl-2.19.0|joml-1.10.5|slf4j-api-2.0.1)\.jar$' -or
    $_.Name -match '^(lwjgl|lwjgl-glfw|lwjgl-opengl)-3\.3\.1(-natives-windows)?\.jar$'
}
$classpath = (@($minecraftJar, $forgeJar) + @($jars.FullName)) -join ';'
& javac -proc:none -encoding UTF-8 -cp $classpath -d $testOutput (Join-Path $PSScriptRoot 'PbrBlendStateRegression.java')
if ($LASTEXITCODE -ne 0) { throw 'Blend state regression test compilation failed.' }
& java "-Djava.io.tmpdir=$testOutput" -cp "$classpath;$testOutput" PbrBlendStateRegression
if ($LASTEXITCODE -ne 0) { throw 'Blend state regression failed.' }
