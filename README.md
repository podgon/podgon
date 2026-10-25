# Podgon

Мобильное приложение для обмена рекомендациями (отзывами) внутри закрытых групп: книги, фильмы, рестораны, мастера, сервисы — от людей, которым вы доверяете.

Статус: этап проектирования завершён, идёт подготовка репозитория (этап 0 плана).

## Документация
| Документ | О чём |
|---|---|
| [description.md](docs/description.md) | Исходное описание проекта |
| [architecture.md](docs/architecture.md) | Архитектура, решения (ADR), безопасность, развёртывание |
| [plan.md](docs/plan.md) | План реализации по этапам с критериями готовности |
| [server.md](docs/server.md) | Серверная часть |
| [client.md](docs/client.md) | Мобильный клиент |
| [db.md](docs/db.md) | База данных |
| [docs/branching.md](docs/branching.md) | Ветвление, MR, настройка защиты веток |
| [docs/adr/](docs/adr/README.md) | Индекс и шаблон ADR |

## Структура репозитория
| Каталог | Назначение |
|---|---|
| `api/` | Контракт OpenAPI (`openapi.yaml`) — источник правды для клиента и сервера |
| `shared-contract/` | Общий KMP-модуль: модели, правила валидации, коды ошибок |
| `server/` | Backend (Kotlin, Spring Boot) |
| `mobile/` | KMP-клиент (Android, позже iOS) |
| `deploy/` | Dockerfile, docker-compose, конфигурация развёртывания |
| `docs/` | ADR и вспомогательные документы |

## Ветки
`main` (production), `stable` (production-ready), `develop` (разработка). В `main` и `stable` — только через MR с ревью человека. Подробнее — [docs/branching.md](docs/branching.md).

## Лицензия
См. [LICENSE](LICENSE).
