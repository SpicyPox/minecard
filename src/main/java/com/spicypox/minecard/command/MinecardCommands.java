package com.spicypox.minecard.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.spicypox.minecard.game.blackjack.BlackjackGames;
import com.spicypox.minecard.ui.CardCatalogDialog;
import com.spicypox.minecard.ui.CardTableLoop;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
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
				.executes(ctx -> {
					ctx.getSource().sendSuccess(() -> Component.translatable("minecard.help"), false);
					return 1;
				})
		);
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
		BlackjackGames.start(player);
		ctx.getSource().sendSuccess(() -> Component.translatable("minecard.bj.started"), false);
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
