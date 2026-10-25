# server

Backend: Kotlin на JDK 25, Spring Boot 4.1.x, модульный монолит, пакет `app.podgon` (см. [server.md](../docs/server.md)).

## Запуск локально
Нужны JDK 25 и PostgreSQL 18 с расширениями `citext` и `pg_trgm` (contrib).
```
export PODGON_DB_URL=jdbc:postgresql://localhost:5432/podgon
export PODGON_DB_USER=podgon
gradle bootRun          # либо ./gradlew bootRun
```
Проверка: `curl localhost:8080/actuator/health` → `{"status":"UP"}`.

## Тесты
```
gradle test             # интеграционные тесты используют Testcontainers (образ postgres:18, нужен Docker/OrbStack)
```

## Миграции
Flyway, `src/main/resources/db/migration`, применяются при старте. Применённые миграции не редактируются.
