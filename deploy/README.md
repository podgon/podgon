# deploy
Dockerfile, docker-compose (backend + PostgreSQL + Mailpit), конфигурация развёртывания в Selectel (см. [architecture.md](../docs/architecture.md), раздел 7).

## Роли БД (R6)
- Миграции Flyway выполняет владелец схемы (`PODGON_DB_MIGRATOR_USER` / `PODGON_DB_MIGRATOR_PASSWORD`), приложение работает ролью `podgon_app` без прав на DDL (`PODGON_DB_USER` / `PODGON_DB_PASSWORD`).
- Роль создаёт `db-init/app-role.sql` (идемпотентно): права на данные существующих таблиц и права по умолчанию на таблицы будущих миграций.
- Локально скрипт выполняется при первом запуске тома. Для уже существующего тома — один раз вручную:
  `docker compose exec db sh /docker-entrypoint-initdb.d/10-app-role.sh`
- В Selectel (managed PostgreSQL) скрипт выполняет администратор БД, подставив имя владельца схемы и пароль роли.

## Прокси и IP клиента (R4)
- Backend наружу не публикуется: доступ только через обратный прокси (Caddy/Nginx) с TLS и `limit_req`.
- `PODGON_TRUSTED_PROXIES` — регулярное выражение адресов прокси (по умолчанию только loopback). `X-Forwarded-For` учитывается лишь от них; иначе лимиты по IP либо видят адрес прокси, либо обходятся подделкой заголовка.

## Версии клиента (R1)
- `PODGON_CLIENT_MINVERSION_ANDROID`, `PODGON_CLIENT_MINVERSION_IOS` — минимальный номер сборки; более старые получают 426 и экран обновления. По умолчанию 0 (без ограничения).
