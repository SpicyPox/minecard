package com.spicypox.minecard.pack;

import com.spicypox.minecard.Minecard;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

/**
 * Serves the Minecard card resource pack to vanilla clients over HTTP.
 */
public final class PackHttpServer implements AutoCloseable {
	private final HttpServer server;
	private final int port;

	private PackHttpServer(HttpServer server, int port) {
		this.server = server;
		this.port = port;
	}

	public static PackHttpServer start(CardResourcePack pack, int port) throws IOException {
		HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
		byte[] bytes = pack.bytes();
		server.createContext("/minecard-cards.zip", exchange -> {
			if (!"GET".equalsIgnoreCase(exchange.getRequestMethod()) && !"HEAD".equalsIgnoreCase(exchange.getRequestMethod())) {
				exchange.sendResponseHeaders(405, -1);
				exchange.close();
				return;
			}
			exchange.getResponseHeaders().add("Content-Type", "application/zip");
			exchange.sendResponseHeaders(200, bytes.length);
			if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
				try (OutputStream os = exchange.getResponseBody()) {
					os.write(bytes);
				}
			} else {
				exchange.close();
			}
		});
		server.setExecutor(Executors.newCachedThreadPool());
		server.start();
		Minecard.LOGGER.info("Serving Minecard card pack at http://127.0.0.1:{}/minecard-cards.zip", port);
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
