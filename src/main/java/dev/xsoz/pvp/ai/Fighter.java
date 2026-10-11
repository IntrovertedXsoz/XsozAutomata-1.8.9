package dev.xsoz.pvp.ai;

import dev.xsoz.pvp.modules.FriendsModule;
import dev.xsoz.pvp.modules.PvpModule;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemAxe;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.BlockPos;
import net.minecraft.util.Vec3;

import java.util.List;
import java.util.Random;

/**
 * The fighter: what a good 1.8 player does, tick by tick, with human hands.
 * <ul>
 *   <li><b>Who.</b> A player it can see (or saw a moment ago), close, in front of it - never a
 *   friend, a teammate, or a server's fake "bot" player that isn't on the tab list.</li>
 *   <li><b>Aim and clicks.</b> The hand ({@link AimModel}) tracks a spot round their neck and upper
 *   chest; the finger ({@link ClickModel}) clicks in a human rhythm once they're about in reach and
 *   the crosshair is near them.</li>
 *   <li><b>Spacing and knockback.</b> Sprint in, keep forward pressure; after each hit that lands, a
 *   sprint reset (w-tap, s-tap or block-hit) so the next hit knocks them back again; strafe, with
 *   switches at no fixed rhythm; when hit, jump as the hit lands (a jump reset takes the edge off
 *   the knockback).</li>
 *   <li><b>The kit.</b> A rod to break up their combo and start ours, a bow at range (leading a
 *   moving target, aiming over the drop), healing when low - a splash potion thrown at its own
 *   feet, soup, a golden apple when there's room to eat it.</li>
 *   <li><b>Sumo.</b> Never steps toward an edge; fights toward the middle.</li>
 * </ul>
 */
public final class Fighter {
    private final Minecraft mc;
    private final Random rng = new Random();
    public final Head head;
    public final Body body;
    private final ClickModel clicks;
    private Skill skill = Skill.GREAT;
    private long tick;
    private EntityLivingBase target;
    private long targetSeenAt;
    private String doing = "";
    // spacing and sprint resets
    private int resetTicks;
    private long lastHitLanded = -100;
    private int lastTargetHurt;
    private int strafeDir = 1;
    private long nextStrafeSwitch;
    private int lastOwnHurt;
    // items in use
    private long usingUntil = -1;
    private int returnSlot = -1;
    private long nextRod, nextHeal;
    private int bowDraw;
    private Vec3 middle;

    public Fighter(Minecraft mc) {
        this.mc = mc;
        this.head = new Head(mc, skill, rng);
        this.body = new Body(mc, head);
        this.clicks = new ClickModel(ClickModel.Style.JITTER, skill, rng);
    }

    public String doing() { return doing; }

    public int clickTotal() { return clicks.total(); }

    /** Ticks it has spent wanting to click (in reach, crosshair near them). */
    public int clickingTicks() { return clickingTicks; }

    private int clickingTicks;

    public EntityLivingBase target() { return target; }

    /** Self-test only: a practice dummy (a mob named "Dummy") counts as someone to fight. */
    public static boolean selfTest = System.getenv("XSOZ_SELFTEST") != null;

