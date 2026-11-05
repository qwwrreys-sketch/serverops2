package com.serverops.fabric.mixin;

import com.serverops.fabric.client.ClientState;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityMixin {

    /** ESP: other players get the outline/glow render on THIS client only (never touches the server entity). */
    @Inject(method = "isCurrentlyGlowing", at = @At("HEAD"), cancellable = true)
    private void serverops$esp(CallbackInfoReturnable<Boolean> cir) {
        if (!ClientState.esp) return;
        Entity self = (Entity) (Object) this;
        if (self instanceof Player && self.level() instanceof ClientLevel && self != Minecraft.getInstance().player) {
            cir.setReturnValue(true);
        }
    }
}
