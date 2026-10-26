#!/bin/sh
# Создаёт роль приложения podgon_app (см. app-role.sql). При первом запуске контейнера PostgreSQL
# вызывается автоматически; для существующего тома — вручную:
#   docker compose exec db sh /docker-entrypoint-initdb.d/10-app-role.sh
set -eu
: "${PODGON_APP_DB_PASSWORD:?нужен PODGON_APP_DB_PASSWORD}"
sed -e "s/__MIGRATOR__/${POSTGRES_USER}/g" -e "s/__APP_PASSWORD__/${PODGON_APP_DB_PASSWORD}/g" \
    /docker-entrypoint-initdb.d/app-role.sql.template \
    | psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"