    /** One game tick (before the game reads the keys). */
    public void tick(PvpModule m) {
        tick++;
        EntityPlayerSP me = mc.thePlayer;
        if (me == null || mc.theWorld == null) return;
        skill = m.skill.get();
        head.setSkill(skill);
        clicks.setSkill(skill);
        clicks.setStyle(m.clicking.get());
        body.beginTick(tick);
        if (me.isDead || me.getHealth() <= 0) {
            target = null;
            doing = "dead";
            body.apply();
            return;
        }
        if (middle == null) middle = me.getPositionVector();

        target = pick(me, m);
        if (target == null) {
            clicks.step(0.05, false);
            doing = "looking for someone to fight";
            finishItemUse(me);
            body.apply();
            return;
        }
        double dist = me.getDistanceToEntity(target);
        Kit kit = m.kit.get() == Kit.AUTO ? Kit.detect(this) : m.kit.get();

        // ---- healing first, if it's time and there's room for it
        if (heal(me, kit, dist)) {
            body.apply();
            return;
        }
        // ---- something in hand that's being used up (eating, a bow drawn): see it through
        if (tick < usingUntil) {
            body.use();
            head.look(target);
            body.apply();
            return;
        }
        finishItemUse(me);

        // ---- at range: the bow; closing in: a rod; in reach: the sword
        if (kit.bow && dist > 12 && bow(me, dist)) {
            body.apply();
            return;
        }
        bowDraw = 0;
        if (kit.rod && dist > 3.2 && dist < 7 && tick >= nextRod && rod(me)) {
            body.apply();
            return;
        }
        holdWeapon(me);
        melee(me, m, kit, dist);
        body.apply();
    }

    // ------------------------------------------------------------------ who

    private EntityLivingBase pick(EntityPlayerSP me, PvpModule m) {
        if (selfTest) {
            for (Object o : mc.theWorld.loadedEntityList) {
                if (o instanceof EntityLivingBase && !(o instanceof EntityPlayer) && ((EntityLivingBase) o).hasCustomName()
                        && "Dummy".equals(((EntityLivingBase) o).getCustomNameTag()) && ((EntityLivingBase) o).isEntityAlive()
                        && me.getDistanceToEntity((EntityLivingBase) o) < 40) return (EntityLivingBase) o;
            }
        }
        EntityPlayer best = null;
        double bestScore = Double.MAX_VALUE;
        @SuppressWarnings("unchecked")
        List<EntityPlayer> players = mc.theWorld.playerEntities;
        for (EntityPlayer p : players) {
            if (p == me || p.isDead || p.getHealth() <= 0 || p.isSpectator()) continue;
            if (FriendsModule.isFriend(p.getName())) continue;
            if (m.teams.get() == PvpModule.OnOff.ON && sameTeam(me, p)) continue;
            // a server's fake player (an NPC, an anti-cheat bot) isn't on the tab list
            if (mc.getNetHandler() != null && mc.getNetHandler().getPlayerInfo(p.getUniqueID()) == null) continue;
            if (p.isInvisible() && p.getTotalArmorValue() == 0) continue;
            double d = me.getDistanceToEntity(p);
            if (d > 40) continue;
            boolean sees = me.canEntityBeSeen(p);
            if (!sees && !(p == target && tick - targetSeenAt < 60)) continue;
            // closest, in front of us, and whoever we're already on
            double off = head.offBy(p.getPositionEyes(1f));
            double score = d + off / 30.0 - (p == target ? 3 : 0);
            if (score < bestScore) {
                bestScore = score;
                best = p;
            }
        }
        if (best != null && me.canEntityBeSeen(best)) targetSeenAt = tick;
        return best;
    }

    private static boolean sameTeam(EntityPlayer me, EntityPlayer p) {
        if (me.isOnSameTeam(p)) return true;
        // the usual server way: the same colour on the name
        String a = me.getDisplayName().getFormattedText(), b = p.getDisplayName().getFormattedText();
        int ia = a.indexOf('§'), ib = b.indexOf('§');
        return ia >= 0 && ib >= 0 && ia + 1 < a.length() && ib + 1 < b.length() && a.charAt(ia + 1) == b.charAt(ib + 1)
                && "0123456789abcdef".indexOf(a.charAt(ia + 1)) >= 0;
    }

    // ------------------------------------------------------------------ the sword

