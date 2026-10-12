#!/usr/bin/env python3
"""Export the 37 bicycle-kick frames for scene G (Ronaldo on purple Matrix rain).

PRIVATE USE ONLY: the figure is a cut-out of copyrighted broadcast footage (a Cristiano Ronaldo
bicycle kick, TV broadcast clip). Do not publish or redistribute the frames or any APK containing them.

How the cut-outs were made (outside the repo, in ~/.hermes/profiles/kratos/cache/scratch/ron/):
  1. frames 28..64 of the clip were extracted at 1080 px video scale (hi/f_*.png);
  2. a YOLOv8x-seg person mask (conf 0.15, imgsz 1280) selected the blue-kit player in each frame;
  3. the mask became the alpha channel, green pitch spill was removed from the edge pixels (despill),
     and each frame was tight-cropped to the mask (cut_hi.py -> hicut/p_028.png .. p_064.png).

Run with:  ~/yolo-venv/bin/python scripts/export_ronaldo.py

Reads   <SRC>/hicut/p_028.png .. p_064.png   (37 RGBA cut-outs, 490x572 to 643x837)
Writes  app/src/main/assets/ronaldo/f_00.png .. f_36.png   (pixels unchanged, optimised)
        app/src/main/assets/ronaldo/manifest.txt           (one line per frame: index width height)
"""
import os

from PIL import Image

SRC = os.path.expanduser("~/.hermes/profiles/kratos/cache/scratch/ron/hicut/")
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "main", "assets", "ronaldo")
FIRST, LAST = 28, 64


def main() -> None:
    os.makedirs(OUT, exist_ok=True)
    lines = []
    for n, src in enumerate(range(FIRST, LAST + 1)):
        im = Image.open(os.path.join(SRC, "p_%03d.png" % src)).convert("RGBA")
        im.save(os.path.join(OUT, "f_%02d.png" % n), optimize=True)
        lines.append("%d %d %d" % (n, im.width, im.height))
    with open(os.path.join(OUT, "manifest.txt"), "w") as f:
        f.write("\n".join(lines) + "\n")
    total = sum(os.path.getsize(os.path.join(OUT, "f_%02d.png" % n)) for n in range(LAST - FIRST + 1))
    print("wrote %d frames, %.1f MB" % (len(lines), total / 1e6))


if __name__ == "__main__":
    main()
