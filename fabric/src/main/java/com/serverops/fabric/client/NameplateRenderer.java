package com.serverops.fabric.client;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import org.joml.Matrix4f;

/** Draws one extra billboarded text line next to the vanilla nametag (same look as vanilla). */
public final class NameplateRenderer {
    private NameplateRenderer() {}

    /** @param pixelY vertical offset in nametag pixels: 0 = the vanilla name line, -10 = one line above, +10 = below */
    public static void draw(Entity entity, Component text, PoseStack poseStack, MultiBufferSource buffer, int light, int pixelY) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getEntityRenderDispatcher().distanceToSqr(entity) > 4096.0D) return;

        boolean seeThrough = !entity.isDiscrete();
        poseStack.pushPose();
        poseStack.translate(0.0F, entity.getBbHeight() + 0.5F, 0.0F);
        poseStack.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        poseStack.scale(-0.025F, -0.025F, 0.025F);
        Matrix4f matrix = poseStack.last().pose();
        int background = (int) (mc.options.getBackgroundOpacity(0.25F) * 255.0F) << 24;
        Font font = mc.font;
        float x = -font.width(text) / 2.0F;
        font.drawInBatch(text, x, pixelY, 0x20FFFFFF, false, matrix, buffer,
                seeThrough ? Font.DisplayMode.SEE_THROUGH : Font.DisplayMode.NORMAL, background, light);
        if (seeThrough) {
            font.drawInBatch(text, x, pixelY, 0xFFFFFFFF, false, matrix, buffer, Font.DisplayMode.NORMAL, 0, light);
        }
        poseStack.popPose();
    }
}
