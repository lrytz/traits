#!/bin/sh
# Seed the data volume from the image's baked-in dataset on first boot only,
# then hand off to the JVM. The app itself never seeds (see backend/Main.scala),
# so an empty volume would otherwise start with no data.
set -e

DB="${TRAITS_DB_PATH:-/app/data/traits.sqlite}"
if [ ! -f "$DB" ]; then
  echo "[entrypoint] no DB at $DB — seeding from /app/seed/traits.sqlite"
  mkdir -p "$(dirname "$DB")"
  cp /app/seed/traits.sqlite "$DB"
else
  echo "[entrypoint] existing DB at $DB — keeping it (no re-seed)"
fi

exec java --enable-native-access=ALL-UNNAMED -jar /app/traits.jar
