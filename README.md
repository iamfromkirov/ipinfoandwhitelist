# NetCheck (Android, Kotlin)

Сверху рамка с информацией о вашем IP (ipinfo.io), под ней список сайтов
со статусами доступности (DNS → TCP → HTTP). Внешних библиотек нет,
используется только стандартный Android SDK.

## Сборка APK через GitHub Actions

Каждый push в `main` запускает сборку (около 3–5 минут).
Готовый APK: **Actions → нужный запуск → Artifacts → netcheck-apk**.

Для публикации в Releases создайте тег:

```bash
git tag v0.1
git push origin v0.1
```

## Установка на телефон

Скачайте APK из архива artifact, откройте его и разрешите установку
из неизвестных источников.

## Изменение списка сайтов

Список `SITES` находится в начале `app/src/main/java/org/netcheck/MainActivity.kt`.
