# Ветвление и работа с MR

## Ветки
| Ветка | Назначение | Кто пишет |
|---|---|---|
| `main` | production | только через MR, принимает ревьюер-человек |
| `stable` | production-ready | только через MR, merge — по явному запросу разрешения |
| `develop` | разработка | основная ветка интеграции |
| `feature/<этап>-<кратко>` | работа над этапом/задачей | от `develop`, обратно в `develop` |

Поток: `feature/*` → `develop` → `stable` → `main`.

## Настройка защиты веток на GitHub (выполняет владелец репозитория)
Settings → Branches → Branch protection rules, для `main` и `stable`:
- Require a pull request before merging; Require approvals: 1 (ревьюер — человек);
- Dismiss stale approvals при новых коммитах;
- Require status checks to pass: `Проверки репозитория` (и далее — jobs сервера/клиента);
- Require branches to be up to date before merging;
- Restrict who can push to matching branches (запретить прямой push, включая администраторов — Do not allow bypassing);
- Block force pushes и deletions.

Для `develop`: рекомендуется Require status checks; прямой push допустим, пока работает один разработчик, но предпочтительны MR из `feature/*`.

## MR
- Шаблон — `.github/pull_request_template.md`.
- Один MR — один этап/логически связанное изменение; контракт, миграции, сервер и клиент меняются согласованно.
- Заголовок и описание — на русском.

## Коммиты
- Сообщения на русском, в повелительном/описательном стиле, одна тема на коммит.
- Секреты, пароли, ключи, персональные данные не коммитятся (см. `.gitignore`, проверка в CI).

## Релизы
- Merge `develop` → `stable` — после успешных тестов и приёмки, по явному согласованию.
- Merge `stable` → `main` — релиз; теги `vX.Y.Z`.
