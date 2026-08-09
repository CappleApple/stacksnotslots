package com.cappleapple.stacksnotslots.mixin;

import com.cappleapple.stacksnotslots.client.ContainerInteractionTrace;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
    @Inject(method = "handleContainerSetSlot", at = @At("HEAD"))
    private void sns$traceContainerSlot(ClientboundContainerSetSlotPacket packet, CallbackInfo callback) {
        ContainerInteractionTrace.incomingSlot(packet);
    }

    @Inject(method = "handleContainerContent", at = @At("HEAD"))
    private void sns$traceContainerContent(ClientboundContainerSetContentPacket packet, CallbackInfo callback) {
        ContainerInteractionTrace.incomingContent(packet);
    }
}
