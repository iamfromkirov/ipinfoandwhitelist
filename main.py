import socket
import threading
from urllib.parse import urlparse

import requests
import urllib3
from kivy.app import App
from kivy.clock import Clock
from kivy.core.window import Window
from kivy.graphics import Color, Line, RoundedRectangle
from kivy.metrics import dp, sp
from kivy.uix.boxlayout import BoxLayout
from kivy.uix.button import Button
from kivy.uix.gridlayout import GridLayout
from kivy.uix.label import Label
from kivy.uix.scrollview import ScrollView

urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)

# --- Список сайтов для проверки ---
SITES = [
    "t.me", "x.com", "instagram.com", "youtube.com", "chatgpt.com", "gosuslugi.ru",
    "ya.ru", "vk.ru", "max.ru", "sberbank.ru", "vtb.ru", "tbank.ru", "alfabank.ru",
    "pornolab.net", "rutracker.org",
    "ozon.ru", "wildberries.ru", "pikabu.com", "yaplakal.com",
]

# --- Поля информации об IP ---
IP_FIELDS = [
    ("ip", "IP"),
    ("hostname", "Hostname"),
    ("city", "City"),
    ("region", "Region"),
    ("country", "Country"),
    ("org", "Org"),
]

TIMEOUT = 5  # секунд на каждый этап
UA = ("Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 "
      "(KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36")

COLOR_WAIT = (0.69, 0.53, 0.0, 1)
COLOR_OK = (0.10, 0.50, 0.22, 1)
COLOR_NOT = (0.81, 0.13, 0.18, 1)
COLOR_TEXT = (0.92, 0.93, 0.95, 1)
COLOR_DIM = (0.65, 0.68, 0.74, 1)


# ---------- Виджеты ----------
class Frame(BoxLayout):
    """Контейнер с рамочкой и скруглённым фоном."""

    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        with self.canvas.before:
            Color(0.16, 0.17, 0.20, 1)
            self._bg = RoundedRectangle(radius=[dp(8)])
            Color(0.38, 0.42, 0.50, 1)
            self._ln = Line(rounded_rectangle=(0, 0, 0, 0, dp(8)), width=1)
        self.bind(pos=self._upd, size=self._upd)

    def _upd(self, *_):
        self._bg.pos = self.pos
        self._bg.size = self.size
        self._ln.rounded_rectangle = (self.x, self.y, self.width, self.height, dp(8))


class WrapLabel(Label):
    """Метка с переносом строк, высота подстраивается под текст."""

    def __init__(self, **kwargs):
        kwargs.setdefault("halign", "left")
        kwargs.setdefault("valign", "top")
        kwargs.setdefault("color", COLOR_TEXT)
        kwargs.setdefault("font_size", sp(13))
        super().__init__(size_hint_y=None, **kwargs)
        self.bind(width=lambda *_: setattr(self, "text_size", (self.width, None)))
        self.bind(texture_size=lambda *_: setattr(
            self, "height", max(self.texture_size[1], dp(18))))


def row_label(text, **kwargs):
    lbl = Label(text=text, halign="left", valign="middle",
                color=COLOR_TEXT, font_size=sp(14), **kwargs)
    lbl.bind(size=lambda w, s: setattr(w, "text_size", s))
    return lbl


