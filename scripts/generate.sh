#!/usr/bin/env bash
set -euo pipefail

# Regenerate the API client from Convoy's OpenAPI spec with OpenAPI Generator
# (java, native library), then sync it into src/main/java/com/getconvoy/
# without touching the hand-written webhook verify package.
#
# Requires: java 17+, rsync, curl. Run from the repo root.

SPEC_URL="${SPEC_URL:-https://raw.githubusercontent.com/frain-dev/convoy/main/docs/v3/openapi3.yaml}"
# Pin so regeneration output is reproducible; bump deliberately.
GENERATOR_VERSION="7.23.0"
GENERATOR_JAR="${GENERATOR_JAR:-.cache/openapi-generator-cli-${GENERATOR_VERSION}.jar}"

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

if [ ! -f "$GENERATOR_JAR" ]; then
  mkdir -p "$(dirname "$GENERATOR_JAR")"
  curl -fsSL -o "$GENERATOR_JAR" \
    "https://repo1.maven.org/maven2/org/openapitools/openapi-generator-cli/${GENERATOR_VERSION}/openapi-generator-cli-${GENERATOR_VERSION}.jar"
fi

curl -fsSL "$SPEC_URL" -o "$tmp/openapi3.yaml"

# hideGenerationTimestamp: @Generated annotations must not embed a wall-clock
# date or every regeneration diffs all 200+ files and "no diff, no PR" breaks.
java -jar "$GENERATOR_JAR" generate \
  -i "$tmp/openapi3.yaml" \
  -g java \
  --library native \
  -o "$tmp/gen" \
  --additional-properties=groupId=com.getconvoy,artifactId=convoy,apiPackage=com.getconvoy.api,modelPackage=com.getconvoy.models,invokerPackage=com.getconvoy.client,useJakartaEe=true,hideGenerationTimestamp=true

# Mirror only the three generated packages. The hand-written verify package
# (com/getconvoy/webhook) is a sibling and is never created or removed here.
# --delete keeps each generated package an exact mirror of generator output.
for pkg in api client models; do
  rsync -a --delete "$tmp/gen/src/main/java/com/getconvoy/$pkg/" \
    "src/main/java/com/getconvoy/$pkg/"
done

echo "Generated client synced into src/main/java/com/getconvoy/{api,client,models}"
