#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LIB_DIR="${ROOT_DIR}/infra/cdc/lib"
POSTGRES_VERSION="42.7.7"
JAR_NAME="postgresql-${POSTGRES_VERSION}.jar"
TARGET="${LIB_DIR}/${JAR_NAME}"

mkdir -p "${LIB_DIR}"

if [[ -f "${TARGET}" ]]; then
  echo "PostgreSQL JDBC driver already present at ${TARGET}"
  exit 0
fi

echo "Downloading PostgreSQL JDBC driver ${POSTGRES_VERSION}..."
curl -fsSL "https://jdbc.postgresql.org/download/${JAR_NAME}" -o "${TARGET}"
echo "Driver saved to ${TARGET}"
