#!/usr/bin/env python3
"""Generate original Minecard face PNGs (52 + back + 2 jokers). Not copied from commercial decks."""

from __future__ import annotations

import json
import struct
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TEX = ROOT / "src" / "main" / "resources" / "assets" / "minecard" / "textures" / "font" / "cards"
FONT_JSON = ROOT / "src" / "main" / "resources" / "assets" / "minecard" / "font" / "cards.json"

WIDTH, HEIGHT = 40, 56
SUITS = [
    ("spades", "S", (20, 20, 20), "♠"),
    ("hearts", "H", (180, 30, 40), "♥"),
    ("diamonds", "D", (180, 30, 40), "♦"),
    ("clubs", "C", (20, 20, 20), "♣"),
]
RANKS = [
    ("a", "A"),
    ("2", "2"),
    ("3", "3"),
    ("4", "4"),
    ("5", "5"),
    ("6", "6"),
    ("7", "7"),
    ("8", "8"),
    ("9", "9"),
    ("10", "10"),
    ("j", "J"),
    ("q", "Q"),
    ("k", "K"),
]

# 5x7 bitmap glyphs for A–K and digits
GLYPHS: dict[str, list[str]] = {
    "A": ["01110", "10001", "10001", "11111", "10001", "10001", "10001"],
    "2": ["01110", "10001", "00001", "00010", "00100", "01000", "11111"],
    "3": ["11110", "00001", "00001", "01110", "00001", "00001", "11110"],
    "4": ["00010", "00110", "01010", "10010", "11111", "00010", "00010"],
    "5": ["11111", "10000", "11110", "00001", "00001", "10001", "01110"],
    "6": ["01110", "10000", "10000", "11110", "10001", "10001", "01110"],
    "7": ["11111", "00001", "00010", "00100", "01000", "01000", "01000"],
    "8": ["01110", "10001", "10001", "01110", "10001", "10001", "01110"],
    "9": ["01110", "10001", "10001", "01111", "00001", "00001", "01110"],
    "0": ["01110", "10001", "10001", "10001", "10001", "10001", "01110"],
    "J": ["00111", "00010", "00010", "00010", "00010", "10010", "01100"],
    "Q": ["01110", "10001", "10001", "10001", "10101", "10010", "01101"],
    "K": ["10001", "10010", "10100", "11000", "10100", "10010", "10001"],
    "1": ["00100", "01100", "00100", "00100", "00100", "00100", "01110"],
}

SUIT_GLYPHS: dict[str, list[str]] = {
    "S": [
        "00100",
        "01110",
        "11111",
        "11111",
        "01110",
        "00100",
        "01110",
    ],
    "H": [
        "01010",
        "11111",
        "11111",
        "11111",
        "01110",
        "00100",
        "00000",
    ],
    "D": [
        "00100",
        "01110",
        "11111",
        "11111",
        "01110",
        "00100",
        "00000",
    ],
    "C": [
        "01110",
        "11111",
        "01110",
        "11111",
        "01110",
        "00100",
        "01110",
    ],
}


def png_rgba(pixels: list[list[tuple[int, int, int, int]]], path: Path) -> None:
    h = len(pixels)
    w = len(pixels[0])
    raw = bytearray()
    for row in pixels:
        raw.append(0)
        for r, g, b, a in row:
            raw.extend((r, g, b, a))

    def chunk(tag: bytes, data: bytes) -> bytes:
        return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    ihdr = struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0)
    data = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr) + chunk(b"IDAT", zlib.compress(bytes(raw), 9)) + chunk(b"IEND", b"")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(data)


def blank(color: tuple[int, int, int, int] = (245, 245, 240, 255)) -> list[list[tuple[int, int, int, int]]]:
    return [[color for _ in range(WIDTH)] for _ in range(HEIGHT)]


def fill_rect(
    px: list[list[tuple[int, int, int, int]]],
    x0: int,
    y0: int,
    x1: int,
    y1: int,
    color: tuple[int, int, int, int],
) -> None:
    for y in range(max(0, y0), min(HEIGHT, y1)):
        for x in range(max(0, x0), min(WIDTH, x1)):
            px[y][x] = color


def draw_glyph(
    px: list[list[tuple[int, int, int, int]]],
    glyph: list[str],
    ox: int,
    oy: int,
    color: tuple[int, int, int, int],
    scale: int = 2,
) -> None:
    for gy, row in enumerate(glyph):
        for gx, ch in enumerate(row):
            if ch != "1":
                continue
            for dy in range(scale):
                for dx in range(scale):
                    x, y = ox + gx * scale + dx, oy + gy * scale + dy
                    if 0 <= x < WIDTH and 0 <= y < HEIGHT:
                        px[y][x] = color


