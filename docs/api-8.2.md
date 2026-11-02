# Черновик API этапа 8.2 (редизайн)

Изменения контракта `api/openapi.yaml` (сейчас 0.8.0) для этапа 8.2. Основание — `docs/design.md`
(«Реализация: объём работ»), ADR-011 и `docs/categories.md`. Черновик 2026-11-02: при переносе в `openapi.yaml`
имена и детали можно уточнять, но смысл менять — только с записью решения здесь.

Общие правила остаются: JSON, `bearerAuth`, ошибки — Problem Details с `code`, ответы 401/426/429 на всех
защищённых методах, курсоры непрозрачные, `limit` 1–50.

## 0. Совместимость и версия
- Контракт меняется **несовместимо**: категории групп и ручные назначения при вступлении уходят, категория
  переезжает на рекомендацию. Выпущенных клиентов нет, поэтому старые поля не сохраняем.
- Версия — **0.9.0**. Проверка совместимости в CI (решение владельца 2026-11-02, уже в `.github/workflows/ci.yml`):
  база сравнения для `oasdiff` — последний тег релиза `v*`; до первого релиза задание только выводит
  несовместимости и не роняет сборку, с первого тега снова блокирует (`--fail-on ERR`).
- Устаревших методов нет, всё удаляемое удаляется сразу (вместе с кодом сервера и клиента).

## 1. Категории (ADR-011)

### Новое
`GET /categories?locale=ru|en` — весь набор (без авторизации не нужен, но метод под `bearerAuth`, как остальные).
- Ответ `200 CategorySet`, заголовок `ETag` (версия набора). Запрос с `If-None-Match` той же версии — `304`.
- Клиент кэширует набор в Room вместе с версией; при смене языка — новый запрос.

```yaml
CategorySet:
  required: [version, items]
  properties:
    version: { type: string, description: Версия набора, совпадает с ETag. }
    items:
      type: array
      description: Корни и подкатегории вперемешку, в порядке sortOrder; клиент строит дерево по parentId.
      items: { $ref: Category }
Category:
  required: [id, key, parentId, name, sortOrder, system]
  properties:
    id: { type: string, format: uuid }
    key: { type: string, description: 'Стабильный ключ из docs/categories.md (food.coffee).' }
    parentId: { type: string, format: uuid, nullable: true }
    name: { type: string, description: Название на запрошенном языке. }
    sortOrder: { type: integer }
    system: { type: boolean, description: '«Без категории» (key = none).' }
    archived: { type: boolean, description: Скрыта из выбора; используется только у старых рекомендаций. }
```
Скрытые (`archived`) категории тоже входят в ответ с полем `archived: true` (нужны для подписи старых
рекомендаций); в форме и в панели фильтров клиент их не предлагает.

### Удаляется
- `/groups/{groupId}/categories`, `/groups/{groupId}/categories/{categoryId}`, `.../parent`, `.../delete-impact`.
- Схемы `CategoryInfo`, `CreateCategoryRequest`, `UpdateCategoryRequest`, `MoveCategoryRequest`, `CategoryDeleteImpact`.
- Коды ошибок `CATEGORY_NAME_TAKEN`, `CATEGORY_CYCLE`, `CATEGORY_TOO_DEEP`, `CATEGORY_HAS_FOREIGN_REVIEWS`
  (список расширяемый — из enum убираем, клиент удаляет тексты).

## 2. Рекомендации

