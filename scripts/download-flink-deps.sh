#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LIB_DIR="${ROOT_DIR}/infra/cdc/lib"
POSTGRES_VERSION="42.7.7"
FLINK_CONNECTOR_VERSION="3.3.0-1.19"

declare -A ARTIFACTS=(
  ["postgresql-${POSTGRES_VERSION}.jar"]="https://jdbc.postgresql.org/download/postgresql-${POSTGRES_VERSION}.jar"
  ["flink-sql-connector-kafka-${FLINK_CONNECTOR_VERSION}.jar"]="https://repo1.maven.org/maven2/org/apache/flink/flink-sql-connector-kafka/${FLINK_CONNECTOR_VERSION}/flink-sql-connector-kafka-${FLINK_CONNECTOR_VERSION}.jar"
  ["flink-connector-jdbc-${FLINK_CONNECTOR_VERSION}.jar"]="https://repo1.maven.org/maven2/org/apache/flink/flink-connector-jdbc/${FLINK_CONNECTOR_VERSION}/flink-connector-jdbc-${FLINK_CONNECTOR_VERSION}.jar"
)

mkdir -p "${LIB_DIR}"

for filename in "${!ARTIFACTS[@]}"; do
  url="${ARTIFACTS[${filename}]}"
  target="${LIB_DIR}/${filename}"
  if [[ -f "${target}" ]]; then
    echo "Dependency already present: ${target}"
    continue
  fi
  echo "Downloading ${filename}..."
  curl -fsSL "${url}" -o "${target}"
  echo "Saved to ${target}"
done
