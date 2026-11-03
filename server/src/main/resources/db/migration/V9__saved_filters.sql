-- Этап 8.2.2 (И4, И30): сохранённые фильтры панели и закреплённые категории — одна таблица на пользователя.
-- Закреплённая категория существует, только пока закреплена (открепление удаляет запись).
create table saved_filters (
    id          uuid primary key,
    user_id     uuid        not null references users (id) on delete cascade,
    kind        varchar(16) not null,
    name        varchar(60),
    category_id uuid references categories (id),
    -- версионированное определение фильтра (FilterDefinition, поле v); группа не сохраняется
    definition  jsonb,
    in_feed     boolean     not null default false,
    -- порядок среди закреплённых своего вида; у незакреплённых — null
    position    int,
    created_at  timestamptz not null,
    updated_at  timestamptz not null,
    constraint saved_filters_kind_check check (kind in ('FILTER', 'CATEGORY')),
    constraint saved_filters_shape check (
        (kind = 'FILTER' and name is not null and definition is not null and category_id is null)
            or (kind = 'CATEGORY' and category_id is not null and name is null and definition is null and in_feed)),
    constraint saved_filters_position check ((position is null) = (not in_feed))
);
create index saved_filters_user_idx on saved_filters (user_id);
create unique index saved_filters_category_uq on saved_filters (user_id, category_id) where kind = 'CATEGORY';