# ---------- Приложение ----------
class NetCheckApp(App):
    title = "Сеть: IP и сайты"

    def build(self):
        Window.clearcolor = (0.11, 0.12, 0.14, 1)

        self.rows = {}        # url -> status label
        self.ip_labels = {}   # field -> value label
        self.checking = False
        self.done = 0

        root = BoxLayout(orientation="vertical",
                         padding=[dp(10), dp(8), dp(10), dp(8)], spacing=dp(8))

        # --- Рамка с информацией об IP (сверху) ---
        ip_frame = Frame(orientation="vertical", size_hint_y=None,
                         padding=dp(8), spacing=dp(2))
        ip_frame.bind(minimum_height=ip_frame.setter("height"))

        title = WrapLabel(text="IP info", bold=True, color=COLOR_DIM)
        ip_frame.add_widget(title)

        grid = GridLayout(cols=2, size_hint_y=None, spacing=(dp(8), dp(2)))
        grid.bind(minimum_height=grid.setter("height"))
        for key, name in IP_FIELDS:
            grid.add_widget(WrapLabel(text=f"{name}:", bold=True,
                                      size_hint_x=None, width=dp(78)))
            val = WrapLabel(text="…")
            grid.add_widget(val)
            self.ip_labels[key] = val
        ip_frame.add_widget(grid)
        root.add_widget(ip_frame)

        # --- Список сайтов (под рамкой) ---
        scroll = ScrollView(do_scroll_x=False)
        sites = GridLayout(cols=2, size_hint_y=None,
                           row_default_height=dp(30), row_force_default=True,
                           padding=[dp(6), 0, dp(6), 0])
        sites.bind(minimum_height=sites.setter("height"))
        for url in SITES:
            sites.add_widget(row_label(url))
            st = Label(text="wait", size_hint_x=None, width=dp(56), bold=True,
                       halign="right", valign="middle", color=COLOR_WAIT,
                       font_size=sp(14))
            st.bind(size=lambda w, s: setattr(w, "text_size", s))
            sites.add_widget(st)
            self.rows[url] = st
        scroll.add_widget(sites)
        root.add_widget(scroll)

        # --- Нижняя панель ---
        bottom = BoxLayout(size_hint_y=None, height=dp(46), spacing=dp(8))
        self.counter = Label(text="", color=COLOR_DIM, font_size=sp(13),
                             halign="left", valign="middle")
        self.counter.bind(size=lambda w, s: setattr(w, "text_size", s))
        bottom.add_widget(self.counter)
        self.btn = Button(text="Проверить снова", size_hint_x=None, width=dp(170),
                          font_size=sp(14), on_release=lambda *_: self.check_all())
        bottom.add_widget(self.btn)
        root.add_widget(bottom)

        return root

    def on_start(self):
        self.check_all()

    # ---------- Запуск проверки ----------
    def check_all(self):
        if self.checking:
            return
        self.checking = True
        self.done = 0
        self.btn.disabled = True
        self.counter.text = f"Проверка… 0/{len(SITES)}"

        for lbl in self.rows.values():
            lbl.text = "wait"
            lbl.color = COLOR_WAIT
        for lbl in self.ip_labels.values():
            lbl.text = "…"

        threading.Thread(target=self._load_ip, daemon=True).start()
        for url in SITES:
            threading.Thread(target=self._check_one, args=(url,),
                             daemon=True).start()

    # ---------- IP info ----------
    def _load_ip(self):
        try:
            r = requests.get("https://ipinfo.io/json", timeout=TIMEOUT + 3,
                             headers={"User-Agent": UA})
            data = r.json()
        except Exception:
            data = None
        Clock.schedule_once(lambda dt: self._update_ip(data), 0)

    def _update_ip(self, data):
        for key, lbl in self.ip_labels.items():
            if data is None:
                lbl.text = "ошибка" if key == "ip" else "—"
            else:
                lbl.text = str(data.get(key, "N/A"))

    # ---------- Проверка сайта (в потоке) ----------
    def _check_one(self, url):
        ok = self._probe(url)
        Clock.schedule_once(lambda dt: self._update_row(url, ok), 0)

    def _probe(self, url):
        """Трёхэтапная проверка: DNS → TCP → HTTP."""
        if not url.startswith(("http://", "https://")):
            url = "https://" + url

        parsed = urlparse(url)
        host = parsed.hostname
        port = parsed.port or (443 if parsed.scheme == "https" else 80)
        if not host:
            return False

        try:
            socket.gethostbyname(host)
        except socket.gaierror:
            return False

        try:
            with socket.create_connection((host, port), timeout=TIMEOUT):
                pass
        except (socket.timeout, socket.error):
            return False

        try:
            r = requests.get(
                url, timeout=TIMEOUT, verify=False, allow_redirects=True,
                headers={
                    "User-Agent": UA,
                    "Accept": "text/html,application/xhtml+xml,*/*;q=0.8",
                    "Accept-Language": "ru-RU,ru;q=0.9,en;q=0.8",
                },
            )
            return r.status_code < 500
        except Exception:
            return False

    # ---------- Обновление строки (в главном потоке) ----------
    def _update_row(self, url, ok):
        lbl = self.rows[url]
        lbl.text = "ok" if ok else "not"
        lbl.color = COLOR_OK if ok else COLOR_NOT

        self.done += 1
        if self.done >= len(SITES):
            self.checking = False
            self.btn.disabled = False
            okc = sum(1 for l in self.rows.values() if l.text == "ok")
            self.counter.text = f"Доступно: {okc}/{len(SITES)}"
        else:
            self.counter.text = f"Проверка… {self.done}/{len(SITES)}"


if __name__ == "__main__":
    NetCheckApp().run()
