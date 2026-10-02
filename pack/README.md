# Card pack zip (CDN)

`minecard-cards.zip` is committed so vanilla clients can Accept a **direct HTTPS** URL:

- `https://cdn.jsdelivr.net/gh/SpicyPox/minecard@vX.Y.Z/pack/minecard-cards.zip`
- `https://raw.githubusercontent.com/SpicyPox/minecard/vX.Y.Z/pack/minecard-cards.zip`

Do **not** use GitHub `releases/download/...` for in-game Accept — it 302s to short-lived S3 URLs and fails in Minecraft (browsers still work).

Regenerate after changing card assets:

```bash
./gradlew generateCardPack
```

Commit the updated zip before tagging a release.
