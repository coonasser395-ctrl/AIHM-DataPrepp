#!/bin/bash
set -e
cd "$(dirname "$0")"

if ! command -v kotlinc &> /dev/null; then
  echo "Installing Kotlin (once)..."
  curl -s https://get.sdkman.io | bash
  source "$HOME/.sdkman/bin/sdkman-init.sh"
  sdk install kotlin
fi

echo "Compiling with kotlinc..."
kotlinc src/DataPrep.kt -include-runtime -d dataprep.jar

echo "Running with kotlin..."
kotlin dataprep.jar
