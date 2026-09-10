@echo off
rem ============================================================
rem  一键编译 DeepSeekBalance（debug APK）
rem  需要：JDK 17、Android SDK（platform 35 / build-tools 35）
rem  用之前请按你的路径修改下面两行，或先设置好系统环境变量
rem ============================================================
setlocal

if "%JAVA_HOME%"=="" set JAVA_HOME=E:\ai-toolchain\jdk\jdk-17.0.20.1+1
if "%ANDROID_HOME%"=="" set ANDROID_HOME=E:\ai-toolchain\sdk
set ANDROID_SDK_ROOT=%ANDROID_HOME%

set HERE=%~dp0
rem 如使用本机安装的 gradle，可把下面一行换成 gradle
set GRADLE_CMD=%HERE%gradlew.bat
if not exist "%GRADLE_CMD%" set GRADLE_CMD=gradle

echo [1/2] JAVA_HOME   = %JAVA_HOME%
echo [2/2] ANDROID_HOME= %ANDROID_HOME%
echo.

call "%GRADLE_CMD%" -p "%HERE%." --no-daemon --console=plain :app:assembleDebug %*
if errorlevel 1 (
  echo.
  echo 编译失败，请检查上面的错误信息。
  exit /b 1
)

echo.
echo 编译成功，APK 位于：
echo   %HERE%app\build\outputs\apk\debug\app-debug.apk
endlocal
