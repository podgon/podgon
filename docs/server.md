# Серверная часть

## Стек
| Область | Выбор | Примечание |
|---|---|---|
| Язык / JVM | Kotlin 2.4 на JDK 25 (LTS) | sealed-классы; плагин kotlin-spring |
| Фреймворк | Spring Boot 4.1 | Web MVC, Security (OAuth2 Resource Server для JWT), JDBC, Flyway, Mail, Actuator, Modulith |
| Сборка | Gradle (Kotlin DSL) | Maven — допустимая альтернатива |
| Доступ к БД | `JdbcClient` с именованными параметрами, транзакции — `TransactionTemplate` | *Заменено решением этапа 1–2:* JPA/Hibernate не используются — запросы (поиск, рекурсивные CTE, блокировки) естественнее писать на SQL. Все запросы параметризованы |
| Миграции | Flyway | |
| Контракт | OpenAPI 3.0.3 (`api/openapi.yaml`, вручную), проверка запросов и ответов в тестах (swagger-request-validator) | contract-first; 3.0.3, а не 3.1 — валидатор 3.1 пока не поддерживает; клиент генерируется из yaml на этапе 3 |
| Безопасность | Spring Security (из Spring Boot 4), JWT (Nimbus), одноразовые коды (HMAC-SHA256) | пароль не используется |
| Rate limit | Лимиты входа — счётчики в таблице `email_codes`; лимиты авторизованных запросов — фиксированное окно в памяти (`UserRateLimiter`, этап 8.1) | *Заменено:* Bucket4j не понадобился; при нескольких инстансах счётчики переезжают в Redis |
| Почта | Spring Mail / API провайдера, outbox | |
| Тесты | JUnit 5, Testcontainers (PostgreSQL 18), Spring Modulith tests, swagger-request-validator | MockK и ArchUnit пока не понадобились |
| Общий код | модуль `shared-contract` (KMP): модели API, валидация, коды ошибок | общий с клиентом |

## Модульная структура (пакеты)

```
app.podgon
├── PodgonApplication
├── common/            ошибки, время (Clock), id, пагинация, утилиты — без бизнес-логики
├── identity/          пользователи, регистрация, вход, токены, профили
│   │                  публичное API модуля — классы в самом пакете `identity` (AuthenticatedUser, AuthProperties)
│   ├── web/           контроллеры
│   ├── domain/        сервисы (OtpService, AuthService, SessionService, TokenService, ProfileService)
│   ├── security/      Spring Security, конфигурация JWT
│   └── persistence/   репозитории (Spring JDBC)
├── groups/            группы, членство, роли, приглашения
├── reviews/           рекомендации, теги, публикации в группы, правила шаринга, поиск и фильтры,
│                      категории (шаблон и категории групп; до этапа 8.1 — отдельный модуль catalog)
├── moderation/        жалобы, блокировки (этап 9)
└── mail/              очередь писем (MailQueue, MailSender в корне пакета), outbox и отправка в `mail.internal`
```
Правила: модуль виден другим только через классы в корне своего пакета (порты, события); зависимости проверяет Spring Modulith (`ModularityTests`) — только в коде, чтение чужих таблиц в SQL проверяется ревью. Межмодульные связи — синхронные события в транзакции инициатора (`GroupCreated`, `MemberJoined`, `MemberLeft`, `GroupDeleting`, `UserDeleting`; см. ADR-001).

Слои внутри модуля: `web → domain (сервисы) → persistence (репозитории на JdbcClient)`. Контроллеры тонкие, бизнес-логика — в сервисах, права проверяются в одном месте модуля групп (`GroupPolicy`, наружу — порт `GroupAccess`).

## Авторизация и права

- Аутентификация: JWT access → `SecurityContext`. Идентификатор пользователя — UUID.
- Роли в группе проверяются в сервисах: `GroupAccess.requireMember(userId, groupId)`, `requireAdmin(...)`. Нет проверки — нет доступа (404 для чужих ресурсов, чтобы не раскрывать существование).
- Права членов группы определяются полем `is_admin` роли; кастомные роли в первой версии — «меточные» (отображаемое имя), права — admin/не admin (YAGNI; расширение до набора permissions — потом).

## API (черновик, `/api/v1`)

