# SEYRO

Единый репозиторий двух Android-проектов: **SEYRO Monitor** и **SEYRO Lite** (клиент подключения).

## Структура

- `SEYRO_Monitor/` — приложение мониторинга серверов.
- `SEYRO_Lite_Light/` — Android-клиент подключения SEYRO Lite.
- `.github/workflows/` — автоматизация сборок.
- `CHANGELOG.md` — история изменений клиента.

Работа ведётся непосредственно в основной ветке `main`. Не используем отдельные ветки для разработки.

## SEYRO Lite — VPN-клиент

Клиент использует **sing-box/libbox 1.14.3** как единственный VPN-движок. Runtime Xray и HEV в этой сборке не используются. Поддерживаемые профили преобразуются в конфигурацию sing-box; неподдерживаемый транспорт должен выдавать понятную ошибку, а не запускать ложное подключение.

- Исходники: `SEYRO_Lite_Light/app/src/main/`
- Gradle-проект: `SEYRO_Lite_Light/`
- Сборка: workflow **Build SEYRO Lite APK**
- Пакет тестового приложения: `com.seyro.lite.singboxtest`
- Версия APK: `0.9.<номер запуска>`
- APK публикуется как prerelease `debug-build-<номер>`

[Открыть SEYRO Lite в репозитории](https://github.com/pavelzolotovng-oss/seyro/tree/main/SEYRO_Lite_Light) · [Сборки и APK](https://github.com/pavelzolotovng-oss/seyro/actions/workflows/android-apk.yml)

> Успешная компиляция подтверждает создание APK, но сама по себе не подтверждает, что VPN пропускает трафик. После изменений транспорта нужно проверить подключение и открытие сайтов на Android-устройстве.

## SEYRO Monitor

Исходники мониторинга находятся отдельно от клиента в `SEYRO_Monitor/`, чтобы сборки и изменения приложений не смешивались.

[Открыть SEYRO Monitor в репозитории](https://github.com/pavelzolotovng-oss/seyro/tree/main/SEYRO_Monitor)

## Подпись и безопасность

Для обновления приложения поверх установленной версии сохраняйте прежний ключ подписи и совместимый package ID. Не коммитьте keystore, пароли, токены или приватные ссылки подписок. Не публикуйте секреты в журналах и скриншотах.
