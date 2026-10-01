package com.spicypox.minecard.wallet;

import net.minecraft.resources.Identifier;

/**
 * Inventory-side stake source. Only plain stacks matching item id (no custom components).
 */
public interface ItemSource {
	long count(Identifier itemId);

	/** Remove up to {@code amount}; return how many were actually removed. */
	long take(Identifier itemId, long amount);

	/**
	 * Return items to the source. Returns the count that could not fit
	 * (caller may credit wallet instead — never delete).
	 */
	long give(Identifier itemId, long amount);

	static ItemSource empty() {
		return new ItemSource() {
			@Override
			public long count(Identifier itemId) {
				return 0L;
			}

			@Override
			public long take(Identifier itemId, long amount) {
				return 0L;
			}

			@Override
			public long give(Identifier itemId, long amount) {
				return amount;
			}
		};
	}
}
