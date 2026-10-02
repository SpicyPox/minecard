package com.spicypox.minecard.pack;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CardPackOffersTest {
	@Test
	void canAutoPushHttpsAndLoopbackOnly() {
		assertTrue(CardPackOffers.canAutoPush("https://cdn.jsdelivr.net/gh/SpicyPox/minecard@v0.1.8/pack/minecard-cards.zip"));
		assertTrue(CardPackOffers.canAutoPush("http://127.0.0.1:8765/minecard-cards.zip"));
		assertTrue(CardPackOffers.canAutoPush("http://localhost:8765/minecard-cards.zip"));
		assertFalse(CardPackOffers.canAutoPush("http://203.0.113.10:8765/minecard-cards.zip"));
		assertFalse(CardPackOffers.canAutoPush("http://192.168.0.211:8765/minecard-cards.zip"));
	}

	@Test
	void directHttpsCandidatesPreferCdnNotReleaseDownload() {
		List<String> urls = CardPackOffers.directHttpsCandidates("0.1.8");
		assertEquals(2, urls.size());
		assertTrue(urls.get(0).contains("cdn.jsdelivr.net"));
		assertTrue(urls.get(1).contains("raw.githubusercontent.com"));
		assertTrue(urls.stream().noneMatch(u -> u.contains("releases/download")));
	}
}
