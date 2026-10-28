@echo off
cd /d "%~dp0"
if not exist out mkdir out
javac -d out -sourcepath src src\triqueue\Main.java src\triqueue\ui\MainGUI.java
if errorlevel 1 (
  echo Compile failed. Make sure the JDK is installed.
  pause
  exit /b
)
java -cp out triqueue.ui.MainGUI