#!/usr/bin/env python3
"""Export the bitmap assets for scenes D (Spider-Verse) and F (Rick and Morty).

PRIVATE USE ONLY: rick.png and morty.png are cut from copyrighted show art
(Rick and Morty, (c) Adult Swim / Warner Bros. Discovery) and spider.png / miles.png are
fan sprites of copyrighted characters (Marvel / Sony). Do not publish or redistribute them.

Run with:  ~/yolo-venv/bin/python scripts/export_assets.py

Inputs (outside the repo, from the concept prototypes):
  <GIF>/src/rm_keyed.png   860x536 RGBA cut-out, Rick left, Morty right, split at x=478
  <GIF>/concepts3.py       spiderman() / miles() 32x34 pixel sprites
Outputs: app/src/main/res/drawable-nodpi/{rick,morty,spider,miles}.png

rick/morty replicate concepts5.build_canvas(): each face is recentred on its own face centre and
the two share one chin line, so a vertical half split of both canvases lines up.
"""
import os
import sys

import numpy as np
from PIL import Image

GIF = os.path.expanduser("~/.hermes/profiles/kratos/cache/scratch/gif/")
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "main", "res", "drawable-nodpi")
SPLIT = 478

sys.path.insert(0, GIF)
import concepts3  # noqa: E402  (sprites; importing also loads the shared concept helpers)


def build_canvases():
    rm = Image.open(GIF + "src/rm_keyed.png").convert("RGBA")
    rick = rm.crop((0, 0, SPLIT, rm.height))
    morty = rm.crop((SPLIT, 0, rm.width, rm.height))
    rb, mb = rick.getbbox(), morty.getbbox()
    rick, morty = rick.crop(rb), morty.crop(mb)
    rick_cx, morty_cx = 255 - rb[0], 662 - SPLIT - mb[0]   # face centres in the crops
    rick_chin, morty_chin = 480 - rb[1], 497 - mb[1]       # chin lines in the crops
    cx = max(rick_cx, morty_cx, rick.width - rick_cx, morty.width - morty_cx)
    cw, chn = int(cx * 2 + 4), max(rick_chin, morty_chin)
    ch = max(rick.height - rick_chin + chn, morty.height - morty_chin + chn) + 6
    r = Image.new("RGBA", (cw, ch), (0, 0, 0, 0))
    m = Image.new("RGBA", (cw, ch), (0, 0, 0, 0))
    r.alpha_composite(rick, (int(cx - rick_cx), int(chn - rick_chin)))
    m.alpha_composite(morty, (int(cx - morty_cx), int(chn - morty_chin)))
    return r, m


def save(img, name):
    path = os.path.join(OUT, name)
    img.save(path, optimize=True)
    a = np.array(Image.open(path).convert("RGBA"))[..., 3]
    print(f"{name}: {img.width}x{img.height}, {os.path.getsize(path)} bytes, opaque px {(a > 0).sum()}")


def main():
    os.makedirs(OUT, exist_ok=True)
    rick, morty = build_canvases()
    save(rick, "rick.png")
    save(morty, "morty.png")
    save(concepts3.spiderman(), "spider.png")
    save(concepts3.miles(), "miles.png")


if __name__ == "__main__":
    main()
