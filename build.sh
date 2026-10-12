#!/bin/bash
# Build script for mini-vercel

cd /c/Users/Admin/OneDrive/Desktop/cloud/mini-vercel

echo "Building mini-vercel with Maven..."
./mvnw package -DskipTests

if [ $? -eq 0 ]; then
    echo ""
    echo "✓ Build successful!"
    echo ""
    echo "Run the application with:"
    echo "  java -jar target/mini-vercel-0.0.1-SNAPSHOT.jar"
    echo ""
else
    echo ""
    echo "✗ Build failed. Check the output above for errors."
    exit 1
fi
