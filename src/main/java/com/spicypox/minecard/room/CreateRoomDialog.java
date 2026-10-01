package com.spicypox.minecard.room;

import com.spicypox.minecard.Minecard;
import com.spicypox.minecard.config.MinecardConfig;
import com.spicypox.minecard.game.blackjack.StakeItem;
import com.spicypox.minecard.ui.CardLayer;
import com.spicypox.minecard.ui.DialogUi;
import com.spicypox.minecard.ui.Dialogs;
import com.spicypox.minecard.ui.MainMenuDialog;
import com.spicypox.minecard.wallet.Wallets;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.CommonButtonData;
import net.minecraft.server.dialog.CommonDialogData;
import net.minecraft.server.dialog.DialogAction;
import net.minecraft.server.dialog.Input;
import net.minecraft.server.dialog.MultiActionDialog;
import net.minecraft.server.dialog.action.CustomAll;
import net.minecraft.server.dialog.body.DialogBody;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.dialog.input.BooleanInput;
import net.minecraft.server.dialog.input.NumberRangeInput;
import net.minecraft.server.dialog.input.SingleOptionInput;
import net.minecraft.server.dialog.input.TextInput;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Multi-step create-room wizard (game → BJ settings → stake). */
public final class CreateRoomDialog {
	public static final Identifier GAME_BJ = Identifier.fromNamespaceAndPath(Minecard.MOD_ID, "create/game_bj");
	public static final Identifier GAME_POKER = Identifier.fromNamespaceAndPath(Minecard.MOD_ID, "create/game_poker");
	public static final Identifier SETTINGS_NEXT = Identifier.fromNamespaceAndPath(Minecard.MOD_ID, "create/settings_next");
	public static final Identifier SETTINGS_BACK = Identifier.fromNamespaceAndPath(Minecard.MOD_ID, "create/settings_back");
	public static final Identifier STAKE_CREATE = Identifier.fromNamespaceAndPath(Minecard.MOD_ID, "create/stake_create");
	public static final Identifier STAKE_BACK = Identifier.fromNamespaceAndPath(Minecard.MOD_ID, "create/stake_back");
	public static final Identifier CANCEL = Identifier.fromNamespaceAndPath(Minecard.MOD_ID, "create/cancel");
	public static final Identifier POKER_BACK = Identifier.fromNamespaceAndPath(Minecard.MOD_ID, "create/poker_back");

	private CreateRoomDialog() {
	}

	public static void openGameSelect(ServerPlayer player) {
		CreateRoomDraft.getOrCreate(player.getUUID());
		player.openDialog(Holder.direct(buildGameSelect()));
	}

	public static void openBjSettings(ServerPlayer player) {
		CreateRoomDraft draft = CreateRoomDraft.getOrCreate(player.getUUID());
		draft.setGame(CreateRoomDraft.Game.BLACKJACK);
		player.openDialog(Holder.direct(buildBjSettings(draft)));
	}

	public static void openStake(ServerPlayer player) {
		CreateRoomDraft draft = CreateRoomDraft.getOrCreate(player.getUUID());
		player.openDialog(Holder.direct(buildStake(player, draft)));
	}

	public static void openPokerSoon(ServerPlayer player) {
		player.openDialog(Holder.direct(buildPokerSoon()));
	}

	public static boolean onClick(ServerPlayer player, String action, CompoundTag payload) {
		return switch (action) {
			case "game_bj" -> {
				openBjSettings(player);
				yield true;
			}
			case "game_poker" -> {
				openPokerSoon(player);
				yield true;
			}
			case "poker_back", "settings_back" -> {
				openGameSelect(player);
				yield true;
			}
			case "settings_next" -> {
				applySettings(CreateRoomDraft.getOrCreate(player.getUUID()), payload);
				openStake(player);
				yield true;
			}
			case "stake_back" -> {
				openBjSettings(player);
				yield true;
			}
			case "stake_create" -> {
				CreateRoomDraft draft = CreateRoomDraft.getOrCreate(player.getUUID());
				if (!applyStake(player, draft, payload)) {
					openStake(player);
					yield true;
				}
				Rooms.createFromDraft(player, draft);
				yield true;
			}
			case "cancel" -> {
				CreateRoomDraft.clear(player.getUUID());
				Dialogs.clear(player);
				MainMenuDialog.open(player);
				yield true;
			}
			default -> false;
		};
	}

	private static MultiActionDialog buildGameSelect() {
		int w = DialogUi.BUTTON_WIDTH;
		List<DialogBody> body = List.of(
			new PlainMessage(Component.translatable("minecard.create.game.hint"), CardLayer.DIALOG_WIDTH)
		);
		List<ActionButton> actions = List.of(
			button("minecard.create.game.blackjack", GAME_BJ, w),
			button("minecard.create.game.poker", GAME_POKER, w)
		);
		return dialog("minecard.create.game.title", body, List.of(), actions, cancel(w));
	}

