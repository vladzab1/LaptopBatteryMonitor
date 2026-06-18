# 💻 Laptop Battery & Media Monitor Widget (Android + Arch Linux)

Сучасний, повністю кастомізований Material You віджет для Android (відтестований на OnePlus / OxygenOS), який перетворює твій смартфон на пульт керування та монітор стану для ноутбука на базі Arch Linux / CachyOS. Все працює локально, без сторонніх хмар, через приватну мережу **Tailscale**.

---

## 🔥 Що воно вміє:

* **Моніторинг батареї ноутбука:** Заряд у відсотках (%) та поточний статус (Charging/Full/Discharging) у реальному часі.
* **Температура процесора:** Відображення CPU Temp для контролю нагріву заліза.
* **Миттєве керування медіа:** Зміна гучності (Volume Up / Down) та Play/Pause.
* **Миттєвий відгук (Optimistic UI):** Інтерфейс віджета перемикає іконку паузи та відсоток звуку в ту ж мілісекунду, коли ти тапаєш по кнопці, не чекаючи фонової відповіді від забагованого таймера ноута.
* **Соковитий відгук:** Інтегровано чітку тактильну вібрацію (Haptic Feedback / Клік), яка на повну розкриває преміальні вібромотори сучасних смартфонів.

---

## 🛠 Технологічний стек:

### 📱 Android-додаток (Папка `/app`)
* **Мова:** Kotlin
* **Інтерфейс:** Jetpack Glance / RemoteViews (оптимізовано під сітку віджетів 2x2).
* **Мережа:** Вбудований легкий HTTP-сервер на базі **Ktor** (або OkHttp embedded), який слухає вхідні POST-запити з JSON-даними від ноутбука.

### 🐧 Клієнтська частина Linux (Папка `/linux-scripts`)
* **Bash & Awk:** Збір системних метрик (`sysfs`), зчитування звуку PipeWire безпосередньо через `amixer` та `awk` без використання сторонніх інструментів.
* **Playerctl:** Контроль та зчитування стану активних медіаплеєрів у системі.
* **Python:** Легкий медіа-сервер для обробки вхідних команд з телефону.
* **Systemd:** Автоматизація фонових процесів через user-таймери.

---

## 📂 Структура лінукс-скриптів:

* `send_battery.sh` — Bash-скрипт збору даних (Battery, CPU, Volume, Player Status). Формує JSON через `jq` та відправляє через `curl` на телефон.
* `laptop-battery.service` & `laptop-battery.timer` — Юніти `systemd --user` для автоматичного запуска скрипта кожні кілька секунд у фоні.
* `music_server.py` — Python-скрипт, який слухає команди від додатка та керує системою (гучність, плей/пауза).
* `music-server.service` — Служба користувача для тримання Python-сервера постійно запущеним.

---

## 🚀 Як розгорнути лінукс-частину на ноутбуці:

### 1. Копіювання бінарників
Скопіюй скрипти у свою локальну папку бінарників та зроби їх виконуваними:
```bash
cp linux-scripts/send_battery.sh linux-scripts/music_server.py ~/.local/bin/
chmod +x ~/.local/bin/send_battery.sh ~/.local/bin/music_server.py

Відкрий файл ~/.local/bin/send_battery.sh і пропиши IP-адресу свого телефону з Tailscale та порт додатка:
PHONE_IP="ТВІЙ_TAILSCALE_IP_ТЕЛЕФОНУ"
PORT="8080"

Скопіюй файли служб у директорію користувача systemd:
mkdir -p ~/.config/systemd/user/
cp linux-scripts/*.service linux-scripts/*.timer ~/.config/systemd/user/

Перезавантаж конфігурацію systemd, увімкни та запусти таймер батареї разом із медіа-сервером:
systemctl --user daemon-reload
systemctl --user enable --now laptop-battery.timer
systemctl --user enable --now music-server.service

Перевірити, чи скрипт успішно крутиться у фоні, можна командою:
Bash

systemctl --user list-timers
