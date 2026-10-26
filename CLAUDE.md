# Контекст проекта Podgon (для Claude)

Файл обновляется по ходу работы. В начале сессии читать его целиком, в конце значимого шага — обновлять раздел «Прогресс».

## О проекте
Podgon — мобильное приложение для обмена рекомендациями (отзывами) внутри закрытых групп. Подробное описание — `docs/description.md` (источник требований, не менять без запроса).

## Правила работы с пользователем
- Артефакты, комментарии в коде и общение — на русском.
- **Коммитить только по явной просьбе**, после правок не спрашивать «закоммитить?». Сообщение коммита — как указал пользователь (сейчас «Проработка архитектуры»); в конце — строка `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`.
- Push в `main` и `stable` — только через MR ревьювером-человеком; merge в `stable` — только по явному запросу разрешения. Работаем в ветке `develop`.
- Pronouns: по умолчанию они/them, не угадывать по имени.

## Что где лежит
| Файл | Содержимое |
|---|---|
| `docs/description.md` | Исходное описание проекта (требования владельца) |
| `docs/architecture.md` | Архитектура, ADR-001…010, безопасность, развёртывание, принятые предложения и решения по вопросам (§8) |
| `docs/plan.md` | План по этапам 0–12 с DoD, процесс этапа, backlog, риски |
| `docs/server.md` | Backend: стек, модули, API (черновик), сценарии, безопасность |
| `docs/db.md` | Схема PostgreSQL, ограничения, индексы, поиск, каскады |
| `docs/client.md` | Клиент KMP: стек, модули, экраны, безопасность, требования магазина |
| `CLAUDE.md` | Этот файл |

Код: каркас бэкенда в `server/` (Kotlin, Spring Boot 4.1.2, пакет `app.podgon`), `deploy/` (Dockerfile, docker-compose), `.github/` (CI, Dependabot, шаблон MR), `docs/` (branching, ADR). Каталоги `api/`, `shared-contract/`, `mobile/` — пока заготовки с README. Структура репозитория — `docs/architecture.md`, раздел 4.

Команды (из корня репозитория): `export JAVA_HOME=~/dev/soft/java/25/Contents/Home; ~/dev/soft/gradle/9.8.2/bin/gradle build` (либо `./gradlew build`; отдельно `:server:test`, `:shared-contract:jvmTest`); `orb start` перед тестами Testcontainers и docker compose. ktlint-формат: `ktlintFormat`. Maven Central и plugins.gradle.org иногда обрывают TLS — повторять запуск (в Dockerfile шаг сборки повторяется сам).

## Ключевые решения (кратко; детали в docs/architecture.md)
- **Стек:** сервер — Kotlin на JDK 25 (LTS) + Spring Boot 4.x, пакет `app.podgon`; клиент — Kotlin Multiplatform + Compose Multiplatform, **первый релиз только Android**, iOS — этап 11, RuStore — этап 12; БД — PostgreSQL + Flyway; общий модуль `shared-contract`; контракт OpenAPI (contract-first).
- **Архитектура:** модульный монолит (Spring Modulith), контейнер, 12-factor.
- **Вход:** без пароля, email + одноразовый код (6 цифр, HMAC, TTL 10 мин); регистрация = username + displayName после кода; JWT access + rotating refresh; сессии устройств. Требования про пароль в docs/description.md сняты осознанно.
- **Видимость:** категории принадлежат группе; категория рекомендации хранится на связи `review_groups`; шаблон категорий копируется в группу, `template_id` для сопоставления; при вступлении записи без соответствия — пользователь назначает вручную или отказывается от переноса.
- **Данные:** при удалении аккаунта отзывы переходят системному пользователю «Удалённый пользователь» (или удаляются по выбору пользователя); личные отзывы без групп допустимы; выход единственного админа запрещён; удаление группы — с выбором судьбы отзывов.
- **Инфраструктура:** хостинг Selectel (РФ), антибот — Яндекс SmartCaptcha, приложение должно работать из РФ без VPN; Google Play (аккаунт разработчика на Узбекистан), без обязательных зависимостей от сервисов Google; языки ru + en.
- **Отложены до этапа 9:** почтовый провайдер, DDoS-защита и тариф Selectel, статус оператора ПДн (152-ФЗ, юрист), проверка аккаунта Google Play на Узбекистан для резидента РФ.