Аутентификация:
Реализовано на этапе 2 (полный контракт — `api/openapi.yaml`):
- `POST /auth/otp/request` (email, captchaToken) — 202 для любого корректного запроса независимо от наличия аккаунта; 429 + `Retry-After` при превышении лимитов (интервал 60 с, 5 кодов в час на email, 20 в час на IP); 400 `CAPTCHA_FAILED`
- `POST /auth/otp/verify` (email, code, deviceInfo) → `tokens` либо `registrationToken` для нового пользователя; `INVALID_CODE` не различает неверный, просроченный, использованный код и исчерпанные попытки; 403 `USER_BLOCKED`
- `POST /auth/register` (registrationToken, username, displayName, deviceInfo) → 201 токены; 409 `USERNAME_TAKEN` / `EMAIL_ALREADY_REGISTERED`
- `POST /auth/refresh` (ротация; повторное использование отзывает сессию), `POST /auth/logout` (по refresh-токену, идемпотентно)

Пользователь:
- `GET /me`, `PATCH /me` (username, displayName, bio, locale; отсутствующее поле не меняется), `GET /me/sessions`, `DELETE /me/sessions/{id}` — этап 2; `GET /me/delete-impact`, `POST /me/deletion/code` (код на почту), `POST /me/deletion` (`code`, `deleteReviews`) — этап 8

Сквозное (этап 8.1, контракт 0.8.0): любой запрос API может получить 426 `UPGRADE_REQUIRED` (сборка клиента ниже минимальной), авторизованный — 429 `RATE_LIMITED` с `Retry-After`.

Группы (реализовано на этапе 4; полный контракт — `api/openapi.yaml`):
- `POST /groups` (201; создатель — ADMIN), `GET /groups` (мои), `GET/PATCH/DELETE /groups/{id}` (`DELETE` требует `?confirm=true`, иначе 400 `CONFIRMATION_REQUIRED`; `reviews=RETURN_TO_PERSONAL|DELETE` — судьба рекомендаций, опубликованных только в группе; `GET /groups/{id}/delete-impact`)
- `GET /groups/{id}/members`, `DELETE /groups/{id}/members/{userId}` (себя нельзя — 403), `POST /groups/{id}/leave` (единственный админ — 409 `LAST_ADMIN`; `deleteMyReviews=true` удаляет публикации автора в группе; `GET /groups/{id}/leave-impact`)
- `PUT /groups/{id}/members/{userId}/role`
- `GET/POST /groups/{id}/roles`, `PATCH/DELETE /groups/{id}/roles/{roleId}` (системные ADMIN и MEMBER неизменяемы; при удалении роли участники получают MEMBER)
- `POST/GET /groups/{id}/invitations`, `DELETE /groups/{id}/invitations/{invitationId}` (отзыв идемпотентен)
- `POST /invitations/preview` и `POST /invitations/accept` (токен в теле, не в адресе: не попадает в журналы доступа); адресное приглашение принимает только владелец email, любая причина отказа — один и тот же `INVALID_INVITATION`; `POST /invitations/accept` принимает `share` (`ShareRule`) и `assignments`, `POST /invitations/share-preview` — предпросмотр (этап 8)

Категории:
- `GET /groups/{id}/categories` (дерево), `POST`, `PATCH`, `DELETE /groups/{id}/categories/{cid}` (`?confirm=true`; без подтверждения при наличии подкатегорий/публикаций — 400 `CONFIRMATION_REQUIRED`; последствия — `GET .../categories/{cid}/delete-impact`), `PUT .../categories/{cid}/parent` (перенос; `null` — в корень). Реализовано на этапе 5

Рекомендации:
- `POST /reviews`, `GET/PATCH/DELETE /reviews/{id}`, `GET /me/reviews`
- `PUT /reviews/{id}/groups` (набор публикаций: группа + категория)
- `GET /groups/{id}/reviews?q=&categoryIds=&tags=&tagMode=&authorId=&cursor=&limit=` — лента и поиск в группе, `GET /reviews?groupIds=...` (те же фильтры; без `groupIds` — все свои группы; результат — публикации с `groupId`), `GET /groups/{id}/tags?prefix=` — теги группы со счётчиками, `GET /me/reviews?cursor=&limit=` — мои. Курсор непрозрачный: без `q` — keyset по `(sharedAt, id[, groupId])`, с `q` — по `(релевантность, sharedAt, id, groupId)`; курсор другого режима — 400. Реализовано на этапах 6–7 (контракт 0.6.0)
- `GET /groups/{id}/tags`

