#!/usr/bin/env python3
"""Generates the app icon (a teal rounded square with a white water drop) for the
installers: cleanwater-1024.png (source size), cleanwater-256.png (Linux), cleanwater.ico
(Windows, 7 sizes) and cleanwater.icns (macOS). The generated files are committed, so
packaging does not need this script; run it only to change the icon.

usage: python3 tools/make-icons.py [outdir]
       (outdir defaults to cwc-desktop/src/packaging/icons; needs Pillow)
"""
import math
import sys

from PIL import Image, ImageDraw

OUT = sys.argv[1] if len(sys.argv) > 1 else 'cwc-desktop/src/packaging/icons'
SIZE = 1024
TOP, BOTTOM = (0x15, 0x5E, 0x75), (0x08, 0x91, 0xB2)   # brand-800 to brand-600


def drop_points(cx, top, bottom, r, n=180):
    """Teardrop outline: a circle of radius r touching `bottom`, plus the two tangent
    lines from the circle to the tip at (cx, top)."""
    cy = bottom - r
    b = math.acos(r / (cy - top))      # angle between centre->tip and centre->tangent point
    start = -math.pi / 2 + b           # right tangent point (screen coordinates, y down)
    sweep = 2 * math.pi - 2 * b        # around the bottom to the left tangent point
    pts = [(cx, top)]
    for i in range(n + 1):
        ang = start + sweep * i / n
        pts.append((cx + r * math.cos(ang), cy + r * math.sin(ang)))
    return pts


def render(size):
    k = 4                              # supersampling factor
    w = size * k
    im = Image.new('RGBA', (w, w), (0, 0, 0, 0))
    margin, radius = int(w * 0.06), int(w * 0.22)
    background = Image.new('RGBA', (w, w))
    draw_bg = ImageDraw.Draw(background)
    for y in range(w):
        t = y / (w - 1)
        colour = tuple(int(TOP[i] + (BOTTOM[i] - TOP[i]) * t) for i in range(3)) + (255,)
        draw_bg.line([(0, y), (w, y)], fill=colour)
    mask = Image.new('L', (w, w), 0)
    ImageDraw.Draw(mask).rounded_rectangle([margin, margin, w - margin, w - margin],
                                           radius=radius, fill=255)
    im.paste(background, (0, 0), mask)
    draw = ImageDraw.Draw(im)
    draw.polygon(drop_points(w / 2, w * 0.20, w * 0.80, w * 0.21), fill=(255, 255, 255, 255))
    draw.ellipse([w * 0.38, w * 0.56, w * 0.45, w * 0.70], fill=(0x0E, 0x74, 0x90, 70))  # highlight
    return im.resize((size, size), Image.LANCZOS)


def main():
    big = render(SIZE)
    big.save(f'{OUT}/cleanwater-1024.png')
    render(256).save(f'{OUT}/cleanwater-256.png')
    big.save(f'{OUT}/cleanwater.ico',
             sizes=[(16, 16), (24, 24), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)])
    big.save(f'{OUT}/cleanwater.icns')
    print(f'icons written to {OUT}')


if __name__ == '__main__':
    main()
