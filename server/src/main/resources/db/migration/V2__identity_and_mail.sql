-- Пользователи, одноразовые коды входа, refresh-токены (сессии устройств), очередь писем (см. docs/db.md)

create table users (
    id           uuid primary key,
    email        citext       not null,
    username     citext       not null,
    display_name varchar(60),
    bio          varchar(120) not null default '',
    status       varchar(16)  not null,
    locale       varchar(5)   not null default 'ru',
    created_at   timestamptz  not null,
    updated_at   timestamptz  not null,
    deleted_at   timestamptz,
    constraint users_status_check check (status in ('ACTIVE', 'BLOCKED', 'DELETED', 'SYSTEM')),
    constraint users_locale_check check (locale in ('ru', 'en')),
    -- анонимизированные пользователи получают имя deleted_<id>, которое длиннее обычного
    constraint users_username_check check (status in ('DELETED', 'SYSTEM') or username ~ '^[A-Za-z0-9_.]{3,30}$')
);

-- адрес освобождается после удаления аккаунта (email заменяется на deleted+<id>@invalid)
create unique index users_email_uq on users (email) where deleted_at is null;
create unique index users_username_uq on users (username);

-- служебный пользователь, которому переходят отзывы удалённых аккаунтов
insert into users (id, email, username, display_name, bio, status, locale, created_at, updated_at)
values ('00000000-0000-0000-0000-000000000001', 'system+deleted-user@invalid', 'deleted_user',
        'Удалённый пользователь', '', 'SYSTEM', 'ru', now(), now());

create table email_codes (
    id          uuid primary key,
    email       citext      not null,
    code_hmac   bytea       not null,
    created_at  timestamptz not null,
    expires_at  timestamptz not null,
    attempts    int         not null default 0,
    consumed_at timestamptz,
    ip          varchar(45)
);
create index email_codes_email_idx on email_codes (email, created_at desc);
create index email_codes_ip_idx on email_codes (ip, created_at desc);

-- одна строка = одно выданное refresh-значение; токены одной сессии (устройства) объединены family_id
create table refresh_tokens (
    id                 uuid primary key,
    user_id            uuid         not null references users (id),
    family_id          uuid         not null,
    token_hash         bytea        not null,
    device_info        varchar(200),
    session_started_at timestamptz  not null,
    created_at         timestamptz  not null,
    expires_at         timestamptz  not null,
    rotated_at         timestamptz,
    revoked_at         timestamptz
);
create unique index refresh_tokens_hash_uq on refresh_tokens (token_hash);
create index refresh_tokens_user_idx on refresh_tokens (user_id);
create index refresh_tokens_family_idx on refresh_tokens (family_id);

create table mail_outbox (
    id              uuid primary key,
    to_address      varchar(254) not null,
    template        varchar(40)  not null,
    locale          varchar(5)   not null,
    -- параметры письма (в том числе одноразовый код): очищаются после успешной отправки
    payload         jsonb        not null,
    status          varchar(10)  not null,
    attempts        int          not null default 0,
    next_attempt_at timestamptz  not null,
    created_at      timestamptz  not null,
    sent_at         timestamptz,
    constraint mail_outbox_status_check check (status in ('PENDING', 'SENT', 'FAILED'))
);
create index mail_outbox_due_idx on mail_outbox (next_attempt_at) where status = 'PENDING';
