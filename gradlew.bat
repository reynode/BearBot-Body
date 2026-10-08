@echo off
set DIR=%~dp0
if "%DIR%"=="" set DIR=.
set CLASSPATH=%DIR%gradle\wrapper\gradle-wrapper.jar
if defined JAVA_HOME (
  set JAVA_EXE=%JAVA_HOME%\bin\java.exe
) else (
  set JAVA_EXE=java
)
"%JAVA_EXE%" -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
