#!/usr/bin/env bash
#
# Deploy traits (backend + bundled frontend + seed DB) to the Hetzner box.
#
#   Usage:  ./deploy.sh           # day-to-day: jar + frontend-dist + seed DB + image rebuild
#           ./deploy.sh --infra   # also re-uploads Dockerfile + docker-compose.yml + entrypoint.sh
#
# Steps:
#   1. Install frontend deps (npm ci) and build the frontend (vite production build).
#   2. Build the backend fat jar (sbt-assembly).
#   3. Snapshot the local SQLite DB into a single consistent file (the seed).
#      With no local DB (fresh checkout), keep the seed already on the server.
#   4. Capture git SHA + UTC build time.
#   5. Pre-flight: bail if the server's infra files differ from the repo (unless --infra).
#   6. scp jar + frontend-dist + seed.sqlite (and infra if --infra).
#   7. Rebuild the image and restart the container.
#   8. Poll /api/health until healthy, then prune stale images.
#
# NOTE: the seed DB only initialises an EMPTY data volume (see entrypoint.sh).
# Once the live volume has data, redeploys keep it. To push a fresh dataset,
# wipe the volume first:  ssh <host> 'cd <dir> && sudo docker compose down -v'.

set -euo pipefail

HOST="service@178.104.177.218"
JUMP_HOST="service@192.168.1.6" # every ssh/scp proxies through this box
REMOTE_DIR="/home/service/compose/traits"

rssh() { ssh -J "$JUMP_HOST" "$HOST" "$@"; }
rscp() { scp -J "$JUMP_HOST" "$@"; }

INFRA="${1:-}"
if [[ -n "$INFRA" && "$INFRA" != "--infra" ]]; then
  echo "usage: $0 [--infra]" >&2
  exit 64
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
cd "$ROOT_DIR"

# Refuse to deploy if infra files on the server drifted from the repo (unless
# --infra). Pre-flight so a mismatch fails before npm + sbt.
check_infra_in_sync() {
  local has_drift=0 file remote
  for file in Dockerfile docker-compose.yml entrypoint.sh; do
    remote="$(rssh "cat $REMOTE_DIR/$file 2>/dev/null" || true)"
    if [[ "$remote" != "$(cat "$SCRIPT_DIR/$file")" ]]; then
      has_drift=1
      echo "--- infra drift: $file ---" >&2
      diff -u --label "remote:$REMOTE_DIR/$file" --label "local:$file" \
        <(printf '%s\n' "$remote") "$SCRIPT_DIR/$file" >&2 || true
    fi
  done
  if [[ $has_drift -eq 1 ]]; then
    echo "" >&2
    echo "error: server infra differs from the repo. Re-run with --infra to push." >&2
    exit 1
  fi
}

if [[ "$INFRA" != "--infra" ]]; then
  echo ">>> Checking server infra files match the repo..."
  check_infra_in_sync
fi

echo ">>> Building frontend (npm ci && npm run build)..."
# `npm ci` so a fresh checkout works and the build always matches the lockfile.
(cd frontend && npm ci --silent && npm run build --silent)
[[ -d frontend/dist ]] || { echo "error: frontend/dist not produced" >&2; exit 1; }

echo ">>> Building fat jar (sbt clean backend/assembly)..."
# Full clean so nothing stale slips into the jar. Side effect: wipes the
# frontend fastopt dir, so restart `sbt ~frontend/fastLinkJS` after deploying
# if you were running the dev server.
sbt -error clean backend/assembly

JAR_CANDIDATES=()
while IFS= read -r line; do JAR_CANDIDATES+=("$line"); done \
  < <(find backend/target -path '*/scala-*/traits.jar' -type f)
