-- Роль приложения без прав на DDL (замечание R6 ревью, docs/db.md «Безопасность БД»).
-- Миграции Flyway выполняет владелец схемы (__MIGRATOR__), приложение работает ролью podgon_app:
-- только чтение и изменение данных. Скрипт идемпотентен; __MIGRATOR__ и __APP_PASSWORD__ подставляет
-- 10-app-role.sh (в Selectel — администратор БД вручную).
do $$
begin
    if not exists (select 1 from pg_roles where rolname = 'podgon_app') then
        create role podgon_app login password '__APP_PASSWORD__';
    else
        alter role podgon_app login password '__APP_PASSWORD__';
    end if;
end
$$;

-- connect к базе даёт PUBLIC по умолчанию; отдельный grant не нужен
grant usage on schema public to podgon_app;
revoke create on schema public from podgon_app;

-- Уже созданные таблицы и последовательности
grant select, insert, update, delete on all tables in schema public to podgon_app;
grant usage, select on all sequences in schema public to podgon_app;

-- Всё, что миграции создадут дальше
alter default privileges for role __MIGRATOR__ in schema public
    grant select, insert, update, delete on tables to podgon_app;
alter default privileges for role __MIGRATOR__ in schema public
    grant usage, select on sequences to podgon_app;
