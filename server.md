# Серверная часть

## Стек
| Область | Выбор | Примечание |
|---|---|---|
| Язык / JVM | Java 21 (LTS) | records, виртуальные потоки |
| Фреймворк | Spring Boot 3.x | Web, Validation, Security, Data JPA, Actuator |
| Сборка | Gradle (Kotlin DSL) | Maven — допустимая альтернатива |
| Доступ к БД | Spring Data JPA (Hibernate) + нативные/JdbcClient запросы для поиска и фильтров | Все запросы параметризованы |
| Миграции | Flyway | |
| Контракт | OpenAPI 3.1, springdoc / openapi-generator | contract-first |
| Безопасность | Spring Security 6, Argon2id, JWT (Nimbus) | |
| Rate limit | Bucket4j (на старте in-memory, Redis при масштабировании) | |
| Почта | Spring Mail / API провайдера, outbox | |
| Тесты | JUnit 5, Mockito, Testcontainers, Spring Modulith tests, ArchUnit | |
| Прочее | MapStruct (маппинг), Lombok не используем (records) | |

## Модульная структура (пакеты)

```
ru.podgon
├── PodgonApplication
├── common/            ошибки, время (Clock), id, пагинация, утилиты — без бизнес-логики
├── identity/          пользователи, регистрация, вход, токены, профили
│   ├── api/           публичные интерфейсы и DTO модуля (UserApi, CurrentUser)
│   ├── web/           контроллеры
│   ├── domain/        сущности, сервисы
│   └── persistence/   репозитории
├── groups/            группы, членство, роли, приглашения
├── catalog/           категории (шаблон и категории групп)
├── reviews/           рекомендации, теги, публикации в группы, правила шаринга
├── search/            поиск и фильтры (читающая модель)
├── moderation/        жалобы, блокировки
└── mail/              отправка писем, outbox
```
Правила: модуль виден другим только через `api`; зависимости проверяются Spring Modulith/ArchUnit. Общение «событийное» там, где это уместно (например, `UserDeleted`, `MemberLeftGroup` → `reviews` снимает публикации).

Слои внутри модуля: `web → service (use case) → domain/repository`. Контроллеры тонкие, бизнес-логика — в сервисах, права — в сервисах через явный `AccessPolicy`.

## Авторизация и права

- Аутентификация: JWT access → `SecurityContext`. Идентификатор пользователя — UUID.
- Роли в группе проверяются в сервисах: `GroupAccess.requireMember(userId, groupId)`, `requireAdmin(...)`. Нет проверки — нет доступа (404 для чужих ресурсов, чтобы не раскрывать существование).
- Права членов группы определяются полем `is_admin` роли; кастомные роли в первой версии — «меточные» (отображаемое имя), права — admin/не admin (YAGNI; расширение до набора permissions — потом).

## API (черновик, `/api/v1`)

Аутентификация:
- `POST /auth/register`, `POST /auth/confirm-email`, `POST /auth/resend-confirmation`
- `POST /auth/login`, `POST /auth/refresh`, `POST /auth/logout`
- `POST /auth/password/forgot`, `POST /auth/password/reset`

Пользователь:
- `GET /me`, `PATCH /me` (displayName, bio, username), `POST /me/password`, `DELETE /me`

Группы:
- `POST /groups`, `GET /groups` (мои), `GET/PATCH/DELETE /groups/{id}`
- `GET /groups/{id}/members`, `DELETE /groups/{id}/members/{userId}`, `POST /groups/{id}/leave` (параметр `deleteMyReviews`)
- `PUT /groups/{id}/members/{userId}/role`
- `GET/POST/PATCH/DELETE /groups/{id}/roles`
- `POST /groups/{id}/invitations`, `GET /groups/{id}/invitations`, `DELETE /groups/{id}/invitations/{id}`
- `POST /invitations/accept` (token + правила шаринга)

Категории:
- `GET /groups/{id}/categories` (дерево), `POST`, `PATCH`, `DELETE /groups/{id}/categories/{cid}` (`?confirm=true`; ответ 409 с описанием последствий, если подтверждения нет — основа для предупреждения в UI; также `GET .../categories/{cid}/delete-impact`)

Рекомендации:
- `POST /reviews`, `GET/PATCH/DELETE /reviews/{id}`, `GET /me/reviews`
- `PUT /reviews/{id}/groups` (набор публикаций: группа + категория)
- `GET /groups/{id}/reviews?categoryIds=&tags=&authorId=&q=&cursor=&limit=`
- `GET /reviews?groupIds=...` — по нескольким своим группам
- `GET /groups/{id}/tags`

