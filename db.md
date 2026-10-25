# База данных

PostgreSQL 16+, кодировка `UTF8`, `LC_COLLATE`/`LC_CTYPE` — `ru_RU.UTF-8` либо ICU-коллация (`und-x-icu`); все серверные и клиентские соединения в UTF-8. Часовой пояс хранения — UTC (`timestamptz`).
Миграции — Flyway (`V<номер>__<описание>.sql`), правило: применённые миграции не редактируются.
Расширения: `citext`, `pg_trgm`, `pgcrypto` (при необходимости `gen_random_uuid()` встроен с PG13+).

Соглашения: имена `snake_case`, таблицы во множественном числе, PK — `uuid` (`gen_random_uuid()`; для хронологической локальности можно UUIDv7, генерируемый приложением), у каждой таблицы `created_at timestamptz not null default now()`.

## ER-диаграмма (логическая)

```
users ──< memberships >── groups ──< categories (self: parent_id)
  │            │              │              ▲
  │            └── group_roles┘              │
  │                                          │
  └──< reviews ──< review_tags >── tags      │
          │                                  │
          └──< review_groups (review, group, category) ┘
groups ──< invitations
users ──< refresh_tokens, reports;  email_codes (по email, без FK)
category_templates (self: parent_id)  — базовый набор
```

## Таблицы

### users
| Поле | Тип | Ограничения |
|---|---|---|
| id | uuid PK | |
| email | citext | not null, unique (частичный уникальный индекс по `deleted_at is null`) |
| username | citext | not null, unique, `CHECK (username ~ '^[a-z0-9_.]{3,30}$')` |
| display_name | varchar(60) | |
| bio | varchar(120) | |
| status | varchar(16) | `ACTIVE`, `BLOCKED`, `DELETED`, `SYSTEM` (один служебный пользователь «Удалённый пользователь», фиксированный `id`, создаётся миграцией) |
| locale | varchar(5) | `ru` или `en` |
| created_at, updated_at, deleted_at | timestamptz | мягкое удаление |

При удалении: `email` заменяется на `deleted+<id>@invalid` (освобождение адреса), `username` на `deleted_<id>`, `display_name`, `bio` очищаются.

Роль «администратор группы» хранится не в users, а в членстве (роль зависит от группы). Глобальной роли в описании нет; если понадобится глобальный администратор системы (модерация) — отдельное поле `system_role`.

### groups
`id`, `name varchar(100) not null`, `description`, `created_by uuid → users`, `created_at`, `deleted_at`.

### group_roles
| Поле | Тип | Примечание |
|---|---|---|
| id | uuid PK | |
| group_id | uuid → groups | on delete cascade |
| code | varchar(32) | уникален в пределах группы: `unique(group_id, code)` |
| title | varchar(60) | отображаемое имя роли |
| description | varchar(255) | |
| is_admin | boolean | признак администратора |
| is_system | boolean | системные роли `ADMIN` и `MEMBER` (удалять/редактировать код нельзя) |

Системные роли создаются вместе с группой. Роль «базовый пользователь (без роли)» в БД представлена системной ролью `MEMBER` (чтобы `role_id` был `NOT NULL` и не обрабатывать null); в UI её название не показывается — по аналогии с Telegram. Это отступление от «без роли» ради простоты и целостности (обоснованное).

### memberships (сводная «пользователи ↔ группы»)
`group_id`, `user_id`, `role_id → group_roles`, `joined_at`; PK `(group_id, user_id)`; индекс по `user_id`.
Консистентность «роль принадлежит той же группе»: составной FK `(role_id, group_id) → group_roles (id, group_id)` (в `group_roles` уникальный `(id, group_id)`).
Инвариант «≥ 1 админ в группе» — проверяется в сервисе под блокировкой; страховка в БД — constraint trigger, откладываемый до конца транзакции.

### invitations
`id`, `group_id`, `created_by`, `email citext null` (адресное приглашение), `token_hash` (уникальный; токен выдаётся один раз), `expires_at`, `max_uses`, `used_count`, `revoked_at`, `created_at`.

