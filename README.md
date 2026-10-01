# Minecard

Fabric 26.3 server-side gambling rooms. Vanilla clients can join.

Design: [`doc/ke-hoach.md`](doc/ke-hoach.md)  
Implementation order: [`doc/implement.md`](doc/implement.md)

## Milestone 1

`/minecard cards` opens a dialog with all 52 card faces (bitmap font glyphs) plus labels.

Active test deck: **Kenney Playing Cards Pack (large, CC0)** — import with:

```bat
python tools\import_deck.py kenney_large
python tools\import_deck.py --list
```

Source packs live under `assets/`. Imported textures go to `src/main/resources/assets/minecard/textures/font/cards/`.

Requires Java 25+, Fabric Loader 0.19.5+, Fabric API for 26.3.

```bat
gradlew build
gradlew runClient
```

In-game: accept the Minecard resource pack, then `/minecard cards`.

For a dedicated server on LAN, pass the reachable host: `-Dminecard.packHost=192.168.x.x`.
