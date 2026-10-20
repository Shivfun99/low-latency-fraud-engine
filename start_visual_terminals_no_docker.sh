#!/usr/bin/env bash
# Kafka, Redis, and PostgreSQL must already be available.
set -euo pipefail
PROJECT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
if command -v gnome-terminal >/dev/null && [[ -n "${DISPLAY:-}" ]]; then
    exec gnome-terminal --title="Fraud Engine — local services" -- \
        bash "$PROJECT_DIR/scripts/run-local.sh"
fi
exec bash "$PROJECT_DIR/scripts/run-local.sh"
