$ErrorActionPreference = "Stop"

$BUILD_TOOLS = "C:\Users\user\AppData\Local\Android\Sdk\build-tools\34.0.0"
$PLATFORM = "C:\Users\user\AppData\Local\Android\Sdk\platforms\android-34\android.jar"
$PROJ = "C:\Users\user\Desktop\platform-tools\SaluteDevHelper"

$AAPT = "$BUILD_TOOLS\aapt.exe"
$D8_JAR = "$BUILD_TOOLS\lib\d8.jar"
$ZIPALIGN = "$BUILD_TOOLS\zipalign.exe"
$APKSIGNER_JAR = "$BUILD_TOOLS\lib\apksigner.jar"

Write-Host ">>> 1. Cleaning build directory..."
Remove-Item -Path "$PROJ\build\classes" -Recurse -Force -ErrorAction SilentlyContinue
Remove-Item -Path "$PROJ\build\gen" -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Path "$PROJ\build\classes" -Force | Out-Null
New-Item -ItemType Directory -Path "$PROJ\build\gen" -Force | Out-Null

Write-Host ">>> 2. Generating R.java with AAPT..."
& $AAPT package -f -m -J "$PROJ\build\gen" -M "$PROJ\app\src\main\AndroidManifest.xml" -S "$PROJ\app\src\main\res" -I $PLATFORM

Write-Host ">>> 3. Compiling Java sources with javac (-encoding UTF-8)..."
$javaFiles = Get-ChildItem -Path "$PROJ\app\src\main\java", "$PROJ\build\gen" -Filter "*.java" -Recurse | Select-Object -ExpandProperty FullName
& javac -encoding UTF-8 -source 1.8 -target 1.8 -bootclasspath $PLATFORM -cp $PLATFORM -d "$PROJ\build\classes" $javaFiles

Write-Host ">>> 4. Converting bytecode to DEX with d8..."
$classFiles = Get-ChildItem -Path "$PROJ\build\classes" -Filter "*.class" -Recurse | Select-Object -ExpandProperty FullName
& java -cp $D8_JAR com.android.tools.r8.D8 --output "$PROJ\build" --lib $PLATFORM $classFiles

Write-Host ">>> 5. Packaging APK with AAPT..."
$UNALIGNED_APK = "$PROJ\build\app-unaligned.apk"
$ALIGNED_APK = "$PROJ\build\SaluteDevHelper.apk"
Remove-Item $UNALIGNED_APK, $ALIGNED_APK -ErrorAction SilentlyContinue

& $AAPT package -f -M "$PROJ\app\src\main\AndroidManifest.xml" -S "$PROJ\app\src\main\res" -I $PLATFORM -F $UNALIGNED_APK
Set-Location "$PROJ\build"
& $AAPT add $UNALIGNED_APK "classes.dex"

Write-Host ">>> 6. Zipalign APK..."
& $ZIPALIGN -f -v 4 $UNALIGNED_APK $ALIGNED_APK

Write-Host ">>> 7. Signing APK with apksigner..."
& java -jar $APKSIGNER_JAR sign --ks "$PROJ\build\debug.keystore" --ks-pass pass:android --key-pass pass:android --ks-key-alias androiddebugkey $ALIGNED_APK

Write-Host ">>> BUILD SUCCESS! Output: $ALIGNED_APK"
