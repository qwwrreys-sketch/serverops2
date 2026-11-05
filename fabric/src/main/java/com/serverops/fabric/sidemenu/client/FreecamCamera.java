package com.serverops.fabric.sidemenu.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * The invisible, client-only "camera body" used by {@link Freecam}. It is NEVER added to the world, so the
 * server and other players never know it exists. A Marker has no eye height, so the camera sits exactly at
 * its position.
 *
 * <p>Its view direction is read live from the real player, so mouse-look stays perfectly smooth (it is
 * evaluated every frame, not once per tick).
 */
public final class FreecamCamera extends Marker {
    public FreecamCamera(ClientLevel level) {
        super(EntityType.MARKER, level);
    }

    @Override
    public float getViewYRot(float partialTick) {
        Player p = Minecraft.getInstance().player;
        return p != null ? p.getViewYRot(partialTick) : super.getViewYRot(partialTick);
    }

    @Override
    public float getViewXRot(float partialTick) {
        Player p = Minecraft.getInstance().player;
        return p != null ? p.getViewXRot(partialTick) : super.getViewXRot(partialTick);
    }

    /** Teleports without interpolating (used when the camera is created). */
    public void snapTo(Vec3 pos) {
        setPos(pos.x, pos.y, pos.z);
        this.xo = pos.x;
        this.yo = pos.y;
        this.zo = pos.z;
        this.xOld = pos.x;
        this.yOld = pos.y;
        this.zOld = pos.z;
    }

    /** Moves one tick: remembers the old position so the renderer can interpolate between ticks (smooth). */
    public void stepTo(Vec3 pos) {
        this.xo = getX();
        this.yo = getY();
        this.zo = getZ();
        this.xOld = this.xo;
        this.yOld = this.yo;
        this.zOld = this.zo;
        setPos(pos.x, pos.y, pos.z);
    }
}