### Создание и правка
```yaml
CreateReviewRequest:
  required: [title, rating, categoryId]
  properties:
    title: { type: string, maxLength: 200 }
    description: { type: string, maxLength: 5000 }
    rating: { type: integer, minimum: 1, maximum: 10 }
    categoryId: { type: string, format: uuid }
    tags: { type: array, maxItems: 10, items: { type: string, maxLength: 31 } }
    groupIds:
      type: array
      maxItems: 50
      description: Обычные группы, где я участник. Пусто или нет поля — рекомендация личная (замок закрыт).
      items: { type: string, format: uuid }
UpdateReviewRequest:      # как сейчас + categoryId
  properties:
    categoryId: { type: string, format: uuid, nullable: true }
SetPublicationsRequest:   # PUT /reviews/{id}/groups
  required: [groupIds]
  properties:
    groupIds: { type: array, maxItems: 50, items: { type: string, format: uuid } }
```
- Оценка и категория обязательны. «Без категории» (`system`) — только у личной: в запросе с непустым `groupIds`
  и при открытии замка (`PUT .../groups` с непустым набором) — `400 VALIDATION_FAILED`, поле `categoryId`.
- `groupIds` с личной или избранной группой — `400 VALIDATION_FAILED` (поле `groupIds`); чужая группа — `404`.
- `PUT /reviews/{id}/groups` с пустым набором делает рекомендацию личной: снимает все публикации (клиент
  подтверждает заранее) и публикует в личную группу; с непустым — снимает публикацию в личной. Снятие
  публикаций чистит чужое избранное (Ф1) в той же транзакции.
- Схемы `PublicationRequest` удаляется (категории на публикации больше нет).

### Ответы
```yaml
ReviewDetails:            # изменения
  required: [..., categoryId, personal, publications]
  properties:
    categoryId: { type: string, format: uuid }
    personal: { type: boolean, description: 'Личная: опубликована только в личной группе.' }
    publications:
      type: array
      description: Только обычные группы; автору — все, остальным — где они участники.
      items: { $ref: PublicationInfo }
PublicationInfo:
  required: [groupId, sharedAt]       # categoryId удаляется
ReviewCard:
  required: [id, title, excerpt, rating, tags, author, categoryId, createdAt, sharedAt, groupIds]
  properties:
    categoryId: { type: string, format: uuid }        # больше не nullable
    createdAt: { type: string, format: date-time }     # дата создания (И25)
    sharedAt:
      type: string
      format: date-time
      description: Самая свежая публикация в области запроса (в группе вкладки; на «Все» — по всем моим группам).
    groupIds:
      type: array
      description: Группы публикации, где я участник (на «Все» — для подписи; в группе — одна).
      items: { type: string, format: uuid }
    # groupId удаляется — заменён groupIds
```

### Ленты и поиск
- `GET /reviews` («Все»): каждая рекомендация **один раз** (И15), порядок и курсор — по `sharedAt` самой свежей
  публикации; системные группы не входят (Л2). Параметр `groupIds` остаётся (экран поиска по группам).
- `GET /groups/{groupId}/reviews` работает и для личной и избранной группы (их id — в профиле, см. п. 4).
- `GET /me/reviews` — «Мои рекомендации» в профиле: **групповые**, где я автор (И21), новые сверху; личные —
  через ленту личной группы.
- Порядок без `q` — новые сверху (И5), с `q` — по релевантности (как сейчас).

Новые параметры фильтра (общие для `GET /reviews`, `GET /groups/{id}/reviews`, оглавления и счётчиков):
```yaml
MinRating: { name: minRating, in: query, schema: { type: integer, minimum: 1, maximum: 10 } }
MaxRating: { name: maxRating, in: query, schema: { type: integer, minimum: 1, maximum: 10 } }
AuthorIds:                 # несколько авторов в панели; заменяет authorId (удаляется)
  name: authorIds
  in: query
  style: form
  explode: false
  schema: { type: array, maxItems: 20, items: { type: string, format: uuid } }
Section:                   # записи одной секции ленты (п. 3)
  name: section
  in: query
  description: 'category:<categoryId> или filter:<savedFilterId>; накладывается на остальные параметры (пересечение).'
  schema: { type: string, maxLength: 60 }
```
- `minRating > maxRating` — `400`. Категории в `categoryIds` — корни включают подкатегории (как сейчас).
- `section` вместе с `q` — `400` (в секциях поиска нет, результаты поиска не группируются).

