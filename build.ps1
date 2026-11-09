param(
 [Parameter(Mandatory=$true)][string]$Jdk,
 [Parameter(Mandatory=$true)][string]$BuildTools,
 [Parameter(Mandatory=$true)][string]$AndroidJar,
 [Parameter(Mandatory=$true)][string]$WorkDir,
 [Parameter(Mandatory=$true)][string]$SigningDir
)
$ErrorActionPreference='Stop'
function Run([string]$exe,[string[]]$params){ & $exe @params; if($LASTEXITCODE -ne 0){throw "Falha: $exe"} }
New-Item -ItemType Directory -Force $WorkDir,$SigningDir | Out-Null
$key=Join-Path $SigningDir 'mibo-signing.p12'
$pass=Join-Path $SigningDir 'signing-password.txt'
if(-not(Test-Path $key)){
 $bytes=New-Object byte[] 32
 [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
 [IO.File]::WriteAllText($pass,[Convert]::ToBase64String($bytes))
 Run "$Jdk\bin\keytool.exe" @('-genkeypair','-keystore',$key,'-storetype','PKCS12','-storepass:file',$pass,'-alias','mibo','-keyalg','RSA','-keysize','3072','-validity','10000','-dname','CN=Mibo Local','-noprompt')
}
foreach($app in @('receptor','controle')){
 $src=Join-Path $PSScriptRoot $app
 $dest=Join-Path $WorkDir $app
 $classes=Join-Path $dest 'classes';$dex=Join-Path $dest 'dex';$generated=Join-Path $dest 'generated'
 New-Item -ItemType Directory -Force $classes,$dex,$generated | Out-Null
 $resources=Join-Path $dest 'resources.zip';$unsigned=Join-Path $dest 'unsigned.apk';$aligned=Join-Path $dest 'aligned.apk'
 $label=if($app -eq 'receptor'){'Receptor'}else{'Controle'}
 $apk=Join-Path $PSScriptRoot "Mibo-$label-Local-v0.2.apk"
 Run "$BuildTools\aapt2.exe" @('compile','--dir',"$src\res",'-o',$resources)
 Run "$BuildTools\aapt2.exe" @('link','-o',$unsigned,'-I',$AndroidJar,'--manifest',"$src\AndroidManifest.xml",'--java',$generated,$resources)
 $sources=@(Get-ChildItem "$src\src",(Join-Path $PSScriptRoot 'common'),$generated -Recurse -Filter *.java | ForEach-Object FullName)
 Run "$Jdk\bin\javac.exe" (@('-encoding','UTF-8','-source','8','-target','8','-classpath',$AndroidJar,'-d',$classes)+$sources)
 $allClasses=@(Get-ChildItem $classes -Recurse -Filter *.class | ForEach-Object FullName)
 Run "$Jdk\bin\java.exe" (@('-cp',"$BuildTools\lib\d8.jar",'com.android.tools.r8.D8','--release','--min-api','26','--lib',$AndroidJar,'--output',$dex)+$allClasses)
 Run "$Jdk\bin\jar.exe" @('uf',$unsigned,'-C',$dex,'classes.dex')
 Run "$BuildTools\zipalign.exe" @('-f','4',$unsigned,$aligned)
 Run "$Jdk\bin\java.exe" @('-jar',"$BuildTools\lib\apksigner.jar",'sign','--ks',$key,'--ks-pass',('file:'+$pass),'--ks-key-alias','mibo','--out',$apk,$aligned)
 Run "$Jdk\bin\java.exe" @('-jar',"$BuildTools\lib\apksigner.jar",'verify','--verbose',$apk)
 Run "$BuildTools\aapt2.exe" @('dump','badging',$apk)
}
