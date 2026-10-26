-- Поиск (этап 7): подстроки и опечатки в названии. Полнотекстовый индекс по search_vector создан в V5.
create index reviews_title_trgm_idx on reviews using gin (title gin_trgm_ops);
