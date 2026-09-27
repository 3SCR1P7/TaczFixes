param([string]$GradleCache = (Join-Path $env:USERPROFILE '.gradle/caches/forge_gradle'))
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$testOutput = Join-Path $projectRoot 'build/pbrEngineTest'
New-Item -ItemType Directory -Force -Path $testOutput | Out-Null
$minecraftJar = Join-Path $GradleCache 'minecraft_user_repo/net/minecraftforge/forge/1.20.1-47.4.10_mapped_parchment_2023.08.20-1.20.1/forge-1.20.1-47.4.10_mapped_parchment_2023.08.20-1.20.1.jar'
if (!(Test-Path -LiteralPath $minecraftJar)) { throw 'Build the project first to populate the mapped Minecraft cache.' }
$forgeJar = Join-Path $GradleCache 'maven_downloader/net/minecraftforge/forge/1.20.1-47.4.10/forge-1.20.1-47.4.10-universal.jar'
if (!(Test-Path -LiteralPath $forgeJar)) { throw 'Missing Forge universal dependency.' }
$manifest = Get-Content (Join-Path $GradleCache 'minecraft_repo/versions/1.20.1/version.json') -Raw | ConvertFrom-Json
$jars = foreach ($library in $manifest.libraries) {
    $p = Join-Path $GradleCache ('maven_downloader/' + $library.downloads.artifact.path)
    if (Test-Path -LiteralPath $p -PathType Leaf) { Get-Item -LiteralPath $p }
}
$jars += Get-ChildItem (Join-Path $projectRoot '.build-cache/caches/modules-2/files-2.1') -Recurse -Filter '*.jar' | Where-Object { $_.FullName -match '\\(net.minecraftforge|cpw.mods|org.ow2.asm|org.spongepowered|com.electronwill.night-config)\\' }
$classpath = (@($minecraftJar, $forgeJar) + @($jars.FullName)) -join ';'
& javac -proc:none -encoding UTF-8 -cp $classpath -d $testOutput (Join-Path $PSScriptRoot 'PbrEnginePipelineTest.java') (Join-Path $projectRoot 'src/main/java/com/ssscript/taczfixes/client/render/pbr/IlluminatedVertexConsumer.java')
if ($LASTEXITCODE -ne 0) { throw 'Engine pipeline regression test compilation failed.' }
& java "-Djava.io.tmpdir=$testOutput" -cp "$classpath;$testOutput" PbrEnginePipelineTest (Join-Path $projectRoot 'src/main/resources/assets/taczfixes') (Join-Path $GradleCache 'minecraft_repo/versions/1.20.1/client.jar')
if ($LASTEXITCODE -ne 0) { throw 'Engine pipeline regression failed.' }
& java "-Djava.io.tmpdir=$testOutput" -cp "$classpath;$testOutput" PbrEnginePipelineTest (Join-Path $projectRoot 'src/main/resources/assets/taczfixes') (Join-Path $GradleCache 'minecraft_repo/versions/1.20.1/client.jar') thin
if ($LASTEXITCODE -ne 0) { throw 'Thin emission engine pipeline regression failed.' }





