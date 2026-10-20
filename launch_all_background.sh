#!/usr/bin/env bash
# Build and launch the complete development stack in Docker.
set -euo pipefail
PROJECT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd "$PROJECT_DIR"
docker compose up --build -d
printf 'Services launched. Dashboard: http://localhost:5173\n'
printf 'Inspect readiness: docker compose ps && docker compose logs --tail=50\n'
