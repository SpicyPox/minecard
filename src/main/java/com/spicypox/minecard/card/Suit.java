package com.spicypox.minecard.card;

public enum Suit {
	SPADES("spades", "bích"),
	HEARTS("hearts", "cơ"),
	DIAMONDS("diamonds", "rô"),
	CLUBS("clubs", "chuồn");

	private final String id;
	private final String viName;

	Suit(String id, String viName) {
		this.id = id;
		this.viName = viName;
	}

	public String id() {
		return id;
	}

	public String viName() {
		return viName;
	}
}
