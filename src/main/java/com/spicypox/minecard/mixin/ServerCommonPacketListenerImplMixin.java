package com.spicypox.minecard.mixin;

import com.spicypox.minecard.dialog.DialogClicks;
import net.minecraft.network.protocol.common.ServerboundCustomClickActionPacket;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class ServerCommonPacketListenerImplMixin {
	@Inject(method = "handleCustomClickAction", at = @At("HEAD"), cancellable = true)
	private void minecard$onCustomClick(ServerboundCustomClickActionPacket packet, CallbackInfo ci) {
		if (!((Object) this instanceof ServerGamePacketListenerImpl game)) {
			return;
		}
		if (DialogClicks.handle(game.player, packet.id())) {
			ci.cancel();
		}
	}
}
