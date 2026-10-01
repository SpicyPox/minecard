# Minecard

Fabric 26.3 server-side gambling rooms. Vanilla clients can join.

Design: [`doc/ke-hoach.md`](doc/ke-hoach.md)  
Implementation order: [`doc/implement.md`](doc/implement.md)

## Milestone 1

`/minecard cards` opens a dialog with all 52 card faces (bitmap font glyphs) plus labels.

Requires Java 25+, Fabric Loader 0.19.5+, Fabric API for 26.3.

```bat
gradlew build
gradlew runClient
```

In-game: `/minecard cards`
