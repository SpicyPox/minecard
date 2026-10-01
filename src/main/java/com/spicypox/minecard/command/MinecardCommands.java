package com.spicypox.minecard.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.spicypox.minecard.game.blackjack.BlackjackGames;
import com.spicypox.minecard.history.HistoryDb;
import com.spicypox.minecard.room.Rooms;
import com.spicypox.minecard.ui.CardCatalogDialog;
import com.spicypox.minecard.ui.CardTableLoop;
import com.spicypox.minecard.ui.MainMenuDialog;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class MinecardCommands {
	private MinecardCommands() {
	}

	public static void register(
		CommandDispatcher<CommandSourceStack> dispatcher,
		CommandBuildContext registry,
		Commands.CommandSelection selection
	) {
		dispatcher.register(
			Commands.literal("minecard")
				.then(Commands.literal("bj")
					.executes(MinecardCommands::startBlackjack))
				.then(Commands.literal("menu")
					.executes(MinecardCommands::openMenu))
				.then(Commands.literal("join")
					.then(Commands.argument("room", StringArgumentType.word())
						.executes(MinecardCommands::joinRoom)))
				.then(Commands.literal("invite")
					.then(Commands.argument("player", EntityArgument.player())
						.executes(MinecardCommands::invitePlayer)))
				.then(Commands.literal("admin")
					.then(Commands.literal("stats")
						.then(Commands.argument("player", EntityArgument.player())
							.executes(MinecardCommands::adminStats)))
					.then(Commands.literal("ledger")
						.then(Commands.argument("player", EntityArgument.player())
							.executes(MinecardCommands::adminLedger)))
					.then(Commands.literal("end")
						.then(Commands.argument("player", EntityArgument.player())
							.executes(MinecardCommands::adminEnd))))
				.then(Commands.literal("cards")
					.then(Commands.literal("poker")
						.executes(ctx -> openShowcase(ctx, CardCatalogDialog.Mode.POKER)))
					.then(Commands.literal("blackjack")
						.executes(MinecardCommands::startBlackjack))
					.then(Commands.literal("loop")
						.executes(CardTableLoopCommands::start))
					.then(Commands.literal("stop")
						.executes(CardTableLoopCommands::stop))
					.executes(ctx -> openShowcase(ctx, CardCatalogDialog.Mode.POKER)))
				.executes(MinecardCommands::openMenu)
		);

		dispatcher.register(Commands.literal("bj").executes(MinecardCommands::startBlackjack));
		dispatcher.register(Commands.literal("pk")
			.executes(ctx -> openShowcase(ctx, CardCatalogDialog.Mode.POKER)));
	}

	private static int openMenu(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		MainMenuDialog.open(player);
		return 1;
	}

	private static int joinRoom(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		String room = StringArgumentType.getString(ctx, "room");
		Rooms.join(player, room);
		return 1;
	}

	private static int invitePlayer(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer host = ctx.getSource().getPlayerOrException();
		ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
		Rooms.invite(host, target);
		return 1;
	}

	private static int adminStats(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		if (!isAdmin(ctx.getSource())) {
			ctx.getSource().sendFailure(Component.literal("OP required"));
			return 0;
		}
		ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
		String line = HistoryDb.statsLine(target.getUUID());
		ctx.getSource().sendSuccess(
			() -> Component.literal(target.getGameProfile().name() + " · " + line),
			false
		);
		return 1;
	}

	private static int adminLedger(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		if (!isAdmin(ctx.getSource())) {
			ctx.getSource().sendFailure(Component.literal("OP required"));
			return 0;
		}
		ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
		var lines = HistoryDb.recentLedger(target.getUUID(), 15);
		if (lines.isEmpty()) {
			ctx.getSource().sendSuccess(() -> Component.literal("No ledger rows (DB empty or offline)."), false);
			return 0;
		}
		for (String line : lines) {
			ctx.getSource().sendSuccess(() -> Component.literal(line), false);
		}
		return lines.size();
	}

	private static int adminEnd(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		if (!isAdmin(ctx.getSource())) {
			ctx.getSource().sendFailure(Component.literal("OP required"));
			return 0;
		}
		ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
		if (!Rooms.adminEnd(target)) {
			ctx.getSource().sendFailure(Component.literal("Player is not in a Minecard room."));
			return 0;
		}
		ctx.getSource().sendSuccess(
			() -> Component.literal("Closed room for " + target.getGameProfile().name()),
			true
		);
		return 1;
	}

	private static boolean isAdmin(CommandSourceStack src) {
		if (!src.isPlayer()) {
			return true;
		}
		var id = src.getPlayer().nameAndId();
		var server = src.getServer();
		return server.isSingleplayerOwner(id) || server.getPlayerList().isOp(id);
	}

	private static int openShowcase(CommandContext<CommandSourceStack> ctx, CardCatalogDialog.Mode mode)
		throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		CardTableLoop.stop(player.getUUID());
		BlackjackGames.stop(player.getUUID());
		CardCatalogDialog.open(player, mode);
		ctx.getSource().sendSuccess(
			() -> Component.translatable("minecard.cards.opened." + mode.name().toLowerCase()),
			false
		);
		return 1;
	}

	private static int startBlackjack(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		CardTableLoop.stop(player.getUUID());
		BlackjackGames.stop(player.getUUID());
		MainMenuDialog.open(player);
		ctx.getSource().sendSuccess(() -> Component.translatable("minecard.menu.title"), false);
		return 1;
	}

	private static final class CardTableLoopCommands {
		private CardTableLoopCommands() {
		}

		private static int start(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
			ServerPlayer player = ctx.getSource().getPlayerOrException();
			BlackjackGames.stop(player.getUUID());
			CardTableLoop.start(player);
			ctx.getSource().sendSuccess(() -> Component.translatable("minecard.loop.started"), false);
			return 1;
		}

		private static int stop(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
			ServerPlayer player = ctx.getSource().getPlayerOrException();
			boolean stopped = CardTableLoop.stop(player.getUUID());
			ctx.getSource().sendSuccess(
				() -> Component.translatable(stopped ? "minecard.loop.stopped" : "minecard.loop.not_running"),
				false
			);
			return stopped ? 1 : 0;
		}
	}
}
