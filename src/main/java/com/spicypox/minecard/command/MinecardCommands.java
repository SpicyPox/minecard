package com.spicypox.minecard.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.spicypox.minecard.ui.CardCatalogDialog;
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
				.then(Commands.literal("cards")
					.then(Commands.literal("poker")
						.executes(ctx -> open(ctx, CardCatalogDialog.Mode.POKER)))
					.then(Commands.literal("blackjack")
						.executes(ctx -> open(ctx, CardCatalogDialog.Mode.BLACKJACK)))
					.executes(ctx -> open(ctx, CardCatalogDialog.Mode.POKER)))
				.executes(ctx -> {
					ctx.getSource().sendSuccess(() -> Component.translatable("minecard.help"), false);
					return 1;
				})
		);
	}

	private static int open(CommandContext<CommandSourceStack> ctx, CardCatalogDialog.Mode mode)
		throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		CardCatalogDialog.open(player, mode);
		ctx.getSource().sendSuccess(
			() -> Component.translatable("minecard.cards.opened." + mode.name().toLowerCase()),
			false
		);
		return 1;
	}
}
