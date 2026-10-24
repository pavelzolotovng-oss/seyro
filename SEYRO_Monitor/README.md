# SEYRO Monitor

Нативное Android-приложение на Kotlin и Jetpack Compose для **только чтения** операционных данных SEYRO через существующий FastAPI. Оно не содержит SSH-клиента, VPN-runtime и функций изменения серверов.

## Статус

Создан новый Compose-каркас (`ru.seyro.monitor`) для read-only монитора. Экран настройки явно требует отдельный `monitor-token`; технические Xray-токены, пароль администратора и cookie-сессии не используются.

Приложение ожидает отдельный контракт FastAPI:

```text
GET /api/v1/monitor/overview
GET /api/v1/monitor/nodes
GET /api/v1/monitor/nodes/{role}/{id}
GET /api/v1/monitor/nodes/{role}/{id}/metrics?from=&to=&step=
```

Пока read-only API не добавлен в FastAPI, приложение сознательно не показывает демонстрационные метрики как реальные и не выполняет сетевых запросов.

## Обновления из GitHub

В разделе «Доступ» приложение проверяет публичный `update.json` в этом репозитории. При наличии новой версии оно скачивает APK только по HTTPS, сверяет SHA-256 и передаёт установку системному Android Package Installer.

Релизы выпускаются тегом `seyro-monitor-vX.Y.Z`. Для этого в GitHub Actions необходимы четыре Secrets: `SEYRO_MONITOR_KEYSTORE_BASE64`, `SEYRO_MONITOR_KEYSTORE_PASSWORD`, `SEYRO_MONITOR_KEY_ALIAS` и `SEYRO_MONITOR_KEY_PASSWORD`. Это должен быть один постоянный ключ подписи: Android отклонит обновление, если APK подписан другим ключом.

## Сборка

Откройте каталог `SEYRO_Monitor` в Android Studio и соберите debug APK. Требуются JDK 17, Android SDK 35 и доступ Gradle к Google/Maven Central.
