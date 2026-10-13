"""Erzeugt Store-Icon (512×512) und Titelgrafik (1024×500) für Google Play.

    python play/make_graphics.py
"""
import os

from PIL import Image, ImageDraw, ImageFont

OUT = os.path.join(os.path.dirname(__file__), "graphics")
SS = 4  # Supersampling für glatte Kanten
RED = (211, 47, 47)
BOLD = r"C:\Windows\Fonts\arialbd.ttf"
TITLE = r"C:\Windows\Fonts\segoeuib.ttf"
TEXT = r"C:\Windows\Fonts\segoeui.ttf"


def sign(draw, cx, cy, r, text):
    """Tempo-Schild wie in der App: roter Ring (20 % des Radius), weiße Fläche, schwarze Zahl."""
    draw.ellipse((cx - r, cy - r, cx + r, cy + r), fill=RED)
    inner = r * 0.8
    draw.ellipse((cx - inner, cy - inner, cx + inner, cy + inner), fill="white")
    font = ImageFont.truetype(BOLD, int(r * (1.0 if len(text) < 3 else 0.76)))
    draw.text((cx, cy), text, font=font, fill="black", anchor="mm")


def icon():
    size = 512 * SS
    img = Image.new("RGB", (size, size), "black")
    sign(ImageDraw.Draw(img), size / 2, size / 2, size * 0.40, "30")
    img.resize((512, 512), Image.LANCZOS).save(os.path.join(OUT, "icon-512.png"))


def feature():
    w, h = 1024 * SS, 500 * SS
    img = Image.new("RGB", (w, h), "black")
    d = ImageDraw.Draw(img)
    sign(d, 250 * SS, 250 * SS, 185 * SS, "70")
    x = 475 * SS
    d.text((x, 205 * SS), "was erlaubt", font=ImageFont.truetype(TITLE, 92 * SS), fill="white", anchor="ls")
    sub = ImageFont.truetype(TEXT, 30 * SS)
    d.text((x, 270 * SS), "Das erlaubte Tempo –", font=sub, fill=(190, 190, 190), anchor="ls")
    d.text((x, 308 * SS), "groß und passend zur Fahrtrichtung", font=sub, fill=(190, 190, 190), anchor="ls")
    d.text((x, 400 * SS), "Daten: OpenStreetMap · offline nutzbar", font=ImageFont.truetype(TEXT, 24 * SS),
           fill=(120, 120, 120), anchor="ls")
    img.resize((1024, 500), Image.LANCZOS).save(os.path.join(OUT, "feature-graphic-1024x500.png"))


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    icon()
    feature()
    print("fertig:", OUT)
