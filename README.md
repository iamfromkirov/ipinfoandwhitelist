# NetCheck (Android)

Приложение на Kivy: сверху рамка с информацией о вашем IP (ipinfo.io),
ниже список сайтов со статусами доступности (DNS → TCP → HTTP).

## Сборка APK через GitHub Actions

1. Создайте пустой репозиторий на GitHub (например, `netcheck`).
2. В папке проекта выполните:

```bash
git init
git add .
git commit -m "Initial commit"
git branch -M main
git remote add origin https://github.com/<ваш_логин>/netcheck.git
git push -u origin main
```

3. Откройте вкладку **Actions** — запустится workflow **Build Android APK**
   (первая сборка занимает 20–40 минут, следующие быстрее благодаря кешу).
4. Готовый APK: **Actions → нужный запуск → Artifacts → netcheck-apk**.
5. Для публикации в Releases создайте тег:

```bash
git tag v0.1
git push origin v0.1
```

APK появится в разделе **Releases**.

## Установка на телефон

Скачайте APK, откройте его и разрешите установку из неизвестных источников.

## Локальный запуск (для проверки интерфейса на ПК)

```bash
pip install kivy requests
python main.py
```

## Изменение списка сайтов

Список `SITES` находится в начале `main.py`.