Модерация: `POST /reports`, `POST /users/{id}/block`.

Служебное: `/actuator/health`, `/actuator/prometheus` (закрыто от внешнего доступа).

### Соглашения API
- JSON, UTF-8; даты — ISO-8601 UTC; идентификаторы — UUID.
- Ошибки — RFC 9457 `application/problem+json` с машинным кодом ошибки (для локализации на клиенте).
  Список кодов (`code`) в контракте — `x-extensible-enum`: новые коды добавляются без несовместимости, клиент обрабатывает неизвестный код как общую ошибку (`ErrorCode.fromCode` → `null`). Остальные перечисления в ответах — закрытые `enum`: новое значение там ломает клиент, и `oasdiff` в CI это ловит.
- Пагинация — keyset (cursor), стабильная сортировка по `(created_at, id)`.
- Валидация входных данных (Bean Validation) на сервере, независимо от клиента.
- Версия клиента: заголовки `X-Client-Platform` (`android`, `ios`) и `X-Client-Version` (номер сборки); минимальные версии — `PODGON_CLIENT_MINVERSION_ANDROID`/`_IOS`, ниже — 426 `UPGRADE_REQUIRED`. Запросы без заголовков пропускаются.
- Идемпотентность создания через `Idempotency-Key` — не реализована (не понадобилась: клиент не повторяет изменяющие запросы сам).

## Ключевые сценарии и инварианты

1. **Запрос кода:** проверка CAPTCHA и лимитов → генерация кода, сохранение HMAC (TTL, счётчик попыток) → письмо через outbox. Ответ одинаков вне зависимости от существования аккаунта.
2. **Подтверждение кода:** проверка активного кода, попыток и TTL (сравнение за постоянное время) → если пользователь есть и не заблокирован — пара токенов; иначе короткоживущий `registrationToken`. **Регистрация:** валидация username (уникальность без учёта регистра) и displayName → создание пользователя (email подтверждён по построению) → токены.
3. **Создание группы (этап 4):** транзакция — группа, копия категорий из шаблона, членство создателя с ролью ADMIN.
4. **Инвариант «в группе ≥ 1 админ»:** проверка в сервисе внутри транзакции с блокировкой (`SELECT ... FOR UPDATE` по группе) при снятии роли/удалении/выходе.
5. **Удаление категории:** разрешено, если пуста, либо все публикации в ней (и её потомках) принадлежат инициатору; иначе — отказ (в соответствии с описанием). Предупреждение: `delete-impact` возвращает число публикаций и подкатегорий; UI показывает подтверждение. Удаляются публикации в группе (не сами рекомендации в других группах).
6. **Присоединение по приглашению:** валидация токена (хэш, TTL, лимит), добавление членства, применение правил шаринга (одной SQL-операцией `INSERT ... SELECT`), в одной транзакции. Подбор категории в новой группе — по `template_id`; записи без соответствия возвращаются в предпросмотре (`unmatched`), пользователь для каждой либо назначает категорию, либо отказывается от переноса; решения приходят в запросе вступления.
7. **Выход из группы:** запрещён для единственного админа (пока не назначен другой или группа не удалена); по флагу — удаление публикаций автора в группе.
8. **Удаление аккаунта:** soft delete + анонимизация, отзыв токенов, снятие членств (единственного админа удалить нельзя — сначала передать права или удалить группу), `POST /me/deletion/code` + `POST /me/deletion` (`code`, `deleteReviews`; `GET /me/delete-impact` возвращает число отзывов, в том числе опубликованных в группах): при `false` отзывы с публикациями переводятся на системного пользователя, личные без публикаций удаляются; при `true` удаляются все отзывы пользователя; событие `UserDeleted`. Подтверждение — кодом из письма (повторный вход по OTP).
9. **Удаление группы:** `GET /groups/{id}/delete-impact` возвращает число отзывов, которые существуют только в этой группе; `DELETE /groups/{id}?reviews=RETURN_TO_PERSONAL|DELETE&confirm=true`. Отзывы, опубликованные и в других группах, не затрагиваются.
10. **Локализация:** язык берётся из `Accept-Language`/`users.locale`; письма на `ru` и `en`; ошибки API содержат машинный код без языковой привязки.