    private void melee(EntityPlayerSP me, PvpModule m, Kit kit, double dist) {
        head.look(target);
        // did our last hit land? (their hurt timer starts over)
        if (target.hurtTime > lastTargetHurt && target.hurtTime >= 9) {
            lastHitLanded = tick;
            if (m.reset.get() != Reset.NONE && skill.atLeast(Skill.DECENT) && rng.nextDouble() > skill.fumble * 4) {
                resetTicks = m.reset.get() == Reset.BLOCKHIT ? 2 + rng.nextInt(2) : 2 + rng.nextInt(skill.atLeast(Skill.GREAT) ? 2 : 3);
            }
        }
        lastTargetHurt = target.hurtTime;
        // were we just hit? jump as it lands (a jump reset), if on the ground
        boolean hitNow = me.hurtTime > lastOwnHurt && me.hurtTime >= 9;
        lastOwnHurt = me.hurtTime;

        // ---- moving
        boolean close = dist < 3.4;
        if (resetTicks > 0) {
            resetTicks--;
            switch (m.reset.get()) {
                case WTAP:
                    doing = "w-tapping";
                    break;
                case STAP:
                    body.back();
                    doing = "s-tapping";
                    break;
                case BLOCKHIT:
                    body.forward();
                    body.use();
                    doing = "block-hitting";
                    break;
                default:
                    body.forward();
            }
        } else {
            body.forward();
            body.sprint();
            doing = close ? "fighting " + target.getName() : "closing in on " + target.getName();
        }
        // a strafe, switched at no set rhythm (and away from an edge in sumo)
        if (dist < 5) {
            if (tick >= nextStrafeSwitch) {
                strafeDir = -strafeDir;
                nextStrafeSwitch = tick + 8 + rng.nextInt(18);
            }
            if (kit == Kit.SUMO) strafeDir = saferSide(me, strafeDir);
            if (strafeDir > 0) body.right();
            else body.left();
        }
        if (hitNow && me.onGround && skill.atLeast(Skill.GOOD) && rng.nextDouble() < 0.55 + 0.1 * skill.ordinal()) {
            body.jump();
            doing = "jump-resetting";
        } else if (!close && dist > 6 && me.onGround && me.isSprinting() && kit != Kit.SUMO && rng.nextDouble() < 0.25) {
            // closing a gap: sprint-jumping
            body.jump();
        }
        // sumo: nothing that walks us off
        if (kit == Kit.SUMO && edgeAhead(me)) {
            body.noForward();
            doing = "keeping off the edge";
        }
        // ---- clicking: about in reach and the crosshair near them
        double off = head.offBy(target.getPositionEyes(1f).addVector(0, -0.4, 0));
        boolean want = dist < 4.0 && off < 22;
        if (want) clickingTicks++;
        body.click(clicks.step(0.05, want));
    }

    /** Which way to strafe without going off the platform (toward the middle when it's close). */
    private int saferSide(EntityPlayerSP me, int dir) {
        Vec3 look = me.getLook(1f);
        Vec3 right = new Vec3(-look.zCoord, 0, look.xCoord).normalize();
        Vec3 side = dir > 0 ? right : new Vec3(-right.xCoord, 0, -right.zCoord);
        if (!solidBelow(me.getPositionVector().add(new Vec3(side.xCoord * 1.5, 0, side.zCoord * 1.5)))) return -dir;
        return dir;
    }

    private boolean edgeAhead(EntityPlayerSP me) {
        Vec3 look = me.getLook(1f);
        Vec3 flat = new Vec3(look.xCoord, 0, look.zCoord).normalize();
        return !solidBelow(me.getPositionVector().add(new Vec3(flat.xCoord * 1.3, 0, flat.zCoord * 1.3)));
    }

    private boolean solidBelow(Vec3 p) {
        for (int d = 1; d <= 2; d++) {
            BlockPos b = new BlockPos(p.xCoord, p.yCoord - d, p.zCoord);
            Material mat = mc.theWorld.getBlockState(b).getBlock().getMaterial();
            if (mat.isSolid()) return true;
            if (mat.isLiquid()) return false;
        }
        return false;
    }

