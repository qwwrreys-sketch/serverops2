package com.serverops.fabric.sidemenu.client;

import com.serverops.fabric.client.ClientState;
import com.serverops.fabric.sidemenu.MenuConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Free camera. How it works (no mixins, nothing server-side):
 * <ol>
 *   <li>an invisible {@link FreecamCamera} becomes the game's camera entity;</li>
 *   <li>the real player's movement input is swapped for an empty one, so your body stands still;</li>
 *   <li>every tick the camera is moved with WASD / Space / Shift along the direction you look, with
 *       smoothed acceleration; the mouse keeps turning the player, and the camera copies that view;</li>
 *   <li>attack / use / pick-block clicks are swallowed so you cannot build or fight from the camera;</li>
 *   <li>everything is restored when you switch it off, get hurt, die or leave the world.</li>
 * </ol>
 */
public final class Freecam {
    private Freecam() {}

    private static boolean active;
    private static FreecamCamera camera;
    private static LocalPlayer owner;
    private static Input savedInput;
    private static Entity savedCameraEntity;
    private static float savedYaw;
    private static float savedPitch;
    private static Vec3 velocity = Vec3.ZERO;

    public static boolean isActive() {
        return active;
    }

    /** May this player use freecam? (see {@link MenuConfig#FREECAM_STAFF_ONLY}) */
    public static boolean canUse() {
        return !MenuConfig.FREECAM_STAFF_ONLY || ClientState.allowed();
    }

    public static void toggle() {
        if (active) disable("");
        else enable();
    }

    private static void enable() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || active || !canUse()) return;

        owner = mc.player;
        savedInput = owner.input;
        savedCameraEntity = mc.getCameraEntity();
        savedYaw = owner.getYRot();
        savedPitch = owner.getXRot();

        camera = new FreecamCamera(mc.level);
        camera.snapTo(owner.getEyePosition());
        velocity = Vec3.ZERO;

        owner.input = new Input(); // empty input = the body stays where it is
        mc.setCameraEntity(camera);
        active = true;
    }

    /** Switch off and put everything back. {@code reason} (optional) is shown above the hotbar. */
    public static void disable(String reason) {
        Minecraft mc = Minecraft.getInstance();
        if (!active) return;
        active = false;

        boolean sameWorldPlayer = mc.player != null && mc.player == owner;
        if (sameWorldPlayer) {
            owner.input = savedInput;
            owner.setYRot(savedYaw);
            owner.setXRot(savedPitch);
            Entity back = savedCameraEntity != null && !savedCameraEntity.isRemoved() ? savedCameraEntity : mc.player;
            mc.setCameraEntity(back);
            if (!reason.isEmpty()) mc.player.displayClientMessage(Component.literal(reason), true);
        }
        clear();
    }

    /** Disconnect: just forget everything (the old player object is gone). */
    public static void forceStop() {
        active = false;
        clear();
    }

    private static void clear() {
        camera = null;
        owner = null;
        savedInput = null;
        savedCameraEntity = null;
        velocity = Vec3.ZERO;
    }

    // ---------------------------------------------------------------- tick hooks

    /** START of every client tick: eat clicks BEFORE the game turns them into attacks / block placing. */
    public static void beforeTick(Minecraft mc) {
        if (!active) return;
        mc.options.keyAttack.setDown(false);
        mc.options.keyUse.setDown(false);
        while (mc.options.keyAttack.consumeClick()) { /* swallowed */ }
        while (mc.options.keyUse.consumeClick()) { /* swallowed */ }
        while (mc.options.keyPickItem.consumeClick()) { /* swallowed */ }
    }

    /** END of every client tick: safety checks, then move the camera. */
    public static void afterTick(Minecraft mc) {
        if (!active) return;

        // new player object (respawn / dimension change) or left the world -> stop quietly
        if (mc.player == null || mc.level == null || mc.player != owner) {
            forceStop();
            if (mc.player != null) mc.setCameraEntity(mc.player);
            return;
        }
        // your body is in danger -> back to it immediately
        if (mc.player.hurtTime > 0 || !mc.player.isAlive()) {
            disable("Freecam ended: you were hurt.");
            return;
        }

        Vec3 wish = Vec3.ZERO;
        if (mc.screen == null) {
            double yaw = Math.toRadians(owner.getYRot());
            double pitch = Math.toRadians(owner.getXRot());
            // forward follows the look direction (including up/down), right is always horizontal
            Vec3 fwd = new Vec3(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
            Vec3 right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));

            double f = (mc.options.keyUp.isDown() ? 1 : 0) - (mc.options.keyDown.isDown() ? 1 : 0);
            double s = (mc.options.keyRight.isDown() ? 1 : 0) - (mc.options.keyLeft.isDown() ? 1 : 0);
            double v = (mc.options.keyJump.isDown() ? 1 : 0) - (mc.options.keyShift.isDown() ? 1 : 0);

            wish = fwd.scale(f).add(right.scale(s)).add(0, v, 0);
            if (wish.lengthSqr() > 1.0E-6) {
                double speed = MenuConfig.FREECAM_SPEED * (mc.options.keySprint.isDown() ? MenuConfig.FREECAM_SPRINT_MULTIPLIER : 1.0);
                wish = wish.normalize().scale(speed);
            } else {
                wish = Vec3.ZERO;
            }
        }

        // ease towards the wanted velocity: smooth start AND smooth stop
        velocity = velocity.add(wish.subtract(velocity).scale(0.25));
        if (velocity.lengthSqr() < 1.0E-5) velocity = Vec3.ZERO;

        Vec3 next = camera.position().add(velocity);
        Vec3 center = owner.getEyePosition();
        double max = MenuConfig.FREECAM_MAX_DISTANCE;
        if (next.distanceToSqr(center) > max * max) {
            next = center.add(next.subtract(center).normalize().scale(max));
            velocity = Vec3.ZERO;
        }
        camera.stepTo(next);
    }
}
