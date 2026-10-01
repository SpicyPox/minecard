#!/usr/bin/env python3
"""Automated checks for milestone 1 card GUI assets."""

from __future__ import annotations

import json
import struct
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TEX = ROOT / "src/main/resources/assets/minecard/textures/font/cards"
FONT = ROOT / "src/main/resources/assets/minecard/font/cards.json"
CODEPOINTS = ROOT / "src/main/resources/assets/minecard/cards_codepoints.json"
JAR = ROOT / "build/libs/minecard-0.1.0.jar"

SUITS = ("spades", "hearts", "diamonds", "clubs")
RANKS = ("a", "2", "3", "4", "5", "6", "7", "8", "9", "10", "j", "q", "k")


def fail(msg: str) -> None:
	print(f"FAIL: {msg}")
	sys.exit(1)


def main() -> None:
	cp = json.loads(CODEPOINTS.read_text(encoding="utf-8"))
	faces = [f"{s}_{r}" for s in SUITS for r in RANKS]
	if len(faces) != 52:
		fail(f"face key list size {len(faces)}")
	missing = [k for k in faces if k not in cp]
	if missing:
		fail(f"missing codepoints: {missing[:5]}")

	hashes: set[bytes] = set()
	for key in faces:
		path = TEX / f"{key}.png"
		if not path.is_file():
			fail(f"missing texture {path}")
		data = path.read_bytes()
		hashes.add(data)
		w, h = struct.unpack(">II", data[16:24])
		if w < 8 or h < 8:
			fail(f"{key} too small {w}x{h}")
	if len(hashes) != 52:
		fail(f"only {len(hashes)} unique PNGs among 52 faces")

	providers = json.loads(FONT.read_text(encoding="utf-8"))["providers"]
	face_providers = [p for p in providers if any(k in p["file"] for k in faces)]
	if len(face_providers) != 52:
		fail(f"font face providers {len(face_providers)}")

	if not (TEX / "back.png").is_file():
		fail("missing back.png")

	if JAR.is_file():
		with zipfile.ZipFile(JAR) as z:
			jar_faces = [
				n
				for n in z.namelist()
				if n.startswith("assets/minecard/textures/font/cards/")
				and n.endswith(".png")
				and "joker" not in n
				and not n.endswith("back.png")
			]
			if len(jar_faces) != 52:
				fail(f"jar faces {len(jar_faces)}")
			if "com/spicypox/minecard/command/MinecardCommands.class" not in z.namelist():
				fail("MinecardCommands missing from jar")
			if "com/spicypox/minecard/ui/CardCatalogDialog.class" not in z.namelist():
				fail("CardCatalogDialog missing from jar")

	print("OK: milestone 1 assets — 52 unique faces + back + font + jar classes")


if __name__ == "__main__":
	main()
