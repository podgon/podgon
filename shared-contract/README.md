# shared-contract
Общий Kotlin Multiplatform модуль (сервер + клиент): DTO API, правила валидации (`Validation`: email, username, displayName, bio, код), коды ошибок (`ErrorCode`). Без зависимостей от Spring и Android. Подпроект корневой Gradle-сборки; сейчас только таргет `jvm` (Android — этап 3, iOS — этап 11).

DTO — простые `data class` без аннотаций сериализации: сервер сериализует их Jackson. Сериализацию для клиента (kotlinx.serialization или клиент, сгенерированный из `api/openapi.yaml`) выберем на этапе 3.

Тесты: `gradle :shared-contract:jvmTest`.