	private static MultiActionDialog buildPokerSoon() {
		int w = DialogUi.BUTTON_WIDTH;
		List<DialogBody> body = List.of(
			new PlainMessage(Component.translatable("minecard.create.poker.soon"), CardLayer.DIALOG_WIDTH)
		);
		List<ActionButton> actions = List.of(button("minecard.create.back", POKER_BACK, w));
		return dialog("minecard.create.game.poker", body, List.of(), actions, cancel(w));
	}

	private static MultiActionDialog buildBjSettings(CreateRoomDraft draft) {
		BjRoomRules r = draft.rules();
		int w = DialogUi.BUTTON_WIDTH;
		List<DialogBody> body = List.of(
			new PlainMessage(Component.translatable("minecard.create.settings.hint"), CardLayer.DIALOG_WIDTH)
		);
		List<Input> inputs = List.of(
			new Input(
				"turn_seconds",
				new NumberRangeInput(
					w,
					Component.translatable("minecard.create.settings.timer"),
					"options.generic_value",
					new NumberRangeInput.RangeInfo(5f, 120f, Optional.of((float) r.turnSeconds()), Optional.of(1f))
				)
			),
			new Input(
				"decks",
				new NumberRangeInput(
					w,
					Component.translatable("minecard.create.settings.decks"),
					"options.generic_value",
					new NumberRangeInput.RangeInfo(1f, 8f, Optional.of((float) r.decks()), Optional.of(1f))
				)
			),
			new Input(
				"max_players",
				new NumberRangeInput(
					w,
					Component.translatable("minecard.create.settings.seats"),
					"options.generic_value",
					new NumberRangeInput.RangeInfo(1f, (float) BjRoom.MAX_PLAYERS, Optional.of((float) r.maxPlayers()), Optional.of(1f))
				)
			),
			new Input(
				"insurance",
				new BooleanInput(
					Component.translatable("minecard.create.settings.insurance"),
					r.insuranceEnabled(),
					"true",
					"false"
				)
			),
			new Input(
				"soft17",
				new BooleanInput(
					Component.translatable("minecard.create.settings.soft17"),
					r.dealerHitsSoft17(),
					"true",
					"false"
				)
			),
			new Input(
				"surrender",
				new BooleanInput(
					Component.translatable("minecard.create.settings.surrender"),
					r.surrenderEnabled(),
					"true",
					"false"
				)
			)
		);
		List<ActionButton> actions = List.of(
			button("minecard.create.next", SETTINGS_NEXT, w),
			button("minecard.create.back", SETTINGS_BACK, w)
		);
		return dialog("minecard.create.settings.title", body, inputs, actions, cancel(w));
	}

	private static MultiActionDialog buildStake(ServerPlayer player, CreateRoomDraft draft) {
		int w = DialogUi.BUTTON_WIDTH;
		List<SingleOptionInput.Entry> items = stakeOptions(player, draft.stakeItem());
		if (items.isEmpty()) {
			items = List.of(new SingleOptionInput.Entry(
				StakeItem.DEFAULT_ID.toString(),
				Optional.of(StakeItem.displayName(StakeItem.DEFAULT_ID)),
				true
			));
		}
		long initialBet = draft.betAmount() > 0 ? draft.betAmount() : Math.max(1L, MinecardConfig.defaultBet);
		List<DialogBody> body = List.of(
			new PlainMessage(Component.translatable("minecard.create.stake.hint"), CardLayer.DIALOG_WIDTH),
			new PlainMessage(Component.translatable("minecard.create.stake.check_order"), CardLayer.DIALOG_WIDTH)
		);
		// Item = SingleOptionInput (vanilla dropdown/cycle). Amount = TextInput (typed number).
		List<Input> inputs = List.of(
			new Input(
				"bet",
				new TextInput(
					w,
					Component.translatable("minecard.create.stake.amount"),
					true,
					Long.toString(initialBet),
					10,
					Optional.empty()
				)
			),
			new Input(
				"item",
				new SingleOptionInput(
					w,
					items,
					Component.translatable("minecard.create.stake.item"),
					true
				)
			)
		);
		List<ActionButton> actions = List.of(
			button("minecard.create.confirm", STAKE_CREATE, w),
			button("minecard.create.back", STAKE_BACK, w)
		);
		return dialog("minecard.create.stake.title", body, inputs, actions, cancel(w));
	}

