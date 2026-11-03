-- Этап 8.3.5: уведомления в приложении (без push), их настройки и подписки на сохранённые фильтры.
create table notifications (
    id         uuid primary key,
    user_id    uuid        not null references users (id) on delete cascade,
    kind       varchar(32) not null,
    -- объект уведомления: приглашение (INVITATION) или рекомендация (FILTER_MATCH); одно уведомление на объект
    ref_id     uuid        not null,
    -- подписи для списка (название группы, рекомендации, фильтра), чтобы не читать чужие модули
    data       jsonb       not null default '{}',
    created_at timestamptz not null,
    read_at    timestamptz,
    constraint notifications_kind_check check (kind in ('INVITATION', 'FILTER_MATCH')),
    constraint notifications_once unique (user_id, kind, ref_id)
);
create index notifications_feed_idx on notifications (user_id, created_at desc, id desc);
create index notifications_unread_idx on notifications (user_id) where read_at is null;

-- Нет строки — всё включено.
create table notification_settings (
    user_id        uuid primary key references users (id) on delete cascade,
    invitations    boolean not null default true,
    filter_matches boolean not null default true
);

-- Подписка: уведомлять о новых рекомендациях, подходящих под сохранённый фильтр (только kind = 'FILTER').
alter table saved_filters add column subscribed boolean not null default false;
alter table saved_filters add constraint saved_filters_subscribed check (not subscribed or kind = 'FILTER');
create index saved_filters_subscribed_idx on saved_filters (user_id) where subscribed;
