# mobile
Клиент на Kotlin Multiplatform + Compose Multiplatform (см. [client.md](../docs/client.md)).

- `shared/` — KMP-библиотека: сеть (Ktor), токены и сессия, вход/регистрация/профиль, Compose-экраны, DI (Koin), ресурсы ru/en. Платформенный код — в `androidMain` (Keystore-хранилище токенов, OkHttp-движок). iOS-таргет добавится на этапе 11.
- `androidApp/` — точка входа Android: `Application` (Koin), `MainActivity`, манифест, network-security-config (cleartext только в debug для эмулятора).
- Общие с сервером модели, валидация и коды ошибок — в [shared-contract](../shared-contract).

## Сборка и тесты
Нужен Android SDK (`ANDROID_HOME` или `sdk.dir` в `local.properties`, платформа `android-37.0`, build-tools 36.0.0).
```
./gradlew :mobile:shared:check          # unit-тесты (host) и ktlint
./gradlew :mobile:androidApp:assembleDebug
```
Debug-сборка обращается к серверу по `http://10.0.2.2:8080` (хост эмулятора); адрес меняется свойством `-Ppodgon.debugApiBaseUrl=...`, для release — `-Ppodgon.apiBaseUrl=https://...`.
