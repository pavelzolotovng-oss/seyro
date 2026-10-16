# SEYRO Lite

**SEYRO Lite** — Android-клиент для подключения к VPN по подписке. Проект написан на Java и использует Android `VpnService`, Xray и HEV tun2socks для маршрутизации трафика.

> **Статус:** проект активно развивается. APK из GitHub Releases помечаются как prerelease/test builds. Проверяйте каждую сборку на своём устройстве перед использованием.

## Возможности

- Импорт и локальное хранение ссылки подписки.
- Профили VLESS + REALITY.
- Поддержка TCP и XHTTP.
- Маршрутизация VPN-трафика через HEV tun2socks к локальному SOCKS-входу Xray.
- Выбор профиля и подключение через системный VPN-интерфейс Android.
- Диагностический журнал.
- Проверка новых версий и предложение установить APK из GitHub Releases.
- Автоматическая сборка и публикация APK через GitHub Actions.

## Скачать APK

Откройте **[Releases](https://github.com/pavelzolotovng-oss/seyro-lite/releases)** и выберите последний релиз SEYRO Lite. В разделе **Assets** скачайте `app-debug.apk`.

Релизы публикуются как **Pre-release**. Android может запросить разрешение на установку приложений из браузера или другого источника. Установка поверх существующей версии возможна, если APK подписан тем же ключом и имеет более высокий `versionCode`.

## Структура проекта

- `SEYRO_Lite_Light/` — проект Android Studio/Gradle.
- `SEYRO_Lite_Light/app/src/main/java/` — исходный Java-код.
- `SEYRO_Lite_Light/app/src/main/res/` — ресурсы Android.
- `SEYRO_Lite_Light/app/build.gradle` — конфигурация приложения и версия.
- `.github/workflows/android-apk.yml` — workflow сборки и публикации APK.
- `CHANGELOG.md` — журнал изменений.

Android-проект остаётся в отдельной папке, чтобы Android Studio могла открыть его как целостный Gradle-проект. Документация находится в корне репозитория.

## Сборка через GitHub Actions

1. Откройте вкладку **Actions**.
2. Выберите **Build SEYRO Lite APK**.
3. Нажмите **Run workflow** и выберите ветку.
4. Дождитесь успешного завершения.
5. Скачайте APK из **Artifacts** или откройте опубликованный релиз в **Releases**.

Workflow использует секреты репозитория `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS` и `ANDROID_KEY_PASSWORD` для подписи APK. Для установки обновлений необходимо сохранять один и тот же ключ подписи.

## Локальная сборка

Откройте `SEYRO_Lite_Light/` в Android Studio. Для сборки используются JDK 17 и Android SDK 35. Настройте переменные подписи как в GitHub Actions и запустите Gradle-задачу `assembleDebug`. Workflow загружает необходимые AAR-библиотеки Xray и HEV во время сборки.

## Технические сведения

- Язык: Java
- Application ID: `com.seyro.lite`
- Минимальная версия Android: Android 10 (API 29)
- Compile/Target SDK: 35
- `versionName` и `versionCode` формируются на основе номера запуска GitHub Actions.

## Безопасность

Ссылка подписки может содержать секретные данные. Не публикуйте её в issues, скриншотах, коммитах или диагностических логах. Не добавляйте в репозиторий keystore, пароли, токены или другие секреты.

## Изменения

Смотрите [CHANGELOG.md](CHANGELOG.md) и [историю коммитов](https://github.com/pavelzolotovng-oss/seyro-lite/commits/main).