if [[ ${#JAR_CANDIDATES[@]} -eq 0 ]]; then
  echo "error: assembly produced no traits.jar under backend/target/scala-*/" >&2
  exit 1
elif [[ ${#JAR_CANDIDATES[@]} -gt 1 ]]; then
  echo "error: multiple traits.jar candidates — remove stale target dirs:" >&2
  printf '  %s\n' "${JAR_CANDIDATES[@]}" >&2
  exit 1
fi
JAR_PATH="${JAR_CANDIDATES[0]}"

LOCAL_DB="${TRAITS_DB_PATH:-traits-data/traits.sqlite}"
SEED_DB=""
if [[ -f "$LOCAL_DB" ]]; then
  echo ">>> Snapshotting local DB into a single seed file..."
  SEED_DB="$(mktemp -t traits-seed.XXXXXX).sqlite"
  # sqlite3 .backup() produces a consistent single-file copy even if the dev
  # backend holds the DB open in WAL mode.
  python3 - "$LOCAL_DB" "$SEED_DB" <<'PY'
import sqlite3, sys
src, dst = sqlite3.connect(sys.argv[1]), sqlite3.connect(sys.argv[2])
with dst:
    src.backup(dst)
dst.close(); src.close()
PY
elif rssh "test -f $REMOTE_DIR/seed.sqlite"; then
  # A fresh checkout has no local DB (traits-data/ is gitignored and the app
  # never seeds itself). The seed only matters for an EMPTY volume, so reuse
  # the one already on the server rather than refusing to deploy.
  echo ">>> No local DB at $LOCAL_DB — keeping the seed already on the server."
else
  echo "error: no local DB at $LOCAL_DB and no seed.sqlite on $HOST:$REMOTE_DIR" >&2
  echo "       (the Dockerfile COPYs seed.sqlite, so the image build needs one)" >&2
  exit 1
fi

GIT_SHA="$(git rev-parse --short HEAD 2>/dev/null || echo unknown)"
if ! git diff --quiet 2>/dev/null || ! git diff --cached --quiet 2>/dev/null; then
  GIT_SHA="${GIT_SHA}-dirty"
fi
BUILD_TIME="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

echo ">>> Ensuring remote dir exists: $HOST:$REMOTE_DIR"
rssh "mkdir -p $REMOTE_DIR"

if [[ "$INFRA" == "--infra" ]]; then
  echo ">>> Uploading infra: Dockerfile + docker-compose.yml + entrypoint.sh"
  rscp "$SCRIPT_DIR/Dockerfile" "$SCRIPT_DIR/docker-compose.yml" "$SCRIPT_DIR/entrypoint.sh" "$HOST:$REMOTE_DIR/"
  echo "    .env is NOT touched — manage it by hand on the server."
fi

echo ">>> Uploading jar ($(du -h "$JAR_PATH" | cut -f1)) + frontend-dist${SEED_DB:+ + seed.sqlite}"
rscp "$JAR_PATH" "$HOST:$REMOTE_DIR/traits.jar"
if [[ -n "$SEED_DB" ]]; then
  rscp "$SEED_DB" "$HOST:$REMOTE_DIR/seed.sqlite"
  rm -f "$SEED_DB"
fi
rssh "rm -rf $REMOTE_DIR/frontend-dist && mkdir -p $REMOTE_DIR/frontend-dist"
tar -C frontend/dist -cf - . | rssh "tar -C $REMOTE_DIR/frontend-dist -xf -"

echo ">>> Rebuilding image and restarting container on $HOST"
rssh "cd $REMOTE_DIR && sudo env GIT_SHA='$GIT_SHA' BUILD_TIME='$BUILD_TIME' docker compose up -d --build"

echo ">>> Waiting for /api/health to report ok (via container)"
for i in $(seq 1 40); do
  if rssh "sudo docker exec traits-backend wget --quiet -O- http://localhost:8080/api/health" 2>/dev/null | grep -q '"status":"ok"'; then
    echo ">>> Healthy."
    break
  fi
  sleep 1
  [[ $i -eq 40 ]] && echo "warning: /api/health not ok within 40s — check 'sudo docker compose logs traits-backend'" >&2
done

echo ">>> Pruning stale images on $HOST"
rssh "sudo docker image prune -af" > /dev/null

echo ">>> Deployed sha=$GIT_SHA at $BUILD_TIME"
