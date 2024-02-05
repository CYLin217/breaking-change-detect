#!/bin/bash

# Specify the URL of the Swagger/OpenAPI documentation endpoint
API_DOCS_URL="http://localhost:8080/v3/api-docs"

# Specify the path to the JAR file
JAR_FILE="build/libs/breaking.change.detect-0.0.1-SNAPSHOT.jar"

# Specify the temporary directory for extraction
TEMP_DIR="/tmp/swagger-extraction"

# Specify the output directory for the Swagger file
OUTPUT_DIR="./swagger-spec"

# Create the temporary directory
mkdir -p "$TEMP_DIR"

# Step 1: Download Swagger/OpenAPI specification from the live API
curl -o "$TEMP_DIR/live-api-spec.json" "$API_DOCS_URL"

# Step 2: Extract Swagger/OpenAPI specification from the JAR file
unzip -o "$JAR_FILE" -d "$TEMP_DIR" "META-INF/resources/api-docs.json"

# Step 3: Move the extracted Swagger file to the output directory
mv "$TEMP_DIR/META-INF/resources/api-docs.json" "$OUTPUT_DIR"

# Clean up: Remove the temporary directory
rm -r "$TEMP_DIR"

echo "Swagger/OpenAPI specification downloaded and extracted successfully."
