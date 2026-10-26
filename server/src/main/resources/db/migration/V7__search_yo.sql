-- Этап 8.1 (замечание R11 ревью): «ё» и «е» в поиске не различаются. Словарь russian их не приравнивает,
-- поэтому текст для поиска нормализуется: полнотекстовый вектор и колонка названия для подстроки и опечаток.
-- Запрос пользователя нормализуется так же в приложении (ReviewRepository).
drop index reviews_title_trgm_idx;
drop index reviews_search_idx;
alter table reviews drop column search_vector;
alter table reviews
    add column title_search text generated always as (translate(title, 'ёЁ', 'еЕ')) stored,
    add column search_vector tsvector generated always as (
        setweight(to_tsvector('russian', translate(title, 'ёЁ', 'еЕ')), 'A')
            || setweight(to_tsvector('russian', translate(description, 'ёЁ', 'еЕ')), 'B')
    ) stored;
create index reviews_search_idx on reviews using gin (search_vector);
create index reviews_title_trgm_idx on reviews using gin (title_search gin_trgm_ops);
