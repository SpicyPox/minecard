package com.spicypox.minecard.pack;

import com.spicypox.minecard.Minecard;
import net.fabricmc.loader.api.FabricLoader;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Builds a vanilla-compatible resource pack zip containing Minecard card font glyphs.
 */
public final class CardResourcePack {
	public static final UUID PACK_ID = UUID.fromString("6e1c4a20-52c0-4d0e-9f11-01aeca5d0001");

	private static final String PACK_MCMETA = """
		{
			"pack": {
				"description": "Minecard 52-card faces",
				"min_format": [97, 1],
				"max_format": [97, 1]
			}
		}
		""";

	private static final String[] ASSET_PATHS = buildAssetPaths();

	private final Path zipPath;
	private final String sha1Hex;
	private final byte[] bytes;

	private CardResourcePack(Path zipPath, byte[] bytes, String sha1Hex) {
		this.zipPath = zipPath;
		this.bytes = bytes;
		this.sha1Hex = sha1Hex;
	}

	public static CardResourcePack build() {
		try {
			byte[] zip = createZipBytes();
			String sha1 = sha1Hex(zip);
			Path out = FabricLoader.getInstance().getConfigDir().resolve(Minecard.MOD_ID).resolve("minecard-cards.zip");
			Files.createDirectories(out.getParent());
			Files.write(out, zip);
			Minecard.LOGGER.info("Wrote card resource pack {} ({} bytes, sha1={})", out, zip.length, sha1);
			return new CardResourcePack(out, zip, sha1);
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to build Minecard resource pack", e);
		}
	}

	public Path zipPath() {
		return zipPath;
	}

	public byte[] bytes() {
		return bytes;
	}

	public String sha1Hex() {
		return sha1Hex;
	}

	public UUID id() {
		return PACK_ID;
	}

	/**
	 * CLI: {@code java -cp minecard.jar com.spicypox.minecard.pack.CardResourcePack out.zip}
	 * Writes the same zip the live server offers (for HTTPS hosting on GitHub Releases).
	 */
	public static void main(String[] args) throws Exception {
		if (args.length < 1) {
			System.err.println("Usage: CardResourcePack <out.zip>");
			System.exit(1);
		}
		byte[] zip = createZipBytes();
		Path out = Path.of(args[0]);
		Path parent = out.toAbsolutePath().getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}
		Files.write(out, zip);
		System.out.println(sha1Hex(zip));
	}

	static byte[] createZipBytes() throws IOException {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		try (ZipOutputStream zos = new ZipOutputStream(bos)) {
			put(zos, "pack.mcmeta", textBytes(PACK_MCMETA));
			for (String asset : ASSET_PATHS) {
				try (InputStream in = CardResourcePack.class.getClassLoader().getResourceAsStream(asset)) {
					if (in == null) {
						throw new IOException("Missing classpath resource: " + asset);
					}
					byte[] raw = in.readAllBytes();
					put(zos, asset, isTextAsset(asset) ? normalizeLf(raw) : raw);
				}
			}
		}
		return bos.toByteArray();
	}

	private static boolean isTextAsset(String path) {
		return path.endsWith(".json") || path.endsWith(".mcmeta") || path.endsWith(".txt");
	}

	/** LF-only so Windows checkout and Linux CI produce the same zip SHA-1. */
	private static byte[] normalizeLf(byte[] raw) {
		String s = new String(raw, StandardCharsets.UTF_8).replace("\r\n", "\n").replace("\r", "\n");
		return s.getBytes(StandardCharsets.UTF_8);
	}

	private static byte[] textBytes(String text) {
		return normalizeLf(text.getBytes(StandardCharsets.UTF_8));
	}

	private static void put(ZipOutputStream zos, String name, byte[] data) throws IOException {
		ZipEntry entry = new ZipEntry(name);
		// Fixed timestamp so SHA-1 is stable across restarts (avoids clients re-downloading identical content).
		entry.setTime(0L);
		zos.putNextEntry(entry);
		zos.write(data);
		zos.closeEntry();
	}

	private static String sha1Hex(byte[] data) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-1");
			return HexFormat.of().formatHex(digest.digest(data));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	private static String[] buildAssetPaths() {
		String[] suits = {"spades", "hearts", "diamonds", "clubs"};
		String[] ranks = {"a", "2", "3", "4", "5", "6", "7", "8", "9", "10", "j", "q", "k"};
		String[] paths = new String[2 + suits.length * ranks.length + 3];
		int i = 0;
		paths[i++] = "assets/minecard/font/cards.json";
		paths[i++] = "assets/minecard/cards_codepoints.json";
		for (String suit : suits) {
			for (String rank : ranks) {
				paths[i++] = "assets/minecard/textures/font/cards/" + suit + "_" + rank + ".png";
			}
		}
		paths[i++] = "assets/minecard/textures/font/cards/back.png";
		paths[i++] = "assets/minecard/textures/font/cards/joker_color.png";
		paths[i++] = "assets/minecard/textures/font/cards/joker_bw.png";
		return paths;
	}
}