### Счётчики для панели фильтров и подсказок
`GET /reviews/facets` и `GET /groups/{groupId}/facets` — те же параметры фильтра (без курсора), ответ:
```yaml
Facets:
  required: [categories, tags, authors]
  properties:
    categories:
      type: array
      description: Категории, которые есть в выборке (корни и подкатегории), с числом рекомендаций.
      items: { required: [categoryId, count], properties: { categoryId: uuid, count: integer } }
    tags:
      type: array
      maxItems: 100
      description: Самые частые первыми — они же «Популярные теги» в подсказках поиска (И9).
      items: { $ref: TagCount }
    authors:
      type: array
      description: Авторы выборки (в личной группе — пусто).
      items: { required: [author, count], properties: { author: ReviewAuthor, count: integer } }
```
- `GET /groups/{groupId}/tags?prefix=` остаётся для автодополнения в форме; на «Все» и в форме без группы —
  новый `GET /me/tags?prefix=` (теги моих групп и моего личного, только видимые мне).
- Недавние запросы (И9) хранятся только на устройстве.

## 3. Секции ленты и сохранённые фильтры (И4, И29, И30)

### Оглавление
`GET /feed?groupId=<uuid>` (без `groupId` — «Все»; допустимы id личной и избранной группы) + параметры фильтра
панели (кроме `q`, `section`, `cursor`) → `200 FeedToc`:
```yaml
FeedToc:
  required: [sections]
  properties:
    sections:
      type: array
      description: |
        Уже в порядке показа (И29): закреплённые фильтры → закреплённые категории (обе группы в порядке
        пользователя) → остальные корневые категории по lastSharedAt, «Без категории» — последней.
        Пустые секции не возвращаются.
      items: { $ref: FeedSection }
FeedSection:
  required: [section, kind, pinned, count, lastSharedAt]
  properties:
    section: { type: string, description: 'Значение для параметра section: category:<id> или filter:<id>.' }
    kind: { type: string, enum: [FILTER, CATEGORY] }
    pinned: { type: boolean }
    categoryId: { type: string, format: uuid, nullable: true, description: Для CATEGORY. }
    filterId: { type: string, format: uuid, nullable: true, description: Для FILTER и закреплённой CATEGORY. }
    name: { type: string, nullable: true, description: 'Название сохранённого фильтра (для категории — из набора на клиенте).' }
    count: { type: integer }
    lastSharedAt: { type: string, format: date-time }
```
Записи секции — существующие ленты с параметром `section` (п. 2), курсор свой у каждой секции.

### Сохранённые фильтры и закрепления
Одна таблица на пользователя (И30): сохранённый фильтр (`FILTER`) или закреплённая категория (`CATEGORY`).
Закреплённая категория существует, только пока закреплена; открепление удаляет запись.
```yaml
SavedFilter:
  required: [id, kind, inFeed, position]
  properties:
    id: { type: string, format: uuid }
    kind: { type: string, enum: [FILTER, CATEGORY] }
    name: { type: string, maxLength: 60, nullable: true, description: Только для FILTER. }
    categoryId: { type: string, format: uuid, nullable: true, description: Только для CATEGORY. }
    definition: { $ref: FilterDefinition, nullable: true, description: Только для FILTER. }
    inFeed: { type: boolean, description: 'Закреплён секцией ленты (булавка). У CATEGORY всегда true.' }
    position: { type: integer, nullable: true, description: Порядок среди закреплённых своего вида. }
FilterDefinition:
  description: Версионированный набор; группа не сохраняется. Удалённые теги и чужие авторы игнорируются.
  required: [v]
  properties:
    v: { type: integer, enum: [1] }
    q: { type: string, maxLength: 100, nullable: true }
    categoryIds: { type: array, maxItems: 20, items: uuid }
    tags: { type: array, maxItems: 10, items: string }
    tagMode: { type: string, enum: [ALL, ANY] }
    authorIds: { type: array, maxItems: 20, items: uuid }
    minRating: { type: integer, nullable: true }
    maxRating: { type: integer, nullable: true }
```
Методы (`tags: [filters]`):
- `GET /me/filters` → `SavedFilter[]`: сначала закреплённые по виду и `position`, затем незакреплённые фильтры
  по имени (алфавит, `ru` collation).
