-- Группы, роли, участники, приглашения (см. docs/db.md)

create table groups (
    id          uuid primary key,
    name        varchar(100) not null,
    description varchar(500) not null default '',
    created_by  uuid         not null references users (id),
    created_at  timestamptz  not null,
    updated_at  timestamptz  not null
);

create table group_roles (
    id          uuid primary key,
    group_id    uuid         not null references groups (id) on delete cascade,
    code        varchar(32)  not null,
    title       varchar(60)  not null,
    description varchar(255) not null default '',
    is_admin    boolean      not null,
    is_system   boolean      not null,
    created_at  timestamptz  not null,
    constraint group_roles_code_uq unique (group_id, code),
    -- нужен как цель составного внешнего ключа из memberships
    constraint group_roles_id_group_uq unique (id, group_id),
    -- системные роли — ровно ADMIN (с правами админа) и MEMBER (без них)
    constraint group_roles_system_check check (
        (is_system and code = 'ADMIN' and is_admin) or (is_system and code = 'MEMBER' and not is_admin) or not is_system
    ),
    constraint group_roles_custom_code_check check (is_system or code not in ('ADMIN', 'MEMBER'))
);

create table memberships (
    group_id  uuid        not null references groups (id) on delete cascade,
    user_id   uuid        not null references users (id),
    role_id   uuid        not null,
    joined_at timestamptz not null,
    primary key (group_id, user_id),
    -- роль принадлежит той же группе; удалить роль с участниками нельзя (сначала переназначить)
    constraint memberships_role_fk foreign key (role_id, group_id) references group_roles (id, group_id)
);
create index memberships_user_idx on memberships (user_id);

create table invitations (
    id         uuid primary key,
    group_id   uuid        not null references groups (id) on delete cascade,
    created_by uuid        not null references users (id),
    -- адресное приглашение: принять может только владелец этого email
    email      citext,
    token_hash bytea       not null,
    expires_at timestamptz not null,
    max_uses   int         not null,
    used_count int         not null default 0,
    revoked_at timestamptz,
    created_at timestamptz not null,
    constraint invitations_token_uq unique (token_hash),
    constraint invitations_uses_check check (max_uses >= 1 and used_count >= 0 and used_count <= max_uses)
);
create index invitations_group_idx on invitations (group_id, created_at desc);
create index invitations_creator_idx on invitations (created_by, created_at desc);

-- Страховка инварианта «в группе есть хотя бы один администратор»: проверяется в конце транзакции,
-- чтобы группу можно было создать (группа, роли и участник вставляются по очереди) и удалить целиком.
create function check_group_has_admin() returns trigger as
$$
declare
    gid uuid := coalesce(old.group_id, new.group_id);
begin
    if exists (select 1 from groups where id = gid)
        and exists (select 1 from memberships where group_id = gid)
        and not exists (select 1
                        from memberships m
                                 join group_roles r on r.id = m.role_id
                        where m.group_id = gid
                          and r.is_admin) then
        raise exception 'в группе % не осталось администратора', gid using errcode = '23514';
    end if;
    return null;
end;
$$ language plpgsql;

create constraint trigger memberships_admin_guard
    after update or delete
    on memberships
    deferrable initially deferred
    for each row
execute function check_group_has_admin();

create constraint trigger group_roles_admin_guard
    after update of is_admin
    on group_roles
    deferrable initially deferred
    for each row
execute function check_group_has_admin();
