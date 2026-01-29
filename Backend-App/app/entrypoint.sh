#!/bin/sh
set -e


i=0
while [ $i -lt 30 ] && [ ! -f /appuser/kv/PGHOST ]; do
  echo "Waiting for CSI secrets... ($i)"
  sleep 1
  i=$((i+1))
done


[ -f /appuser/kv/PGHOST ]     && export PGHOST="$(cat /appuser/kv/PGHOST)"
[ -f /appuser/kv/PGPORT ]     && export PGPORT="$(cat /appuser/kv/PGPORT)"
[ -f /appuser/kv/PGDATABASE ] && export PGDATABASE="$(cat /appuser/kv/PGDATABASE)"
[ -f /appuser/kv/PGUSER ]     && export PGUSER="$(cat /appuser/kv/PGUSER)"
[ -f /appuser/kv/PGPASSWORD ] && export PGPASSWORD="$(cat /appuser/kv/PGPASSWORD)"
[ -f /appuser/kv/PGSSLMODE ]  && export PGSSLMODE="$(cat /appuser/kv/PGSSLMODE)"

exec python /appuser/app.py
