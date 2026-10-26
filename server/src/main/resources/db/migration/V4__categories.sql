-- Категории: базовый набор (шаблон) и категории групп (см. docs/db.md)

create table category_templates (
    id         uuid primary key,
    parent_id  uuid references category_templates (id),
    sort_order int not null
);

create table category_template_translations (
    template_id uuid         not null references category_templates (id) on delete cascade,
    locale      varchar(8)   not null,
    name        varchar(100) not null,
    description varchar(500) not null default '',
    primary key (template_id, locale)
);

create table categories (
    id          uuid primary key,
    group_id    uuid         not null references groups (id) on delete cascade,
    parent_id   uuid,
    template_id uuid references category_templates (id),
    name        varchar(100) not null,
    description varchar(500) not null default '',
    sort_order  int          not null default 0,
    created_by  uuid         not null references users (id),
    created_at  timestamptz  not null,
    -- нужен как цель составных внешних ключей (родитель и, на этапе 6, публикации)
    constraint categories_id_group_uq unique (id, group_id),
    -- родитель — в той же группе; удаление категории удаляет всё поддерево
    constraint categories_parent_fk foreign key (parent_id, group_id)
        references categories (id, group_id) on delete cascade,
    constraint categories_not_self_parent check (parent_id is distinct from id)
);
-- имена уникальны среди соседей без учёта регистра (корень — отдельный индекс из-за NULL)
create unique index categories_root_name_uq on categories (group_id, lower(name)) where parent_id is null;
create unique index categories_child_name_uq on categories (group_id, parent_id, lower(name)) where parent_id is not null;
create index categories_parent_idx on categories (group_id, parent_id);

insert into category_templates (id, parent_id, sort_order)
values
    ('00000000-0000-4000-8000-000000000001', null, 1),
    ('00000000-0000-4000-8000-000000000002', null, 2),
    ('00000000-0000-4000-8000-000000000003', null, 3),
    ('00000000-0000-4000-8000-000000000004', null, 4),
    ('00000000-0000-4000-8000-000000000005', null, 5),
    ('00000000-0000-4000-8000-000000000006', '00000000-0000-4000-8000-000000000005', 6),
    ('00000000-0000-4000-8000-000000000007', '00000000-0000-4000-8000-000000000005', 7),
    ('00000000-0000-4000-8000-000000000008', null, 8),
    ('00000000-0000-4000-8000-000000000009', null, 9),
    ('00000000-0000-4000-8000-000000000010', null, 10);

insert into category_template_translations (template_id, locale, name)
values
    ('00000000-0000-4000-8000-000000000001', 'ru', 'Книги'),
    ('00000000-0000-4000-8000-000000000001', 'en', 'Books'),
    ('00000000-0000-4000-8000-000000000002', 'ru', 'Фильмы и сериалы'),
    ('00000000-0000-4000-8000-000000000002', 'en', 'Movies and TV'),
    ('00000000-0000-4000-8000-000000000003', 'ru', 'Музыка'),
    ('00000000-0000-4000-8000-000000000003', 'en', 'Music'),
    ('00000000-0000-4000-8000-000000000004', 'ru', 'Рестораны и кафе'),
    ('00000000-0000-4000-8000-000000000004', 'en', 'Restaurants and cafes'),
    ('00000000-0000-4000-8000-000000000005', 'ru', 'Путешествия'),
    ('00000000-0000-4000-8000-000000000005', 'en', 'Travel'),
    ('00000000-0000-4000-8000-000000000006', 'ru', 'Страны'),
    ('00000000-0000-4000-8000-000000000006', 'en', 'Countries'),
    ('00000000-0000-4000-8000-000000000007', 'ru', 'Отели'),
    ('00000000-0000-4000-8000-000000000007', 'en', 'Hotels'),
    ('00000000-0000-4000-8000-000000000008', 'ru', 'Услуги'),
    ('00000000-0000-4000-8000-000000000008', 'en', 'Services'),
    ('00000000-0000-4000-8000-000000000009', 'ru', 'Товары'),
    ('00000000-0000-4000-8000-000000000009', 'en', 'Products'),
    ('00000000-0000-4000-8000-000000000010', 'ru', 'Игры'),
    ('00000000-0000-4000-8000-000000000010', 'en', 'Games');