- `POST /me/filters` — `{kind: FILTER, name, definition, inFeed}` или `{kind: CATEGORY, categoryId}` → `201 SavedFilter`.
  Повторное закрепление той же категории — `409 ALREADY_PINNED` (новый код). Лимит — 50 записей, `409 LIMIT_EXCEEDED`.
- `PATCH /me/filters/{id}` — `name`, `definition`, `inFeed` (для FILTER) → `200 SavedFilter`.
- `DELETE /me/filters/{id}` → `204` (удаление фильтра или открепление категории).
- `PUT /me/filters/order` — `{filterIds: [...], categoryIds: [...]}`: полный порядок закреплённых фильтров и
  закреплённых категорий (id записей); состав должен совпадать с закреплёнными, иначе `400` → `200 SavedFilter[]`.

Секция фильтра с `q` в определении — строка поиска в секции не применяется (порядок по дате), `q` учитывается
только при применении фильтра в панели.

## 4. Системные группы
```yaml
UserProfile:              # + два поля
  required: [..., personalGroupId, favoritesGroupId]
  properties:
    personalGroupId: { type: string, format: uuid }
    favoritesGroupId: { type: string, format: uuid }
GroupSummary:             # + счётчик для списка групп и страницы группы
  required: [..., reviewCount]
  properties:
    reviewCount: { type: integer, description: Число рекомендаций, опубликованных в группе. }
```
- `GET /groups` системные группы не возвращает. Для системной группы работают только ленты, оглавление и
  счётчики; управление (участники, роли, приглашения, выход, удаление, `PATCH`) — `409 SYSTEM_GROUP` (новый код).
- Удаление группы `RETURN_TO_PERSONAL` переносит рекомендации, бывшие только в ней, в личную группу автора.
- Удаление аккаунта удаляет обе системные группы и личные рекомендации (Л1); `AccountDeleteImpact.reviews`
  считает и личные — без изменений схемы.
- Избранное: модель и чистка (Ф1, Ф2) — на этом этапе; методы `PUT/DELETE /me/favorites/{reviewId}` и сердечко — backlog.

## 5. Вступление в группу
```yaml
ShareRule:
  properties:
    categoryIds: { description: 'Категории набора (корни включают подкатегории).' }
    # withSubcategories удаляется: в наборе два уровня, корень всегда включает подкатегории
SharePreview:
  required: [total, items]
  properties:
    total: { type: integer }
    items:
      type: array
      maxItems: 100
      description: Первые записи для раскрывающегося списка «Будет опубликовано».
      items: { required: [reviewId, title, categoryId], properties: { reviewId: uuid, title: string, categoryId: uuid } }
AcceptInvitationRequest:
  required: [token]
  properties:
    token: { type: string, maxLength: 200 }
    share: { $ref: ShareRule }
    # assignments удаляется
```
- Удаляются `UnmatchedReview`, `ShareAssignment`, поля `matched`, `unmatched`, `categories`.
- Правило берёт только мои групповые рекомендации (личные не переносятся, Л1).

## 6. Новые и удалённые коды ошибок
- Новые: `SYSTEM_GROUP`, `ALREADY_PINNED`.
- Удалённые: `CATEGORY_NAME_TAKEN`, `CATEGORY_CYCLE`, `CATEGORY_TOO_DEEP`, `CATEGORY_HAS_FOREIGN_REVIEWS`.

## 7. Что не входит
Методы избранного, загрузка аватаров и фото, уведомления и подписки на фильтры, список входящих приглашений,
ссылки «Поделиться» — backlog (`docs/plan.md`).
