# SEYRO Monitor

Нативное Android-приложение на Kotlin и Jetpack Compose для **только чтения** операционных данных SEYRO через существующий FastAPI. Оно не содержит SSH-клиента, VPN-runtime и функций изменения серверов.

## Подключение к API

В разделе «Доступ» указан сервер `https://admin.seyro.ru`. Вставьте выпущенный `monitor-token` и нажмите «Сохранить и подключиться». Токен шифруется ключом Android Keystore и хранится на устройстве. В приложении выполняются только GET-запросы с заголовком `Authorization: Bearer …`; управление серверами отсутствует.

Используются действующие read-only маршруты FastAPI:

```text
GET /api/v1/monitor/overview
GET /api/v1/monitor/nodes
GET /api/v1/monitor/system/status
```

Обзор показывает сводку по узлам, статусы IN/OUT, Xray и синхронизации. API в настоящее время возвращает `metrics_available: false`; CPU, RAM, диск, сеть и их история пока не отображаются.

## Обновления из GitHub

В разделе «Доступ» приложение проверяет публичный `update.json` в этом репозитории. При наличии новой версии оно скачивает APK только по HTTPS, сверяет SHA-256 и передаёт установку системному Android Package Installer.

Релизы выпускаются тегом `seyro-monitor-vX.Y.Z`. Для этого в GitHub Actions необходимы четыре Secrets: `SEYRO_MONITOR_KEYSTORE_BASE64`, `SEYRO_MONITOR_KEYSTORE_PASSWORD`, `SEYRO_MONITOR_KEY_ALIAS` и `SEYRO_MONITOR_KEY_PASSWORD`. Это должен быть один постоянный ключ подписи: Android отклонит обновление, если APK подписан другим ключом.

## Сборка

Откройте каталог `SEYRO_Monitor` в Android Studio и соберите debug APK. Требуются JDK 17, Android SDK 35 и доступ Gradle к Google/Maven Central. GitHub Actions также собирает debug APK при изменениях в этом каталоге.