## Безопасность (реализация)
- Spring Security: stateless, CSRF отключён (нет cookie-сессий), CORS — не нужен для мобильного клиента (закрыт по умолчанию), заголовки безопасности.
- Токены: refresh хранится хэшем, привязан к семейству и устройству, ротация, отзыв.
- Rate limit: `/auth/*` — строго (по IP и по email, счётчики в БД); авторизованные запросы — общий лимит на пользователя в минуту и отдельный на поиск (`podgon.request-limits.*`, по умолчанию 300 и 60).
- Проверка сессии на каждом запросе: подпись и срок access-токена, затем — что сессия (`sid`) не отозвана и пользователь `ACTIVE` (один запрос по индексу `refresh_tokens.family_id`).
- IP клиента: `forward-headers-strategy: native`, доверенные прокси — `PODGON_TRUSTED_PROXIES` (по умолчанию только loopback); из `X-Forwarded-For` берётся самый правый недоверенный адрес.
- Ограничения размера: тело запроса, длина полей (title ≤ 200, description ≤ 5000 — на согласование), число тегов ≤ 10, длина тега ≤ 30.
- Логи без кодов, токенов и email в открытом виде (маскирование).
- Секреты — из окружения; ключи JWT ротируются (`kid`).
- Зависимости сканируются в CI.

## Конфигурация и контейнер
- Профили `local`, `test`, `prod`. Все настройки через `PODGON_*` переменные окружения.
- `-Dfile.encoding=UTF-8`, `server.servlet.encoding.charset=UTF-8` (force), JDBC `UTF8`.
- Dockerfile: multi-stage (JDK build → JRE runtime), non-root, `HEALTHCHECK`, graceful shutdown.
- Stateless: нет локальных файлов/сессий; счётчики лимитов авторизованных запросов (в памяти процесса) при нескольких инстансах переезжают в Redis.
- Роли БД: приложение — `PODGON_DB_USER` (роль `podgon_app` без DDL), Flyway — `PODGON_DB_MIGRATOR_USER` (владелец схемы); скрипт роли — `deploy/db-init/app-role.sql`.
- Планировщик (очистка просроченных кодов/приглашений/токенов): `@Scheduled` + ShedLock при нескольких инстансах.

## Тестирование
- Интеграционные (основной вид): `@SpringBootTest` + Testcontainers (Postgres), проверка прав «чужая группа», миграции, роли БД.
- Контрактные: каждый запрос и ответ MockMvc сверяется с `api/openapi.yaml`; обратная совместимость — `oasdiff` в CI.
- Архитектурные: Spring Modulith.
- Безопасность: тесты на rate limit, блокировку, отзыв токенов.

## Предложения и обоснования
- **JDBC вместо JPA (решение этапов 1–2):** весь доступ к БД — `JdbcClient` с именованными параметрами. Если запросы с динамическими фильтрами станут громоздкими — рассмотреть jOOQ (типобезопасный SQL, тоже защищает от инъекций).
- **Redis не вводим на старте** (YAGNI). Появится при нескольких инстансах (rate limit, кэш).
- **Брокер сообщений не нужен:** события внутри процесса, outbox в БД для писем.
- **Virtual threads** включить после проверки совместимости драйверов (пул JDBC ограничен).

