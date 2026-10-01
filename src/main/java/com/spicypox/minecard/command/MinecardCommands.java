package com.spicypox.minecard.command;

import com.mojang.brigadier.CommandDispatcher;
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
					.executes(ctx -> {
						ServerPlayer player = ctx.getSource().getPlayerOrException();
						CardCatalogDialog.open(player);
						ctx.getSource().sendSuccess(() -> Component.translatable("minecard.cards.opened"), false);
						return 1;
					}))
				.executes(ctx -> {
					ctx.getSource().sendSuccess(() -> Component.translatable("minecard.help"), false);
					return 1;
				})
		);
	}
}