### category_templates (базовый набор)
`id`, `parent_id → category_templates`, `sort_order`; названия и описания — в `category_template_translations(template_id, locale, name, description)` (`ru`, `en`). При копировании в группу берётся язык создателя группы; дальше названия — пользовательский текст группы. Заполняется seed-миграцией (например: Книги, Фильмы, Рестораны, Путешествия → Страны, Услуги…). Базовый набор редактируется только миграциями/администратором системы.

### categories
| Поле | Тип | Примечание |
|---|---|---|
| id | uuid PK | |
| group_id | uuid → groups | on delete cascade; `unique (id, group_id)` |
| parent_id | uuid null | составной FK `(parent_id, group_id) → categories (id, group_id)` — родитель в той же группе |
| template_id | uuid null → category_templates | откуда скопирована; помогает сопоставлять категории между группами |
| name | varchar(100) | `unique (group_id, parent_id, lower(name))` (с учётом `NULL` parent — через `coalesce`/два частичных индекса) |
| description | varchar(500) | |
| created_by | uuid → users | |
| created_at | timestamptz | |

Иерархия: adjacency list (`parent_id`) + рекурсивные CTE. Выбор обоснован: деревья небольшие (десятки–сотни узлов на группу), изменения редки; материализованные пути/closure table усложняют без выгоды. Для фильтра «категория с потомками» используется `WITH RECURSIVE`; при необходимости — добавить колонку `path ltree`. Циклы предотвращаются проверкой в сервисе (и триггером при желании).
Глубина — ограничена (≤ 5, на согласование).

### reviews (рекомендации)
| Поле | Тип | Примечание |
|---|---|---|
| id | uuid PK | |
| author_id | uuid → users | |
| title | varchar(200) | not null |
| description | text | ограничение длины на уровне приложения и `CHECK (length(description) <= 5000)` |
| rating | smallint | `CHECK (rating BETWEEN 1 AND 10)` |
| search_vector | tsvector | генерируемая колонка: `to_tsvector('russian', title || ' ' || description)` (веса: title — A) |
| created_at, updated_at | timestamptz | |

### review_groups (сводная «отзывы ↔ группы»)
`review_id → reviews (on delete cascade)`, `group_id → groups`, `category_id`, `shared_at`; PK `(review_id, group_id)`; составной FK `(category_id, group_id) → categories (id, group_id)` — категория принадлежит этой группе (подкатегория соответствует иерархии, так как категория — это узел дерева; «категория + подкатегория» = выбор узла, родители определяются деревом).
Индексы: `(group_id, shared_at desc, review_id)` — лента группы; `(group_id, category_id)` — фильтр по категории; `(review_id)`.

#### Категория рекомендации
В описании у рекомендации одна категория и подкатегория, а категории принадлежат группам. Рекомендация может быть в нескольких группах, поэтому категория хранится на связи `review_groups`. Рассмотренные альтернативы:
1. Категория в `reviews` + «глобальные» категории — противоречит «категории группы, админ добавляет/удаляет».
2. **Категория на связи (выбрано)** — соответствует модели, позволяет группам иметь разные деревья.
3. Копия рекомендации на каждую группу — дублирование, рассинхронизация, сложнее редактирование.
Недостаток выбранного варианта — при публикации нужно указывать категорию для каждой группы; смягчается подстановкой по `template_id`.

### tags и review_tags
`tags(id, name citext unique)` — имя без `#`, `CHECK (name ~ '^[[:alnum:]_-]{1,30}$')` (юникод-буквы допустимы); `review_tags(review_id, tag_id)` PK обоих, индекс `(tag_id, review_id)`.
Теги группы = `DISTINCT` теги публикаций группы (запрос по индексам). При необходимости — материализованная таблица `group_tags` со счётчиками.

### Служебные таблицы
- `refresh_tokens(id, user_id, family_id, token_hash unique, device_info, expires_at, rotated_at, revoked_at, created_at)` — ротация и обнаружение повторного использования.
- `email_codes(id, email citext, code_hmac, expires_at, attempts, last_sent_at, consumed_at, ip)` — одноразовые коды входа; пароля в системе нет. Записи удаляются по расписанию. Индекс `(email, created_at desc)`.
- `registrationToken` не хранится: это подписанный короткоживущий JWT.
- `mail_outbox(id, to_address, template, payload jsonb, status, attempts, next_attempt_at, created_at)`.
- `reports(id, reporter_id, target_type, target_id, reason, status, created_at)`; `user_blocks(blocker_id, blocked_id)` (этап 9).
- `flyway_schema_history` — Flyway.

