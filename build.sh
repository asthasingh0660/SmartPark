#!/bin/bash
# Build and run the Smart Parking Visualizer
# Usage: ./build.sh [run]

set -e

mkdir -p out

echo "Compiling..."
find src -name "*.java" -print0 | xargs -0 javac --release 17 -d out
echo "Compilation successful!"

if [ "$1" = "run" ]; then
    echo "Launching..."
    java -cp out src.ui.MainFrame
fi
