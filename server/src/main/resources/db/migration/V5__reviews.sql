-- Рекомендации, публикации в группы, теги (см. docs/db.md)

create table reviews (
    id            uuid primary key,
    author_id     uuid         not null references users (id),
    title         varchar(200) not null,
    description   text         not null default '',
    rating        smallint     not null,
    -- полнотекстовый поиск (этап 7): название весит больше описания
    search_vector tsvector generated always as (
        setweight(to_tsvector('russian', title), 'A') || setweight(to_tsvector('russian', description), 'B')
    ) stored,
    created_at    timestamptz  not null,
    updated_at    timestamptz  not null,
    constraint reviews_rating_check check (rating between 1 and 10),
    constraint reviews_description_check check (char_length(description) <= 5000)
);
create index reviews_author_idx on reviews (author_id, created_at desc, id);
create index reviews_search_idx on reviews using gin (search_vector);

-- публикация рекомендации в группе; категория принадлежит этой же группе и удаляется вместе с категорией
create table review_groups (
    review_id   uuid        not null references reviews (id) on delete cascade,
    group_id    uuid        not null references groups (id) on delete cascade,
    category_id uuid        not null,
    shared_at   timestamptz not null,
    primary key (review_id, group_id),
    constraint review_groups_category_fk foreign key (category_id, group_id)
        references categories (id, group_id) on delete cascade
);
create index review_groups_feed_idx on review_groups (group_id, shared_at desc, review_id);
create index review_groups_category_idx on review_groups (group_id, category_id);
create index review_groups_review_idx on review_groups (review_id);

create table tags (
    id   bigint generated always as identity primary key,
    -- формат (буквы, цифры, «_», «-») проверяет приложение: класс [[:alnum:]] зависит от локали БД
    name citext not null unique,
    constraint tags_name_check check (char_length(name) between 1 and 30)
);

create table review_tags (
    review_id uuid   not null references reviews (id) on delete cascade,
    tag_id    bigint not null references tags (id),
    primary key (review_id, tag_id)
);
create index review_tags_tag_idx on review_tags (tag_id, review_id);
