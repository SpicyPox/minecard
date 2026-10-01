package com.spicypox.minecard.ui;

import net.minecraft.network.chat.Component;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.CommonButtonData;
import net.minecraft.server.dialog.CommonDialogData;
import net.minecraft.server.dialog.DialogAction;
import net.minecraft.server.dialog.NoticeDialog;
import net.minecraft.server.dialog.body.DialogBody;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Stacks labeled {@link CardGrid} rows top→bottom for poker / blackjack / etc. */
public final class TableLayout {
	private TableLayout() {
	}

	public static NoticeDialog build(Component title, Component intro, List<CardGrid> grids) {
		List<DialogBody> body = new ArrayList<>();
		body.add(new net.minecraft.server.dialog.body.PlainMessage(intro, CardLayer.DIALOG_WIDTH));
		for (CardGrid grid : grids) {
			body.add(grid.toBody());
		}

		ActionButton ok = new ActionButton(
			new CommonButtonData(Component.translatable("gui.ok"), 150),
			Optional.empty()
		);

		CommonDialogData data = new CommonDialogData(
			title,
			Optional.empty(),
			true,
			false,
			DialogAction.CLOSE,
			List.copyOf(body),
			List.of()
		);
		return new NoticeDialog(data, ok);
	}
}
