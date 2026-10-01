#!/usr/bin/env python3
"""Import an external deck into Minecard font textures + cards.json."""

from __future__ import annotations

import argparse
import json
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TEX = ROOT / "src" / "main" / "resources" / "assets" / "minecard" / "textures" / "font" / "cards"
FONT_JSON = ROOT / "src" / "main" / "resources" / "assets" / "minecard" / "font" / "cards.json"
CODEPOINTS = ROOT / "src" / "main" / "resources" / "assets" / "minecard" / "cards_codepoints.json"
ACTIVE = ROOT / "assets" / "ACTIVE_DECK.txt"

SUITS = ("spades", "hearts", "diamonds", "clubs")
RANKS = ("a", "2", "3", "4", "5", "6", "7", "8", "9", "10", "j", "q", "k")

# source_path relative to assets/ -> our texture key
DECKS: dict[str, dict[str, str]] = {
	"kenney_large": {
		"label": "Kenney Playing Cards Pack (large, CC0)",
		"map": {
			**{
				f"{suit}_{rank}": f"playing-cards-pack/PNG/Cards (large)/card_{suit}_{'A' if rank == 'a' else 'J' if rank == 'j' else 'Q' if rank == 'q' else 'K' if rank == 'k' else rank.zfill(2) if rank.isdigit() and len(rank) == 1 else rank}.png"
				for suit in SUITS
				for rank in RANKS
			},
			"back": "playing-cards-pack/PNG/Cards (large)/card_back.png",
			"joker_color": "playing-cards-pack/PNG/Cards (large)/card_joker_red.png",
			"joker_bw": "playing-cards-pack/PNG/Cards (large)/card_joker_black.png",
		},
	},
	"kins": {
		"label": "KIN's Playing Cards",
		"map": {
			**{
				f"{suit}_{rank}": f"KINs_Playing_Cards/{suit.capitalize()}_{'ACE' if rank == 'a' else rank.upper() if rank in 'jqk' else rank}.png"
				for suit in SUITS
				for rank in RANKS
			},
			"back": "KINs_Playing_Cards/Back_1.png",
			"joker_color": "KINs_Playing_Cards/Joker_1.png",
			"joker_bw": "KINs_Playing_Cards/Joker_2.png",
		},
	},
	"inscryption": {
		"label": "Inscryption Inspired Deck",
		"map": {
			**{
				f"{suit}_{rank}": (
					f"Inscryption Inspired - Deck of Playing Cards/"
					f"{'ace' if rank == 'a' else 'jack' if rank == 'j' else 'queen' if rank == 'q' else 'king' if rank == 'k' else rank}"
					f"_of_{suit}.png"
				)
				for suit in SUITS
				for rank in RANKS
			},
			"back": "Inscryption Inspired - Deck of Playing Cards/card_back.png",
			"joker_color": "Inscryption Inspired - Deck of Playing Cards/joker_red.png",
			"joker_bw": "Inscryption Inspired - Deck of Playing Cards/joker_black.png",
		},
	},
}


def fix_kenney_rank(rank: str) -> str:
	if rank == "a":
		return "A"
	if rank == "j":
		return "J"
	if rank == "q":
		return "Q"
	if rank == "k":
		return "K"
	if rank == "10":
		return "10"
	return rank.zfill(2)


def kenney_map(size: str) -> dict[str, str]:
	out: dict[str, str] = {}
	for suit in SUITS:
		for rank in RANKS:
			src_rank = fix_kenney_rank(rank)
			out[f"{suit}_{rank}"] = f"playing-cards-pack/PNG/Cards ({size})/card_{suit}_{src_rank}.png"
	out["back"] = f"playing-cards-pack/PNG/Cards ({size})/card_back.png"
	out["joker_color"] = f"playing-cards-pack/PNG/Cards ({size})/card_joker_red.png"
	out["joker_bw"] = f"playing-cards-pack/PNG/Cards ({size})/card_joker_black.png"
	return out


DECKS["kenney_large"]["map"] = kenney_map("large")
DECKS["kenney_medium"] = {
	"label": "Kenney Playing Cards Pack (medium, CC0)",
	"map": kenney_map("medium"),
}


def png_size(path: Path) -> tuple[int, int]:
	data = path.read_bytes()
	import struct

	return struct.unpack(">II", data[16:24])


def write_font(keys: list[str], height: int) -> dict[str, int]:
	providers = []
	mapping: dict[str, int] = {}
	codepoint = 0xE000
	ascent = max(1, height - 8)
	for key in keys:
		providers.append(
			{
				"type": "bitmap",
				"file": f"minecard:font/cards/{key}.png",
				"ascent": ascent,
				"height": height,
				"chars": [chr(codepoint)],
			}
		)
		mapping[key] = codepoint
		codepoint += 1
	FONT_JSON.parent.mkdir(parents=True, exist_ok=True)
	FONT_JSON.write_text(json.dumps({"providers": providers}, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
	CODEPOINTS.write_text(json.dumps(mapping, indent=2) + "\n", encoding="utf-8")
	return mapping


def import_deck(deck_id: str) -> None:
	if deck_id not in DECKS:
		raise SystemExit(f"Unknown deck {deck_id}. Choose from: {', '.join(DECKS)}")
	deck = DECKS[deck_id]
	TEX.mkdir(parents=True, exist_ok=True)
	for old in TEX.glob("*.png"):
		old.unlink()

	for key, rel in deck["map"].items():
		src = ROOT / "assets" / rel
		if not src.is_file():
			raise FileNotFoundError(src)
		dst = TEX / f"{key}.png"
		shutil.copy2(src, dst)

	# stable order: 52 faces then extras
	ordered = [f"{s}_{r}" for s in SUITS for r in RANKS]
	for extra in ("back", "joker_color", "joker_bw"):
		if extra in deck["map"]:
			ordered.append(extra)

	sample = TEX / f"{ordered[0]}.png"
	_w, h = png_size(sample)
	write_font(ordered, h)
	ACTIVE.write_text(f"{deck_id}\n{deck['label']}\n", encoding="utf-8")
	print(f"Imported {deck_id}: {deck['label']}")
	print(f"  textures -> {TEX}")
	print(f"  face size: {_w}x{h}")
	print(f"  faces: {sum(1 for k in ordered if k not in ('back', 'joker_color', 'joker_bw'))}")


def main() -> None:
	parser = argparse.ArgumentParser()
	parser.add_argument("deck", nargs="?", default="kenney_large", choices=sorted(DECKS.keys()))
	parser.add_argument("--list", action="store_true")
	args = parser.parse_args()
	if args.list:
		for k, v in DECKS.items():
			print(f"{k}: {v['label']}")
		return
	import_deck(args.deck)


if __name__ == "__main__":
	main()
