package com.spicypox.minecard.wallet;

/** Stake defaults; overwritten when {@link com.spicypox.minecard.config.MinecardConfig} loads. */
public final class WalletConstants {
	public static long STARTING_BALANCE = 128L;
	public static long DEFAULT_BET = 10L;

	private WalletConstants() {
	}

	public static void syncFromConfig(long startingBalance, long defaultBet) {
		STARTING_BALANCE = startingBalance;
		DEFAULT_BET = defaultBet;
	}
}