def draw_text(
    px: list[list[tuple[int, int, int, int]]],
    text: str,
    ox: int,
    oy: int,
    color: tuple[int, int, int, int],
    scale: int = 2,
) -> None:
    cursor = ox
    for ch in text:
        g = GLYPHS.get(ch)
        if not g:
            cursor += 4 * scale
            continue
        draw_glyph(px, g, cursor, oy, color, scale)
        cursor += (len(g[0]) + 1) * scale


def card_face(rank_label: str, suit_key: str, color: tuple[int, int, int]) -> list[list[tuple[int, int, int, int]]]:
    px = blank()
    border = (30, 30, 30, 255)
    fill_rect(px, 0, 0, WIDTH, 1, border)
    fill_rect(px, 0, HEIGHT - 1, WIDTH, HEIGHT, border)
    fill_rect(px, 0, 0, 1, HEIGHT, border)
    fill_rect(px, WIDTH - 1, 0, WIDTH, HEIGHT, border)
    ink = (*color, 255)
    draw_text(px, rank_label, 3, 3, ink, 2 if len(rank_label) == 1 else 1)
    draw_glyph(px, SUIT_GLYPHS[suit_key], 14, 20, ink, 2)
    draw_text(px, rank_label, WIDTH - 3 - (6 if len(rank_label) == 1 else 10) * (2 if len(rank_label) == 1 else 1), HEIGHT - 18, ink, 2 if len(rank_label) == 1 else 1)
    return px


def card_back() -> list[list[tuple[int, int, int, int]]]:
    px = blank((25, 55, 110, 255))
    border = (200, 200, 210, 255)
    fill_rect(px, 0, 0, WIDTH, 2, border)
    fill_rect(px, 0, HEIGHT - 2, WIDTH, HEIGHT, border)
    fill_rect(px, 0, 0, 2, HEIGHT, border)
    fill_rect(px, WIDTH - 2, 0, WIDTH, HEIGHT, border)
    accent = (40, 90, 160, 255)
    for y in range(4, HEIGHT - 4, 4):
        for x in range(4, WIDTH - 4, 4):
            if (x + y) % 8 == 0:
                fill_rect(px, x, y, x + 2, y + 2, accent)
    return px


def joker(kind: str) -> list[list[tuple[int, int, int, int]]]:
    color = (40, 120, 40) if kind == "joker_bw" else (160, 40, 160)
    px = blank()
    border = (30, 30, 30, 255)
    fill_rect(px, 0, 0, WIDTH, 1, border)
    fill_rect(px, 0, HEIGHT - 1, WIDTH, HEIGHT, border)
    fill_rect(px, 0, 0, 1, HEIGHT, border)
    fill_rect(px, WIDTH - 1, 0, WIDTH, HEIGHT, border)
    ink = (*color, 255)
    draw_text(px, "J", 4, 4, ink, 2)
    draw_text(px, "K", 4, 20, ink, 2)
    return px


def main() -> None:
    TEX.mkdir(parents=True, exist_ok=True)
    providers: list[dict] = []
    codepoint = 0xE000

    def add(name: str, pixels: list[list[tuple[int, int, int, int]]]) -> int:
        nonlocal codepoint
        path = TEX / f"{name}.png"
        png_rgba(pixels, path)
        cp = codepoint
        providers.append(
            {
                "type": "bitmap",
                "file": f"minecard:font/cards/{name}.png",
                "ascent": 48,
                "height": 56,
                "chars": [chr(cp)],
            }
        )
        codepoint += 1
        return cp

    mapping: dict[str, int] = {}
    for suit_id, suit_key, color, _ in SUITS:
        for rank_id, rank_label in RANKS:
            name = f"{suit_id}_{rank_id}"
            mapping[name] = add(name, card_face(rank_label, suit_key, color))

    mapping["back"] = add("back", card_back())
    mapping["joker_color"] = add("joker_color", joker("joker_color"))
    mapping["joker_bw"] = add("joker_bw", joker("joker_bw"))

    FONT_JSON.parent.mkdir(parents=True, exist_ok=True)
    FONT_JSON.write_text(json.dumps({"providers": providers}, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    map_path = ROOT / "src" / "main" / "resources" / "assets" / "minecard" / "cards_codepoints.json"
    map_path.write_text(json.dumps(mapping, indent=2) + "\n", encoding="utf-8")
    print(f"Wrote {len(mapping)} textures and {FONT_JSON}")


if __name__ == "__main__":
    main()
