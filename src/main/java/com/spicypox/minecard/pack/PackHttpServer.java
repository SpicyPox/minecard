package com.spicypox.minecard.pack;

import com.spicypox.minecard.Minecard;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

/**
 * Serves the Minecard card resource pack over HTTP on all interfaces (0.0.0.0).
 * Use for browser/manual install on a VPS — vanilla will not Accept cleartext HTTP to a public IP.
 */
public final class PackHttpServer implements AutoCloseable {
	private final HttpServer server;
	private final int port;

	private PackHttpServer(HttpServer server, int port) {
		this.server = server;
		this.port = port;
	}

	public static PackHttpServer start(CardResourcePack pack, int port) throws IOException {
		// Bind all interfaces so Ubuntu/public-IP players can reach the zip in a browser.
		HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
		byte[] bytes = pack.bytes();
		server.createContext("/minecard-cards.zip", exchange -> {
			String method = exchange.getRequestMethod();
			if (!"GET".equalsIgnoreCase(method) && !"HEAD".equalsIgnoreCase(method)) {
				exchange.sendResponseHeaders(405, -1);
				exchange.close();
				return;
			}
			Headers headers = exchange.getResponseHeaders();
			headers.set("Content-Type", "application/zip");
			headers.set("Content-Disposition", "attachment; filename=\"minecard-cards.zip\"");
			headers.set("Cache-Control", "public, max-age=300");
			headers.set("Access-Control-Allow-Origin", "*");
			exchange.sendResponseHeaders(200, bytes.length);
			if ("GET".equalsIgnoreCase(method)) {
				try (OutputStream os = exchange.getResponseBody()) {
					os.write(bytes);
				}
			} else {
				exchange.close();
			}
		});
		server.setExecutor(Executors.newCachedThreadPool());
		server.start();
		Minecard.LOGGER.info(
			"Serving Minecard card pack HTTP (browser/manual) on 0.0.0.0:{} — "
				+ "vanilla in-game Accept still needs a direct HTTPS URL (jsDelivr / packUrl)",
			port
		);
		return new PackHttpServer(server, port);
	}

	public int port() {
		return port;
	}

	public String localUrl() {
		return "http://127.0.0.1:" + port + "/minecard-cards.zip";
	}

	@Override
	public void close() {
		server.stop(0);
	}
}