## Реализация этапа 8.1 (замечания ревью, `docs/review.md`)
- Модуль `catalog` объединён с `reviews` (R8): `reviews.domain.CategoryService`/`TemplateCopier`, `reviews.persistence.CategoryRepository`, `reviews.web.CategoriesController`; порт `ReviewPublications` удалён; настройки категорий — `podgon.reviews.categories.*` (было `podgon.catalog.*`).
- R1: `common.ClientVersionFilter` (сервлетный фильтр до Spring Security) → 426 `UPGRADE_REQUIRED`.
- R3: декодер JWT в `SecurityConfig` после проверки подписи вызывает `SessionService.isActive`; отказ → 401, клиент пытается обновить токен, refresh для отозванной сессии или неактивного пользователя тоже отклоняется → экран входа.
- R4: `server.forward-headers-strategy: native` + `server.tomcat.remoteip.internal-proxies`; тест `ForwardedIpTest` на настоящем Tomcat.
- R5: `identity.security.UserRateLimiter` + фильтр после `BearerTokenAuthenticationFilter`.
- R6: Flyway под владельцем схемы, приложение под `podgon_app`; тест `DbRolesTest` выполняет `deploy/db-init/app-role.sql` и проверяет, что DDL запрещён, а на новые таблицы права выдаются автоматически.
- R11: миграция V7 — колонка `title_search` и `search_vector` с заменой «ё» на «е»; запрос нормализуется так же.
- R12: `reviews.domain.TagCleanup` (ночью) удаляет неиспользуемые теги; запись тегов и чистка сериализуются advisory-блокировкой (разделяемой у записи, исключительной у чистки).
- Ошибки из фильтров пишет `common.writeProblem` (тот же формат problem+json).

## Реализация этапа 8 (шаринг, выход, удаление)
- Связи модулей — синхронные события в транзакции инициатора: `groups` публикует `MemberJoined`, `MemberLeft`, `GroupDeleting`; `identity` — `UserDeleting`. Обработчики: `reviews.SharingService`/`LifecycleService` и `groups.AccountMemberships` (порядок через `@Order`: сначала группы, потом рекомендации). Эндпоинты, которым нужны данные рекомендаций (`*-impact`, `share-preview`), — в `reviews.LifecycleController`.
- Шаринг: `SharingRepository.candidates` — один запрос по правилу (рекурсивный CTE для подкатегорий, `distinct on` по рекомендации, автоподбор категории по `template_id`); публикация — пакетом `insert ... on conflict do nothing`. Записи без пары переносятся только при назначении категории новой группы в запросе.
- Удаление аккаунта: код `ACCOUNT_DELETION_CODE` (лимиты как у входа, без капчи), `AccountService.delete` проверяет код и в одной транзакции: события, отзыв токенов, анонимизация `users` (`status = DELETED`, email `deleted+<id>@invalid`). Единственный админ группы с другими участниками — 409 `LAST_ADMIN`; группа из одного участника удаляется. Открытое окно: access-токен удалённого аккаунта живёт до 15 минут (как у отозванной сессии), но профиль и сессии он уже не читает.
- Страница `/account-deletion` (ru/en) — статический файл для Google Play; форма удаления без приложения и контактный адрес — этап 9.

## Реализация этапа 4 (группы)
- Модуль `groups` (`app.podgon.groups`): `persistence` (GroupRepository, InvitationRepository), `domain` (GroupPolicy — единственное место проверки прав, GroupService, MemberService, RoleService, InvitationService, AdminGuard), `web` (контроллеры). Публичные порты: `GroupAccess` (для категорий и рекомендаций) и, в identity, `UserDirectory`; модуль не читает таблицу `users`.
- Права: не участник → 404, участник без роли админа → 403; идентификаторы вне формата UUID → 404.
- Инвариант «≥ 1 админ»: все изменяющие операции берут блокировку строки группы (`SELECT ... FOR UPDATE`), вносят изменение и вызывают `AdminGuard.ensureAdminRemains` (при нарушении транзакция откатывается, 409 `LAST_ADMIN`); страховка в БД — отложенные constraint trigger на `memberships` и `group_roles`.
- Приглашения: код 120 бит (алфавит Crockford без похожих символов, группами `XXXX-XXXX-...`, 24 знака; при вводе регистр, дефисы, пробелы игнорируются, O→0, I/L→1), в БД SHA-256 канонического вида; счётчик использований обновляется под блокировкой строки приглашения (проверено тестом на гонку). Письмо адресного приглашения кладётся в outbox в той же транзакции; payload (с токеном) стирается после отправки.
- Лимиты (`podgon.groups.*`, переменные окружения `PODGON_GROUPS_*`): групп на пользователя 50, участников 500, ролей 50, действующих приглашений 100, email-приглашений 20 в час на пользователя (429 + `Retry-After`).
- Тесты: 31 интеграционный тест (права на чужие группы, 403/404, инварианты, конкурентность, ограничения БД, лимиты, контракт).