## Поиск и фильтрация

- Полнотекстовый: `search_vector` + GIN-индекс, запрос `websearch_to_tsquery('russian', :q)`, ранжирование `ts_rank_cd`. Русская морфология обеспечивается словарём `russian` (snowball). Опционально — словарь `unaccent`.
- Нечёткий поиск по названию: GIN `title gin_trgm_ops` (опечатки, подстроки).
- Фильтры: категории (по набору узлов с потомками через рекурсивный CTE), теги (`ALL`/`ANY` — режим на согласование, по умолчанию `ALL`), автор. Все условия добавляются как параметры одного запроса к `review_groups` с ограничением по группам пользователя.
- Видимость: каждый запрос чтения содержит условие `review_groups.group_id IN (группы пользователя)`; для поиска по «всем моим группам» — подзапрос по `memberships`.

## Правила шаринга при вступлении (реализация)
Одна транзакция: `INSERT INTO review_groups (...) SELECT ... FROM reviews r JOIN review_groups src ... WHERE r.author_id = :user AND <условия>`. Вариативность (все / категории / подкатегории / теги / кроме...) строится в приложении из структуры правила; правило не хранится (разовое действие — открытый вопрос №5). Категория в новой группе подбирается по `template_id`. Записи, для которых соответствия нет (категория удалена в новой группе или создана вручную и шаблонной связи не имеет), в автоматический перенос не попадают: приложение возвращает их список (предпросмотр), и пользователь для каждой выбирает — назначить категорию вручную или не переносить. Выбранные назначения передаются в тот же запрос вступления, поэтому всё применяется одной транзакцией. Системной категории «Без категории» нет.

## Целостность и каскады
| Событие | Результат |
|---|---|
| Удаление категории | Удаляются `review_groups` в категории и потомках (после подтверждения); `reviews` остаются |
| Удаление группы | Каскад: роли, членства, категории, приглашения, публикации. Отзывы только этой группы по выбору администратора: возвращаются в личные либо удаляются (с подтверждением); отзывы, опубликованные и в других группах, остаются |
| Выход из группы | Удаление членства; по флагу — публикации автора в группе |
| Удаление отзыва | Каскад по `review_groups`, `review_tags` |
| Удаление аккаунта | Мягко: анонимизация `users`, удаление токенов, членств; отзывы с публикациями по умолчанию переводятся на системного пользователя (`author_id`), личные без публикаций удаляются; если пользователь выбрал «удалить все мои отзывы» — удаляются все его отзывы вместе с публикациями и тегами |
Отзывы без публикаций допустимы (личные, видны только автору).

## Безопасность БД
- Приложение подключается ролью `podgon_app` (DML на схему, без DDL); миграции — ролью `podgon_migrator`.
- Нет публичного доступа к порту БД; TLS-соединение.
- Только параметризованные запросы (защита от SQL-инъекций на уровне приложения); логирование медленных запросов.
- Бэкапы: ежедневные + PITR, регулярная проверка восстановления.
- Персональные данные: email и профиль в `users`; шифрование диска провайдером.
- Row Level Security — рассмотрено и **не включаем** на старте (усложняет пул соединений и миграции); защита реализована в приложении и тестах. Вернёмся при выделении сервисов.

## Индексы (стартовый набор)
- `users`: unique email (частичный), unique username.
- `memberships`: PK `(group_id, user_id)`, `(user_id)`.
- `categories`: `(group_id, parent_id)`.
- `review_groups`: см. выше.
- `reviews`: `(author_id, created_at desc)`, GIN(`search_vector`), GIN trgm(`title`).
- `refresh_tokens`: unique `token_hash`, `(user_id)`.

## Согласованность с остальными частями
Ограничения длин и форматов (username, displayName, bio, rating, теги) дублируются в OpenAPI-контракте, серверной валидации и клиентской форме; источник правды — контракт и `CHECK` в БД, расхождения ловятся тестами.
