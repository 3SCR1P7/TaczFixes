param([string]$GradleCache = (Join-Path $env:USERPROFILE '.gradle/caches/forge_gradle'))
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$testOutput = Join-Path $projectRoot 'build/pbrShaderTest'
New-Item -ItemType Directory -Force -Path $testOutput | Out-Null
$jars = Get-ChildItem (Join-Path $GradleCache 'maven_downloader/org/lwjgl') -Recurse -Filter '*.jar' |
    Where-Object { $_.Name -match '^(lwjgl|lwjgl-glfw|lwjgl-opengl)-3\.3\.1(-natives-windows)?\.jar$' }
if ($jars.Count -ne 6) { throw 'Expected LWJGL 3.3.1 core, GLFW, OpenGL and Windows natives in the Forge cache.' }
$gameJar = Join-Path $GradleCache 'minecraft_repo/versions/1.20.1/client.jar'
if (!(Test-Path -LiteralPath $gameJar)) { throw "Missing Minecraft client resources: $gameJar" }
$classpath = $jars.FullName -join ';'
& javac -encoding UTF-8 -cp $classpath -d $testOutput (Join-Path $PSScriptRoot 'PbrShaderSmokeTest.java') `
    (Join-Path $PSScriptRoot 'PbrCelestialLightTest.java') `
    (Join-Path $projectRoot 'src/main/java/com/ssscript/taczfixes/client/render/pbr/CelestialLight.java')
if ($LASTEXITCODE -ne 0) { throw 'Shader test Java compilation failed.' }
& java -cp $testOutput PbrCelestialLightTest
if ($LASTEXITCODE -ne 0) { throw 'Celestial light validation failed.' }
& java "-Djava.io.tmpdir=$testOutput" -cp "$classpath;$testOutput" PbrShaderSmokeTest `
    (Join-Path $projectRoot 'src/main/resources/assets/taczfixes/shaders/core') $gameJar
if ($LASTEXITCODE -ne 0) { throw 'PBR shader validation failed.' }
