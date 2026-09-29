@echo off
cd /d "%~dp0"

echo [1/2] Compiling with kotlinc...
kotlinc src\DataPrep.kt -include-runtime -d dataprep.jar

echo [2/2] Running with kotlin...
kotlin dataprep.jar

pause