    private void holdWeapon(EntityPlayerSP me) {
        int slot = body.hotbar(new Body.ItemTest() {
            @Override
            public boolean test(ItemStack s) { return s.getItem() instanceof ItemSword; }
        });
        if (slot < 0) slot = body.hotbar(new Body.ItemTest() {
            @Override
            public boolean test(ItemStack s) { return s.getItem() instanceof ItemAxe; }
        });
        if (slot >= 0) body.select(slot);
    }

    // ------------------------------------------------------------------ the rod and the bow

    /** A rod cast: in hand, a right click, back to the sword a moment later. */
    private boolean rod(EntityPlayerSP me) {
        int slot = body.hotbar(new Body.ItemTest() {
            @Override
            public boolean test(ItemStack s) { return s.getItem() == Items.fishing_rod; }
        });
        if (slot < 0) return false;
        head.look(target);
        if (!body.select(slot)) {
            doing = "getting the rod out";
            body.forward();
            body.sprint();
            return true;
        }
        if (head.offBy(target.getPositionEyes(1f)) < 6) {
            body.use();
            usingUntil = tick + 1;
            returnSlot = -2; // back to the sword next tick
            nextRod = tick + 30 + rng.nextInt(30);
            doing = "rodding";
        }
        body.forward();
        body.sprint();
        return true;
    }

    /** Bow at range: draw it fully, lead a moving target, aim over the arrow's drop, let go. */
    private boolean bow(EntityPlayerSP me, double dist) {
        int slot = body.hotbar(new Body.ItemTest() {
            @Override
            public boolean test(ItemStack s) { return s.getItem() == Items.bow; }
        });
        if (slot < 0 || !me.inventory.hasItem(Items.arrow)) return false;
        if (!body.select(slot)) {
            doing = "getting the bow out";
            return true;
        }
        // where they'll be when it gets there, and how high to aim for the drop (full draw: ~3 blocks a tick)
        double flight = dist / 2.8;
        Vec3 at = new Vec3(target.posX + (target.posX - target.prevPosX) * flight, target.posY + target.getEyeHeight() * 0.7,
                target.posZ + (target.posZ - target.prevPosZ) * flight);
        double drop = 0.5 * 0.05 * flight * flight;
        head.look(at.addVector(0, drop * 0.9, 0), false);
        body.use();
        bowDraw++;
        doing = "drawing the bow";
        if (bowDraw >= 20 + rng.nextInt(4) && head.offBy(at.addVector(0, drop * 0.9, 0)) < 2.5) {
            // let go (the use key comes up this tick)
            bowDraw = 0;
            usingUntil = -1;
            return true;
        }
        usingUntil = tick + 1;
        return true;
    }

    // ------------------------------------------------------------------ healing

    private boolean heal(EntityPlayerSP me, Kit kit, double dist) {
        if (tick < nextHeal) return false;
        float hp = me.getHealth();
        // a splash potion at our own feet
        if (hp <= 10) {
            int pot = body.hotbar(new Body.ItemTest() {
                @Override
                public boolean test(ItemStack s) { return isHealingSplash(s); }
            });
            if (pot >= 0) {
                if (!body.select(pot)) {
                    doing = "getting a potion out";
                    backOff(me);
                    return true;
                }
                head.look(me.getPositionVector().addVector(0, -0.5, 0).add(me.getLook(1f).normalize()), true);
                if (me.rotationPitch > 70) {
                    body.use();
                    nextHeal = tick + 6 + rng.nextInt(4);
                    returnSlot = -2;
                    usingUntil = tick + 1;
                    doing = "potting";
                } else {
                    doing = "looking down to pot";
                }
                backOff(me);
                return true;
            }
            int soup = body.hotbar(new Body.ItemTest() {
                @Override
                public boolean test(ItemStack s) { return s.getItem() == Items.mushroom_stew; }
            });
            if (soup >= 0) {
                if (body.select(soup)) {
                    body.use();
                    nextHeal = tick + 3 + rng.nextInt(3);
                    returnSlot = -2;
                    usingUntil = tick + 1;
                    doing = "souping";
                }
                head.look(target);
                return true;
            }
        }
        // a golden apple: takes a second and a half to eat - only with a little room
        if (hp <= 11 && (dist > 4.5 || me.hurtTime == 0 && hp <= 6) && !me.isPotionActive(Potion.regeneration)) {
            int gap = body.hotbar(new Body.ItemTest() {
                @Override
                public boolean test(ItemStack s) { return s.getItem() == Items.golden_apple; }
            });
            if (gap >= 0) {
                if (body.select(gap)) {
                    body.use();
                    usingUntil = tick + 34;
                    returnSlot = -2;
                    nextHeal = tick + 60;
                    doing = "eating a golden apple";
                }
                backOff(me);
                head.look(target);
                return true;
            }
        }
        return false;
    }