Модерация: `POST /reports`, `POST /users/{id}/block`.

Служебное: `/actuator/health`, `/actuator/prometheus` (закрыто от внешнего доступа).

### Соглашения API
- JSON, UTF-8; даты — ISO-8601 UTC; идентификаторы — UUID.
- Ошибки — RFC 9457 `application/problem+json` с машинным кодом ошибки (для локализации на клиенте).
- Пагинация — keyset (cursor), стабильная сортировка по `(created_at, id)`.
- Валидация входных данных (Bean Validation) на сервере, независимо от клиента.
- Идемпотентность создания через `Idempotency-Key` (опционально, этап 6).

## Ключевые сценарии и инварианты

1. **Регистрация:** валидация → создание неактивного пользователя → одноразовый код (хэш в БД, TTL, лимит попыток) → письмо → подтверждение активирует пользователя.
2. **Вход:** проверка активности/блокировки → Argon2id verify → выдача пары токенов. Ответ при ошибке одинаков для «нет пользователя» и «неверный пароль»; сравнение с фиктивным хэшем для выравнивания времени.
3. **Создание группы:** транзакция — группа, копия категорий из шаблона, членство создателя с ролью ADMIN.
4. **Инвариант «в группе ≥ 1 админ»:** проверка в сервисе внутри транзакции с блокировкой (`SELECT ... FOR UPDATE` по группе) при снятии роли/удалении/выходе.
5. **Удаление категории:** разрешено, если пуста, либо все публикации в ней (и её потомках) принадлежат инициатору; иначе — отказ (в соответствии с описанием). Предупреждение: `delete-impact` возвращает число публикаций и подкатегорий; UI показывает подтверждение. Удаляются публикации в группе (не сами рекомендации в других группах).
6. **Присоединение по приглашению:** валидация токена (хэш, TTL, лимит), добавление членства, применение правил шаринга (одной SQL-операцией `INSERT ... SELECT`), в одной транзакции.
7. **Выход из группы:** запрет для единственного админа; по флагу — удаление публикаций автора в группе.
8. **Удаление аккаунта:** soft delete + анонимизация, отзыв токенов, снятие членств (с проверкой инварианта админа), событие `UserDeleted`.

## Безопасность (реализация)
- Spring Security: stateless, CSRF отключён (нет cookie-сессий), CORS — не нужен для мобильного клиента (закрыт по умолчанию), заголовки безопасности.
- Токены: refresh хранится хэшем, привязан к семейству и устройству, ротация, отзыв.
- Rate limit: `/auth/*` — строго (по IP и по email/логину); общий лимит на пользователя.
- Ограничения размера: тело запроса, длина полей (title ≤ 200, description ≤ 5000 — на согласование), число тегов ≤ 10, длина тега ≤ 30.
- Логи без паролей, токенов и email в открытом виде (маскирование).
- Секреты — из окружения; ключи JWT ротируются (`kid`).
- Зависимости сканируются в CI.

## Конфигурация и контейнер
- Профили `local`, `test`, `prod`. Все настройки через `PODGON_*` переменные окружения.
- `-Dfile.encoding=UTF-8`, `server.servlet.encoding.charset=UTF-8` (force), JDBC `UTF8`.
- Dockerfile: multi-stage (JDK build → JRE runtime), non-root, `HEALTHCHECK`, graceful shutdown.
- Stateless: нет локальных файлов/сессий; rate limit при нескольких инстансах переезжает в Redis.
- Планировщик (очистка просроченных кодов/приглашений/токенов): `@Scheduled` + ShedLock при нескольких инстансах.

## Тестирование
- Unit: доменная логика, правила шаринга, парсер тегов.
- Интеграционные: `@SpringBootTest` + Testcontainers (Postgres), проверка прав «чужая группа», миграции.
- Контрактные: сгенерированный клиент/валидация ответов по OpenAPI.
- Архитектурные: Modulith/ArchUnit.
- Безопасность: тесты на rate limit, блокировку, отзыв токенов.

## Предложения и обоснования
- **JPA vs jOOQ:** JPA быстрее для CRUD и привычен; сложные фильтры/поиск — через `JdbcClient`/Specification с параметрами. Если запросы с динамическими фильтрами станут громоздкими — рассмотреть jOOQ (типобезопасный SQL, тоже защищает от инъекций).
- **Redis не вводим на старте** (YAGNI). Появится при нескольких инстансах (rate limit, кэш).
- **Брокер сообщений не нужен:** события внутри процесса, outbox в БД для писем.
- **Virtual threads** включить после проверки совместимости драйверов (пул JDBC ограничен).
