# SEYRO

Репозиторий Android VPN-клиента **SEYRO**.

## Основной принцип разработки

- Все изменения клиента и публикация новых APK идут через основную ветку `main`.
- Workflow всегда собирает именно `main`, в том числе при ручном запуске.
- Отдельные ветки разработки для дальнейших изменений не используются.
- Публичное имя VPN-приложения и релизов — **SEYRO**, без пометки TEST.
- VPN-движок — **sing-box/libbox 1.14.3**. Xray и HEV не используются как runtime-движки.

## SEYRO — VPN-клиент

- Исходники: `SEYRO_Lite_Light/app/src/main/`
- Gradle-проект: `SEYRO_Lite_Light/`
- Workflow: **Build SEYRO**
- Android Application ID: `com.seyro.lite`
- Версия APK: `0.9.<номер запуска GitHub Actions>`
- Официальный релиз: тег `seyro-v0.9.<номер>`, APK `seyro.apk`
- Автообновление проверяет только релизы этого репозитория с официальным форматом тега и APK.

[Исходники SEYRO](https://github.com/pavelzolotovng-oss/seyro/tree/main/SEYRO_Lite_Light) · [Сборка SEYRO](https://github.com/pavelzolotovng-oss/seyro/actions/workflows/android-apk.yml) · [Релизы SEYRO](https://github.com/pavelzolotovng-oss/seyro/releases)

## SEYRO Monitor — мониторинг серверов

- Исходники Android-приложения: `SEYRO_Monitor/`
- Ветка разработки и публикации: `main`, рядом с `SEYRO_Lite_Light/`
- Android Application ID: `ru.seyro.monitor`
- Первая версия: `0.1`; обновления публикуются отдельными релизами `seyro-monitor-*`
- Приложение читает системные метрики узлов через существующий FastAPI `GET /api/v1/monitor/nodes`. Новые backend-маршруты не используются.
- Секундные точки кэшируются только в памяти приложения на 30 минут. Автопроверка обновления при запуске отключена; проверка доступна в настройках приложения.

[Исходники SEYRO Monitor](https://github.com/pavelzolotovng-oss/seyro/tree/main/SEYRO_Monitor) · [Сборка Monitor](https://github.com/pavelzolotovng-oss/seyro/actions/workflows/seyro-monitor.yml) · [Релизы Monitor](https://github.com/pavelzolotovng-oss/seyro/releases)

> Успешная сборка подтверждает создание APK, но сама по себе не доказывает, что VPN пропускает трафик. После выпуска нужно проверить подключение и открытие сайтов на Android-устройстве.

## Подпись и обновления

Workflow использует постоянный ключ подписи из секретов GitHub Actions:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

Для обновления поверх уже установленной версии Android требует тот же Application ID, тот же ключ подписи и более высокий `versionCode`. Переход со старого тестового пакета `com.seyro.lite.singboxtest` на официальный `com.seyro.lite` является новой установкой: удалите старый тестовый клиент и установите официальный APK. Данные приложения между этими пакетами автоматически не переносятся.

Не публикуйте ключ подписи, пароли или приватные ссылки подписки.
