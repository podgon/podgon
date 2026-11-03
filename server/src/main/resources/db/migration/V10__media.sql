-- Этап 8.3.4: фото рекомендаций и аватары. Файлы лежат в хранилище (MediaStorage), в БД — их идентификаторы;
-- новый идентификатор при каждой загрузке (адрес картинки меняется, кэш клиента не устаревает).
alter table users add column avatar_id uuid;
alter table reviews add column photo_id uuid;