    /** Getting away a little while busy with an item. */
    private void backOff(EntityPlayerSP me) {
        body.back();
        if (strafeDir > 0) body.right();
        else body.left();
    }

    private static boolean isHealingSplash(ItemStack s) {
        if (!(s.getItem() instanceof ItemPotion) || !ItemPotion.isSplash(s.getMetadata())) return false;
        List<PotionEffect> fx = ((ItemPotion) s.getItem()).getEffects(s);
        if (fx == null) return false;
        for (PotionEffect e : fx) if (e.getPotionID() == Potion.heal.id) return true;
        return false;
    }

    /** Done with whatever item was used: back to the weapon. */
    private void finishItemUse(EntityPlayerSP me) {
        if (returnSlot == -2) {
            returnSlot = -1;
            holdWeapon(me);
        }
    }

    // ------------------------------------------------------------------ the kits

    public enum Kit {
        AUTO("Work it out", "Looks at what's in the hotbar: potions, soup, a rod and a bow...", true),
        CLASSIC("Classic", "Sword, rod and bow, armour.", true),
        SUMO("Sumo", "No items: knock them off, never get walked off the edge.", false),
        BOXING("Boxing", "Hits count: keep the combo going.", false),
        NODEBUFF("NoDebuff", "Splash healing potions at its feet when low.", true),
        SOUP("Soup", "Soup when low.", true),
        UHC("UHC", "Rod, bow, golden apples.", true),
        COMBO("Combo", "Long combos: forward pressure, sprint resets.", false);

        public final String title, detail;
        final boolean rod, bow;

        Kit(String title, String detail, boolean items) {
            this.title = title;
            this.detail = detail;
            this.rod = items;
            this.bow = items;
        }

        static Kit detect(Fighter f) {
            boolean pots = f.body.hotbar(new Body.ItemTest() {
                @Override
                public boolean test(ItemStack s) { return isHealingSplash(s); }
            }) >= 0;
            if (pots) return NODEBUFF;
            boolean soup = f.body.hotbar(new Body.ItemTest() {
                @Override
                public boolean test(ItemStack s) { return s.getItem() == Items.mushroom_stew; }
            }) >= 0;
            if (soup) return SOUP;
            boolean gap = f.body.hotbar(new Body.ItemTest() {
                @Override
                public boolean test(ItemStack s) { return s.getItem() == Items.golden_apple; }
            }) >= 0;
            return gap ? UHC : CLASSIC;
        }
    }

    public enum Reset {
        WTAP("W-tap", "Lets go of W for a moment after each hit, so the next one knocks them back again."),
        STAP("S-tap", "Taps S after each hit: a sharper reset, keeps the spacing."),
        BLOCKHIT("Block-hit", "Blocks with the sword for a moment after each hit (less damage taken, resets sprint)."),
        NONE("None", "Just holds W.");

        public final String title, detail;

        Reset(String title, String detail) {
            this.title = title;
            this.detail = detail;
        }
    }
}
