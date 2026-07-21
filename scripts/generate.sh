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
# Official artifact checksum; the download is verified before execution so a
# compromised mirror/CDN cannot run arbitrary code in CI. Update alongside
# GENERATOR_VERSION (sha256 of the Maven Central JAR).
GENERATOR_SHA256="cb087e40001e31eb08ef6140dd5de10938dbeb89016a1fe0481eaa25cd569026"

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

if [ ! -f "$GENERATOR_JAR" ]; then
  mkdir -p "$(dirname "$GENERATOR_JAR")"
  curl -fsSL -o "$GENERATOR_JAR" \
    "https://repo1.maven.org/maven2/org/openapitools/openapi-generator-cli/${GENERATOR_VERSION}/openapi-generator-cli-${GENERATOR_VERSION}.jar"
fi

# Fail closed on checksum mismatch (covers cached files too).
echo "${GENERATOR_SHA256}  ${GENERATOR_JAR}" | shasum -a 256 -c - >/dev/null || {
  echo "ERROR: ${GENERATOR_JAR} failed sha256 verification" >&2
  exit 1
}

curl -fsSL "$SPEC_URL" -o "$tmp/openapi3.yaml"

# hideGenerationTimestamp: @Generated annotations must not embed a wall-clock
# date or every regeneration diffs all 200+ files and "no diff, no PR" breaks.
java -jar "$GENERATOR_JAR" generate \
  -i "$tmp/openapi3.yaml" \
  -g java \
  --library native \
  -o "$tmp/gen" \
  --additional-properties=groupId=io.github.frain-dev,artifactId=convoy,apiPackage=com.getconvoy.api,modelPackage=com.getconvoy.models,invokerPackage=com.getconvoy.client,useJakartaEe=true,hideGenerationTimestamp=true

# Mirror only the three generated packages. The hand-written verify package
# (com/getconvoy/webhook) is a sibling and is never created or removed here.
# --delete keeps each generated package an exact mirror of generator output.
for pkg in api client models; do
  rsync -a --delete "$tmp/gen/src/main/java/com/getconvoy/$pkg/" \
    "src/main/java/com/getconvoy/$pkg/"
done

# Post-process: the stock generator template only reinitializes a JsonNullable
# map when the wrapper is undefined. After setX(null) or deserializing JSON
# null the wrapper is present with a null map and put*Item NPEs on get().put().
# Extend the guard to also recreate the map when the present value is null.
perl -pi -e 's/if \(this\.(\w+) == null \|\| !this\.\1\.isPresent\(\)\) \{/if (this.$1 == null || !this.$1.isPresent() || this.$1.get() == null) {/' \
  src/main/java/com/getconvoy/models/*.java

echo "Generated client synced into src/main/java/com/getconvoy/{api,client,models}"