## Прогресс
- [x] Репозиторий склонирован, ветка `develop`.
- [x] Этап «Архитектурное проектирование»: документы architecture/plan/server/db/client написаны и согласованы с владельцем; открытые вопросы закрыты, кроме отложенных (см. выше).
- [x] Коммиты в `develop` («Проработка архитектуры»): `943732a`, `f888f71`, `10ff3f2`.
- [x] Коммиты архитектуры и CLAUDE.md запушены пользователем в origin/develop. Если Claude нужно пушить сам: `origin` по SSH, ключ `~/.ssh/id_ed25519` с паролем — пользователь грузит его в агент командой `! ssh-add --apple-use-keychain ~/.ssh/id_ed25519`.
- [x] **Этап 0 выполнен:** созданы README.md, .editorconfig, .gitattributes, .gitignore, `.github/pull_request_template.md`, `.github/workflows/ci.yml` (скелет: проверки UTF-8/CRLF/секретов), `docs/branching.md`, `docs/adr/`, заготовки каталогов `api/ shared-contract/ server/ mobile/ deploy/` с README. **Осталось вручную (владелец):** включить защиту веток `main`/`stable` на GitHub по `docs/branching.md`; завести аккаунты; ответы на отложенные вопросы — этап 9.
- [x] **Этап 1 завершён:** `server/` — Gradle (Kotlin DSL, wrapper 9.8.2, каталог версий), Kotlin 2.3.21, Spring Boot 4.1.2, Spring Modulith 2.1.2, Flyway (V1: citext, pg_trgm), Problem Details, UTF-8, graceful shutdown. Проверено: 3 теста проходят (2 интеграционных на Testcontainers `postgres:18` + проверка модулей); `docker compose up` в `deploy/` поднимает db/mailpit/backend, health UP, миграция применилась, контейнер не от root. CI: добавлено задание `server`. Добавлены ktlint (плагин 14.2.0; `ktlintCheck` входит в `gradle build`) и `.github/dependabot.yml`. **Detekt не подключён:** стабильный 1.23.8 падает на JDK 25, 2.0 пока alpha — вернуться после стабильного релиза. Этап 1 завершён полностью. Образы в локальном docker: postgres:18, axllent/mailpit, eclipse-temurin:25-jdk/jre. Сборка: `JAVA_HOME=~/dev/soft/java/25/Contents/Home`, `gradle` из `~/dev/soft/gradle/9.8.2/bin`; OrbStack: `orb start`.
- [x] Этапы 0–1 закоммичены и запушены в `develop` (`6dbe929`); документы перенесены в `docs/`, `description_claude.md` удалён. Зелёный CI на GitHub ещё не проверялся.
- [x] **Этап 2 реализован в ветке `feature/stage2-auth` (не закоммичен):** Gradle-сборка перенесена в корень (подпроекты `server`, `shared-contract`); `shared-contract` (KMP, пока таргет jvm): DTO без сериализации, `Validation`, `ErrorCode`; миграция V2 (users, email_codes, refresh_tokens, mail_outbox, системный пользователь); модули `mail` (outbox, SMTP) и `identity` (OTP с HMAC, лимиты в БД через email_codes, SmartCaptcha, JWT HS256 с kid, refresh с ротацией и отзывом при повторном использовании, регистрация, профиль, сессии устройств, тестовый аккаунт по флагу); `api/openapi.yaml` (3.0.3, тесты сверяют запросы/ответы с ним); 56 серверных тестов + тесты shared-contract проходят, `docker compose up` проверен (письмо приходит в Mailpit). Ревью безопасности: найден и исправлен обход CAPTCHA (fail-open на любое исключение).
- Решения этапа 2: rate limit по email — 429 + Retry-After (не «всегда 202»); INVALID_CODE не различает причины; env-переменные Map-свойств без подчёркиваний в имени (`PODGON_AUTH_JWTKEYS_K1`, `PODGON_AUTH_JWTACTIVEKID`); access-токены отозванной сессии живут до 15 мин (stateless); IP-лимита на verify нет (защита — лимит попыток на код и лимит выдачи кодов).
- [ ] Осталось по этапу 2: ручная проверка и commit/merge в `develop` по просьбе пользователя; зелёный CI на GitHub не проверялся; в `.github/dependabot.yml` путь gradle `/server` нужно сменить на `/` (корневая сборка).
- [ ] Далее: этап 3 (каркас KMP-клиента). Начинать только по просьбе пользователя; перед стартом проверить план на актуальность.

## Окружение разработчика
Весь софт — в `~/dev/soft`: JDK Temurin 25 (`java/25/Contents/Home`), Gradle 9.8.2 (`gradle/9.8.2`), PostgreSQL 18.6 (`postgresql/18.6`), IntelliJ IDEA 2026.2.3, git. Docker — OrbStack (демон нужно запускать вручную). PostgreSQL 18.6 в `~/dev/soft` дополнен contrib (citext, pg_trgm, unaccent): собран из исходников 18.6 с теми же параметрами, исходники удалены. Системной Java нет — задавать `JAVA_HOME` на JDK из `~/dev/soft`.
**Правило:** сначала искать нужный софт в `~/dev/soft` и среди установленных программ; ничего не скачивать без явного вопроса и подтверждения (образы Docker, `brew install`, дистрибутивы). **Исключение:** зависимости проекта (Gradle/Maven) разрешено скачивать без дополнительного подтверждения.

## Заметки
- `CLAUDE.md` — именно в таком регистре (Claude Code читает его автоматически).
- Скрипты/временные файлы — в scratchpad сессии, не в проекте.
