package com.cappleapple.stacksnotslots.mixin;

import com.cappleapple.stacksnotslots.client.ContainerInteractionTrace;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
public abstract class ConnectionMixin {
    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"))
    private void sns$traceContainerClick(Packet<?> packet, CallbackInfo callback) {
        if (packet instanceof ServerboundContainerClickPacket click) ContainerInteractionTrace.outgoingClick(click);
    }
}
