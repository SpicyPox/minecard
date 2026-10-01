package com.spicypox.minecard.card;

public enum Rank {
	ACE("a", "A", "Át"),
	TWO("2", "2", "2"),
	THREE("3", "3", "3"),
	FOUR("4", "4", "4"),
	FIVE("5", "5", "5"),
	SIX("6", "6", "6"),
	SEVEN("7", "7", "7"),
	EIGHT("8", "8", "8"),
	NINE("9", "9", "9"),
	TEN("10", "10", "10"),
	JACK("j", "J", "J"),
	QUEEN("q", "Q", "Q"),
	KING("k", "K", "K");

	private final String id;
	private final String shortLabel;
	private final String viLabel;

	Rank(String id, String shortLabel, String viLabel) {
		this.id = id;
		this.shortLabel = shortLabel;
		this.viLabel = viLabel;
	}

	public String id() {
		return id;
	}

	public String shortLabel() {
		return shortLabel;
	}

	public String viLabel() {
		return viLabel;
	}
}
