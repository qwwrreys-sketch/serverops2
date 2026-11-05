@rem Gradle wrapper launcher for Windows.
@rem Downloads gradle-wrapper.jar once if it is missing, then starts Gradle.
@echo off
setlocal

set APP_HOME=%~dp0
set WRAPPER_JAR=%APP_HOME%gradle\wrapper\gradle-wrapper.jar
set WRAPPER_URL=https://raw.githubusercontent.com/gradle/gradle/v8.8.0/gradle/wrapper/gradle-wrapper.jar

if not exist "%WRAPPER_JAR%" (
  echo gradle-wrapper.jar not found - downloading it once...
  if not exist "%APP_HOME%gradle\wrapper" mkdir "%APP_HOME%gradle\wrapper"
  powershell -NoProfile -ExecutionPolicy Bypass -Command "[Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -UseBasicParsing '%WRAPPER_URL%' -OutFile '%WRAPPER_JAR%'"
  if errorlevel 1 (
    echo ERROR: could not download %WRAPPER_URL%
    exit /b 1
  )
)

if defined JAVA_HOME (
  set JAVACMD=%JAVA_HOME%\bin\java.exe
) else (
  set JAVACMD=java.exe
)

"%JAVACMD%" -Xmx64m -Xms64m -Dorg.gradle.appname=gradlew -classpath "%WRAPPER_JAR%" org.gradle.wrapper.GradleWrapperMain %*
endlocal
