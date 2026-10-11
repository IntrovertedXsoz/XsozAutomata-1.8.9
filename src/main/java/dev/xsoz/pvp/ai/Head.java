package dev.xsoz.pvp.ai;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

import java.util.Random;

/**
 * The head, moved by a hand on a mouse. The brain says what to look at once a tick; every frame
 * the {@link AimModel} works out how the hand moves, and the move goes through the game's own
 * mouse code in whole mouse counts at the player's sensitivity - the same steps a real mouse makes.
 */
public final class Head {
    private final Minecraft mc;
    private final AimModel aim;
    private final Random rng;

    private Entity lookEntity, prevEntity;
    private Vec3 spot = new Vec3(0, 0.75, 0);
    private long spotUntil;
    private long tick;
    private boolean wanted;
    private double accYaw, accPitch;
    private double lastTime = -1;
    private long lastFrameTick = -1;
    // this tick's ask: only the last one counts
    private Vec3 reqPoint, prevPoint;
    private Entity reqEntity;
    private double reqWidth;
    private boolean reqPrecise, reqCalm;

    public Head(Minecraft mc, Skill skill, Random rng) {
        this.mc = mc;
        this.rng = rng;
        this.aim = new AimModel(skill, rng);
    }

    public void setSkill(Skill s) { aim.setSkill(s); }

    public AimModel model() { return aim; }

    /** Look at someone: a spot round their neck and upper chest, wandering a little over time. */
    public void look(Entity e) {
        if (e != lookEntity || tick >= spotUntil) {
            spot = new Vec3(rng.nextGaussian() * 0.12, 0.74 + rng.nextGaussian() * 0.08, rng.nextGaussian() * 0.12);
            spotUntil = tick + 15 + rng.nextInt(35);
        }
        lookEntity = e;
        wanted = true;
        AxisAlignedBB b = e.getEntityBoundingBox();
        double cx = (b.minX + b.maxX) / 2, cz = (b.minZ + b.maxZ) / 2;
        Vec3 p = new Vec3(cx + spot.xCoord * (b.maxX - b.minX), b.minY + spot.yCoord * (b.maxY - b.minY), cz + spot.zCoord * (b.maxZ - b.minZ));
        request(p, angularSize(b), e, false, false);
    }

    /** Look at a point. precise: lining something up (a throw, a pot at our feet). */
    public void look(Vec3 p, boolean precise) {
        lookEntity = null;
        wanted = true;
        request(p, precise ? 1.2 : 4, null, precise, false);
    }

    /** Just look there - around, along the way: one smooth, deliberate turn. */
    public void glance(Vec3 p) {
        lookEntity = null;
        wanted = true;
        request(p, 6, null, false, true);
    }

    private void request(Vec3 p, double width, Entity e, boolean precise, boolean calm) {
        reqPoint = p;
        reqWidth = width;
        reqEntity = e;
        reqPrecise = precise;
        reqCalm = calm;
    }

    public void beginTick(long t) {
        tick = t;
        wanted = false;
    }

    public void endTick() {
        if (!wanted) {
            aim.clear();
            lookEntity = null;
            prevPoint = null;
            prevEntity = null;
            return;
        }
        if (reqPoint == null) return;
        boolean fresh = reqEntity != null ? reqEntity != prevEntity
                : prevEntity != null || prevPoint == null || prevPoint.squareDistanceTo(reqPoint) > 4;
        EntityPlayerSP pl = mc.thePlayer;
        if (pl != null) {
            Vec3 eye = pl.getPositionEyes(1f);
            double dx = reqPoint.xCoord - eye.xCoord, dy = reqPoint.yCoord - eye.yCoord, dz = reqPoint.zCoord - eye.zCoord;
            double yaw = Math.toDegrees(Math.atan2(-dx, dz));
            double pitch = -Math.toDegrees(Math.atan2(dy, Math.max(1e-4, Math.sqrt(dx * dx + dz * dz))));
            double cur = pl.rotationYaw;
            yaw = cur + AimModel.wrap(yaw - cur);
            aim.target(yaw, MathHelper.clamp_double(pitch, -90, 90), reqWidth, fresh, reqPrecise, reqCalm);
        }
        prevPoint = reqPoint;
        prevEntity = reqEntity;
        reqPoint = null;
    }

    private double angularSize(AxisAlignedBB b) {
        EntityPlayerSP pl = mc.thePlayer;
        Vec3 eye = pl.getPositionEyes(1f);
        double cx = (b.minX + b.maxX) / 2, cy = (b.minY + b.maxY) / 2, cz = (b.minZ + b.maxZ) / 2;
        double dist = Math.max(0.5, Math.sqrt(Math.pow(cx - eye.xCoord, 2) + Math.pow(cy - eye.yCoord, 2) + Math.pow(cz - eye.zCoord, 2)));
        return Math.toDegrees(2 * Math.atan(Math.min(b.maxX - b.minX, b.maxY - b.minY) / 2 / dist));
    }

    /**
     * Moves the mouse: every rendered frame. now is game time in seconds (ticks plus the part of a
     * tick the frame is at), so the hand moves at human speed relative to everything else.
     */
    public void frame(double now) {
        EntityPlayerSP pl = mc.thePlayer;
        if (pl == null) return;
        double dt = lastTime < 0 ? 0.0 : now - lastTime;
        if (lastTime >= 0 && dt <= 0) return;
        lastTime = now;
        lastFrameTick = tick;
        double[] d = aim.step(pl.rotationYaw, pl.rotationPitch, dt);
        // the game turns the view by 0.15 * f degrees per mouse count (EntityRenderer)
        double s = mc.gameSettings.mouseSensitivity * 0.6 + 0.2;
        double f = s * s * s * 8.0;
        double perCount = f * 0.15;
        accYaw += d[0];
        accPitch += d[1];
        long cx = (long) (accYaw / perCount);
        long cy = (long) (accPitch / perCount);
        if (cx == 0 && cy == 0) return;
        accYaw -= cx * perCount;
        accPitch -= cy * perCount;
        // (Entity.setAngles: yaw += x * 0.15, pitch -= y * 0.15 - the mouse's y is upside down)
        pl.setAngles((float) (cx * f), (float) (-cy * f));
    }

    /** No frame since the last tick (minimised, or a very slow machine): move by the tick's worth. */
    public void tickFallback() {
        if (lastFrameTick < tick) frame(tick * 0.05);
    }

    /** The crosshair's on this entity right now (what a click this tick would hit). */
    public boolean onTarget(Entity e) {
        MovingObjectPosition h = mc.objectMouseOver;
        return h != null && h.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY && h.entityHit == e;
    }

    /** Degrees between where we look and the direction to this point. */
    public double offBy(Vec3 p) {
        EntityPlayerSP pl = mc.thePlayer;
        Vec3 eye = pl.getPositionEyes(1f);
        Vec3 to = p.subtract(eye).normalize();
        Vec3 look = pl.getLook(1f);
        double dot = MathHelper.clamp_double(to.dotProduct(look), -1, 1);
        return Math.toDegrees(Math.acos(dot));
    }
}