	private static List<SingleOptionInput.Entry> stakeOptions(ServerPlayer player, Identifier preferred) {
		Map<String, Component> options = new LinkedHashMap<>();
		for (Map.Entry<Identifier, Long> e : Wallets.listBalances(player.getUUID())) {
			if (e.getValue() > 0L) {
				options.put(e.getKey().toString(), Component.translatable(
					"minecard.create.stake.option_balance",
					StakeItem.displayName(e.getKey()),
					e.getValue()
				));
			}
		}
		addHandOption(options, player.getMainHandItem(), "minecard.create.stake.option_main");
		addHandOption(options, player.getOffhandItem(), "minecard.create.stake.option_off");
		if (options.isEmpty()) {
			return List.of();
		}
		List<SingleOptionInput.Entry> entries = new ArrayList<>();
		String pref = preferred != null ? preferred.toString() : "";
		boolean anyInitial = false;
		int i = 0;
		for (Map.Entry<String, Component> e : options.entrySet()) {
			if (i >= 16) {
				break;
			}
			boolean initial = e.getKey().equals(pref) || (!anyInitial && pref.isEmpty() && i == 0);
			if (initial) {
				anyInitial = true;
			}
			entries.add(new SingleOptionInput.Entry(e.getKey(), Optional.of(e.getValue()), initial));
			i++;
		}
		if (!anyInitial && !entries.isEmpty()) {
			SingleOptionInput.Entry first = entries.get(0);
			entries.set(0, new SingleOptionInput.Entry(first.id(), first.display(), true));
		}
		return entries;
	}

	private static void addHandOption(Map<String, Component> options, ItemStack stack, String key) {
		if (stack.isEmpty() || !isPlain(stack)) {
			return;
		}
		Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
		options.putIfAbsent(id.toString(), Component.translatable(
			key,
			StakeItem.displayName(id),
			stack.getCount()
		));
	}

	private static boolean isPlain(ItemStack stack) {
		return ItemStack.isSameItemSameComponents(stack, new ItemStack(stack.getItem()));
	}

	private static void applySettings(CreateRoomDraft draft, CompoundTag payload) {
		int turn = Math.round(payload.getFloatOr("turn_seconds", draft.rules().turnSeconds()));
		int decks = Math.round(payload.getFloatOr("decks", draft.rules().decks()));
		int seats = Math.round(payload.getFloatOr("max_players", draft.rules().maxPlayers()));
		boolean insurance = readBool(payload, "insurance", draft.rules().insuranceEnabled());
		boolean soft17 = readBool(payload, "soft17", draft.rules().dealerHitsSoft17());
		boolean surrender = readBool(payload, "surrender", draft.rules().surrenderEnabled());
		draft.setRules(new BjRoomRules(turn, decks, insurance, soft17, surrender, seats));
	}

	/** @return false if amount text is invalid */
	private static boolean applyStake(ServerPlayer player, CreateRoomDraft draft, CompoundTag payload) {
		String betText = payload.getStringOr("bet", Long.toString(draft.betAmount())).trim();
		long bet;
		try {
			bet = Long.parseLong(betText.replace(",", "").replace("_", ""));
		} catch (NumberFormatException e) {
			player.sendSystemMessage(Component.translatable("minecard.create.stake.amount_invalid"));
			return false;
		}
		if (bet < 1L || bet > 1_000_000L) {
			player.sendSystemMessage(Component.translatable("minecard.create.stake.amount_invalid"));
			return false;
		}
		draft.setBetAmount(bet);
		String item = payload.getStringOr("item", draft.stakeItem().toString());
		Identifier id = Identifier.tryParse(item);
		if (id == null) {
			id = StakeItem.DEFAULT_ID;
		}
		draft.setStakeItem(id);
		return true;
	}

	private static boolean readBool(CompoundTag tag, String key, boolean def) {
		Optional<Boolean> b = tag.getBoolean(key);
		if (b.isPresent()) {
			return b.get();
		}
		String s = tag.getStringOr(key, def ? "true" : "false");
		return "true".equalsIgnoreCase(s) || "1".equals(s);
	}

	private static MultiActionDialog dialog(
		String titleKey,
		List<DialogBody> body,
		List<Input> inputs,
		List<ActionButton> actions,
		ActionButton exit
	) {
		CommonDialogData data = new CommonDialogData(
			Component.translatable(titleKey),
			Optional.empty(),
			true,
			false,
			DialogAction.NONE,
			body,
			inputs
		);
		return new MultiActionDialog(data, actions, Optional.of(exit), 1);
	}

	private static ActionButton cancel(int w) {
		return button("minecard.create.cancel", CANCEL, w);
	}

	private static ActionButton button(String key, Identifier id, int width) {
		return new ActionButton(
			new CommonButtonData(Component.translatable(key), width),
			Optional.of(new CustomAll(id, Optional.empty()))
		);
	}
}
