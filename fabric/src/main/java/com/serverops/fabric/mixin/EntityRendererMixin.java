package com.serverops.fabric.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.serverops.fabric.client.ClientState;
import com.serverops.fabric.client.NameplateRenderer;
import com.serverops.fabric.net.Net;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {

    /** "[Principal]" floats exactly one line above the vanilla nametag (only when the nametag itself is drawn). */
    @Inject(method = "renderNameTag", at = @At("TAIL"))
    private void serverops$roleAboveName(Entity entity, Component displayName, PoseStack poseStack,
                                         MultiBufferSource buffer, int light, CallbackInfo ci) {
        if (!(entity instanceof AbstractClientPlayer)) return;
        Net.Tag tag = ClientState.tag(entity.getUUID());
        if (tag == null) return;
        MutableComponent text = Component.literal("[" + tag.name() + "]")
                .withStyle(s -> s.withColor(TextColor.fromRgb(tag.color() & 0xFFFFFF)).withBold(true));
        NameplateRenderer.draw(entity, text, poseStack, buffer, light, -10);
    }

    /** Optional "see other health bars" sight tool: a small bar under other players' names. */
    @Inject(method = "render", at = @At("TAIL"))
    private void serverops$healthBar(Entity entity, float yaw, float partialTick, PoseStack poseStack,
                                     MultiBufferSource buffer, int light, CallbackInfo ci) {
        if (!ClientState.healthBars || !(entity instanceof AbstractClientPlayer p)) return;
        if (p == Minecraft.getInstance().player) return;

        float hp = Math.max(0, p.getHealth());
        float max = Math.max(1, p.getMaxHealth());
        int filled = Math.round(10 * Math.min(1f, hp / max));
        ChatFormatting color = hp / max > 0.6f ? ChatFormatting.GREEN : (hp / max > 0.3f ? ChatFormatting.YELLOW : ChatFormatting.RED);
        MutableComponent bar = Component.literal("|".repeat(filled)).withStyle(color)
                .append(Component.literal("|".repeat(10 - filled)).withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(" " + Math.round(hp) + "/" + Math.round(max)).withStyle(ChatFormatting.WHITE));
        NameplateRenderer.draw(entity, bar, poseStack, buffer, light, 10);
    }
}
