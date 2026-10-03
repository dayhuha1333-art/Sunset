package com.sunset;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.RayTraceResult;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.client.event.RenderBlockOverlayEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Mod(modid = "sunset", name = "Sunset", version = "4.0",
        acceptedMinecraftVersions = "[1.12.2]")
public class SunsetMod {

    private static final Logger LOG = LogManager.getLogger("Sunset");
    private static Configuration config;
    public static KeyBinding guiKey;

    public static boolean killAuraEnabled = false;
    public static float killAuraFov = 30f;
    public static float killAuraMiss = 20f;

    public static boolean triggerBotEnabled = false;
    public static float triggerBotMiss = 20f;

    private static long killAuraLast = 0;
    private static long triggerBotLast = 0;

    public static boolean fullbrightEnabled = false;
    public static float savedGamma = -1f;

    public static boolean fovOverride = false;
    public static float customFov = 70f;

    public static boolean skyColorEnabled = false;
    public static int skyColor = 0x87CEEB;

    public static boolean hitBoxEnabled = false;
    public static int hitBoxColor = 0x00FF00;
    public static int hitBoxTargetColor = 0xFF0000;

    public static boolean disFireEnabled = true;

    public static boolean keysInfEnabled = false;
    public static int keysInfCorner = 0;
    public static int keysInfColor = 0xFFFFFF;

    public static boolean coordsEnabled = false;
    public static int coordsCorner = 0;
    public static int coordsColor = 0xFFFFFF;

    public static boolean fpsEnabled = false;
    public static int fpsCorner = 1;
    public static int fpsColor = 0xFFFFFF;

    public static boolean pingEnabled = false;
    public static int pingCorner = 1;
    public static int pingColor = 0xFFFFFF;

    public static boolean armorHudEnabled = false;
    public static boolean potionTimerEnabled = false;

    private static Minecraft mc() { return Minecraft.getMinecraft(); }

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) { loadConfig(); }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        guiKey = new KeyBinding("Open Sunset GUI", Keyboard.KEY_RSHIFT, "Sunset");
        ClientRegistry.registerKeyBinding(guiKey);
        MinecraftForge.EVENT_BUS.register(this);
        LOG.info("Sunset 4.0 initialized");
    }

    @SubscribeEvent
    public void onKey(InputEvent.KeyInputEvent event) {
        if (guiKey == null) return;
        if (guiKey.isPressed() && mc().currentScreen == null) {
            mc().displayGuiScreen(new SunsetGui());
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = mc();
        if (mc.gameSettings != null) {
            if (fullbrightEnabled) {
                if (savedGamma < 0) savedGamma = mc.gameSettings.gammaSetting;
                mc.gameSettings.gammaSetting = 100f;
            } else if (savedGamma >= 0) {
                mc.gameSettings.gammaSetting = savedGamma;
                savedGamma = -1f;
            }
        }

        if (killAuraEnabled) doKillAura();
        if (triggerBotEnabled) doTriggerBot();
    }

    private static void doKillAura() {
        Minecraft mc = mc();
        if (mc.player == null || mc.world == null || mc.playerController == null) return;
        if (!mc.player.isEntityAlive()) return;

        EntityLivingBase target = null;
        double bestDist = Double.MAX_VALUE;

        for (Entity e : mc.world.getEntitiesWithinAABB(EntityLivingBase.class,
                mc.player.getEntityBoundingBox().grow(4.5))) {
            if (!(e instanceof EntityLivingBase)) continue;
            EntityLivingBase living = (EntityLivingBase) e;
            if (living == mc.player) continue;
            if (!living.isEntityAlive()) continue;
            if (!mc.player.canEntityBeSeen(living)) continue;
            if (!isInFov(mc, living, killAuraFov)) continue;

            double d = mc.player.getDistance(living);
            if (d < bestDist) { bestDist = d; target = living; }
        }

        if (target == null) return;

        long now = System.currentTimeMillis();
        int delay = randInt(10, 80);
        if (now - killAuraLast < delay) return;
        killAuraLast = now;

        float[] rot = getRotations(mc, target);
        mc.player.rotationYaw = rot[0];
        mc.player.rotationPitch = rot[1];

        if (chance(killAuraMiss)) return;

        mc.playerController.attackEntity(mc.player, target);
        mc.player.swingArm(EnumHand.MAIN_HAND);
    }

    private static float[] getRotations(Minecraft mc, EntityLivingBase target) {
        double dx = target.posX - mc.player.posX;
        double dy = (target.posY + target.getEyeHeight()) - (mc.player.posY + mc.player.getEyeHeight());
        double dz = target.posZ - mc.player.posZ;
        double dist = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90);
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, dist));
        return new float[]{yaw, pitch};
    }

    private static void doTriggerBot() {
        Minecraft mc = mc();
        if (mc.player == null || mc.world == null || mc.playerController == null) return;
        if (!mc.player.isEntityAlive()) return;
        if (killAuraEnabled) return;

        RayTraceResult over = mc.objectMouseOver;
        if (over == null || over.typeOfHit != RayTraceResult.Type.ENTITY) return;
        if (!(over.entityHit instanceof EntityLivingBase)) return;

        EntityLivingBase target = (EntityLivingBase) over.entityHit;
        if (!target.isEntityAlive()) return;
        if (!mc.player.canEntityBeSeen(target)) return;

        long now = System.currentTimeMillis();
        int delay = randInt(30, 90);
        if (now - triggerBotLast < delay) return;
        triggerBotLast = now;

        if (chance(triggerBotMiss)) return;

        mc.playerController.attackEntity(mc.player, target);
        mc.player.swingArm(EnumHand.MAIN_HAND);
    }

    @SubscribeEvent
    public void onFov(EntityViewRenderEvent.FOVModifier event) {
        if (fovOverride) event.setFOV(customFov);
    }

    @SubscribeEvent
    public void onFogColors(EntityViewRenderEvent.FogColors event) {
        if (!skyColorEnabled) return;
        float r = ((skyColor >> 16) & 0xFF) / 255f;
        float g = ((skyColor >> 8) & 0xFF) / 255f;
        float b = (skyColor & 0xFF) / 255f;
        event.setRed(r); event.setGreen(g); event.setBlue(b);
    }

    @SubscribeEvent
    public void onRenderBlockOverlay(RenderBlockOverlayEvent event) {
        if (!disFireEnabled) return;
        if (event.getOverlayType() != RenderBlockOverlayEvent.OverlayType.FIRE) return;
        if (event.getPlayer() == null) return;
        if (event.getPlayer() != mc().player) return;
        event.setCanceled(true);
    }

    @SubscribeEvent
    public void onRenderHitBox(RenderWorldLastEvent event) {
        if (!hitBoxEnabled) return;
        Minecraft mc = mc();
        if (mc.world == null || mc.player == null) return;

        float pt = event.getPartialTicks();
        double vx = mc.getRenderManager().viewerPosX;
        double vy = mc.getRenderManager().viewerPosY;
        double vz = mc.getRenderManager().viewerPosZ;

        Entity target = (mc.objectMouseOver != null
                && mc.objectMouseOver.typeOfHit == RayTraceResult.Type.ENTITY)
                ? mc.objectMouseOver.entityHit : null;

        GlStateManager.pushMatrix();
        GlStateManager.pushAttrib();
        GlStateManager.disableTexture2D();
        GlStateManager.disableDepth();
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GL11.glLineWidth(2f);

        for (Entity e : mc.world.loadedEntityList) {
            if (!(e instanceof EntityPlayer)) continue;
            if (e == mc.player) continue;

            double x = e.lastTickPosX + (e.posX - e.lastTickPosX) * pt - vx;
            double y = e.lastTickPosY + (e.posY - e.lastTickPosY) * pt - vy;
            double z = e.lastTickPosZ + (e.posZ - e.lastTickPosZ) * pt - vz;

            AxisAlignedBB bb = e.getEntityBoundingBox()
                    .offset(-e.posX, -e.posY, -e.posZ)
                    .offset(x, y, z)
                    .grow(0.05);

            int color = (e == target) ? hitBoxTargetColor : hitBoxColor;
            float r = ((color >> 16) & 0xFF) / 255f;
            float g = ((color >> 8) & 0xFF) / 255f;
            float b = (color & 0xFF) / 255f;

            GlStateManager.color(r, g, b, 1f);
            RenderGlobal.drawSelectionBoundingBox(bb, r, g, b, 1f);
        }

        GlStateManager.color(1f, 1f, 1f, 1f);
        GL11.glLineWidth(1f);
        GlStateManager.disableBlend();
        GlStateManager.enableLighting();
        GlStateManager.enableDepth();
        GlStateManager.enableTexture2D();
        GlStateManager.popAttrib();
        GlStateManager.popMatrix();
    }

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.getType() != RenderGameOverlayEvent.ElementType.ALL) return;
        Minecraft mc = mc();
        if (mc.player == null) return;

        ScaledResolution sr = new ScaledResolution(mc);
        int sw = sr.getScaledWidth();
        int sh = sr.getScaledHeight();
        int pad = 4;

        if (keysInfEnabled) {
            List<String> lines = new ArrayList<>();
            if (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)) lines.add("L.Shift");
            if (Keyboard.isKeyDown(Keyboard.KEY_W)) lines.add("W");
            if (Keyboard.isKeyDown(Keyboard.KEY_A)) lines.add("A");
            if (Keyboard.isKeyDown(Keyboard.KEY_S)) lines.add("S");
            if (Keyboard.isKeyDown(Keyboard.KEY_D)) lines.add("D");
            if (Mouse.isButtonDown(1)) lines.add("RMB");
            if (Keyboard.isKeyDown(Keyboard.KEY_SPACE)) lines.add("Space");
            if (!lines.isEmpty()) {
                int lineH = 10;
                int boxW = 60;
                int boxH = lines.size() * lineH + 6;
                int[] p = corner(keysInfCorner, sw, sh, boxW, boxH, pad);
                Gui.drawRect(p[0], p[1], p[0] + boxW, p[1] + boxH, 0x80000000);
                for (int i = 0; i < lines.size(); i++) {
                    mc.fontRenderer.drawString(lines.get(i), p[0] + 4, p[1] + 3 + i * lineH, keysInfColor);
                }
            }
        }

        if (coordsEnabled) {
            String txt = String.format("X: %.1f  Y: %.1f  Z: %.1f",
                    mc.player.posX, mc.player.posY, mc.player.posZ);
            int w = mc.fontRenderer.getStringWidth(txt) + 6;
            int[] p = corner(coordsCorner, sw, sh, w, 12, pad);
            Gui.drawRect(p[0], p[1], p[0] + w, p[1] + 12, 0x80000000);
            mc.fontRenderer.drawString(txt, p[0] + 3, p[1] + 2, coordsColor);
        }

        if (fpsEnabled) {
            String txt = "FPS: " + Minecraft.getDebugFPS();
            int w = mc.fontRenderer.getStringWidth(txt) + 6;
            int[] p = corner(fpsCorner, sw, sh, w, 12, pad);
            Gui.drawRect(p[0], p[1], p[0] + w, p[1] + 12, 0x80000000);
            mc.fontRenderer.drawString(txt, p[0] + 3, p[1] + 2, fpsColor);
        }

        if (pingEnabled && mc.getConnection() != null
                && mc.getConnection().getPlayerInfo(mc.player.getUniqueID()) != null) {
            int ping = mc.getConnection().getPlayerInfo(mc.player.getUniqueID()).getResponseTime();
            String txt = "Ping: " + ping + "ms";
            int w = mc.fontRenderer.getStringWidth(txt) + 6;
            int[] p = corner(pingCorner, sw, sh, w, 12, pad);
            Gui.drawRect(p[0], p[1], p[0] + w, p[1] + 12, 0x80000000);
            mc.fontRenderer.drawString(txt, p[0] + 3, p[1] + 2, pingColor);
        }

        if (armorHudEnabled) {
            int y = sh - 60;
            int x = sw / 2 + 90;
            for (int i = 3; i >= 0; i--) {
                net.minecraft.item.ItemStack st = mc.player.inventory.armorItemInSlot(i);
                if (!st.isEmpty()) {
                    int dmg = st.getMaxDamage() - st.getItemDamage();
                    mc.fontRenderer.drawStringWithShadow(dmg + "", x, y, 0xFFFFFF);
                    y += 10;
                }
            }
        }

        if (potionTimerEnabled) {
            Collection<PotionEffect> effects = mc.player.getActivePotionEffects();
            int y = 20;
            for (PotionEffect ef : effects) {
                String name = net.minecraft.client.resources.I18n.format(ef.getEffectName());
                String txt = name + " " + formatTime(ef.getDuration());
                mc.fontRenderer.drawStringWithShadow(txt, 4, y, 0xFFFFFF);
                y += 10;
            }
        }
    }

    private static String formatTime(int ticks) {
        int s = ticks / 20;
        return String.format("%d:%02d", s / 60, s % 60);
    }

    private static int[] corner(int c, int sw, int sh, int w, int h, int pad) {
        switch (c) {
            case 0: return new int[]{pad, pad};
            case 1: return new int[]{sw - w - pad, pad};
            case 2: return new int[]{pad, sh - h - pad};
            default: return new int[]{sw - w - pad, sh - h - pad};
        }
    }

    private static boolean isInFov(Minecraft mc, EntityLivingBase target, float fov) {
        double dx = target.posX - mc.player.posX;
        double dz = target.posZ - mc.player.posZ;
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90);
        float diff = Math.abs(mc.player.rotationYaw - yaw) % 360;
        if (diff > 180) diff = 360 - diff;
        return diff <= fov / 2f;
    }

    private static int randInt(int min, int max) {
        if (max <= min) return min;
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    private static boolean chance(float percent) {
        return ThreadLocalRandom.current().nextFloat() * 100f < percent;
    }

    private static void loadConfig() {
        try {
            config = new Configuration(new File("config/sunset.cfg"));
            config.load();

            killAuraEnabled = config.get("combat", "killAura", false).getBoolean();
            killAuraFov = (float) config.get("combat", "killAuraFov", 30.0).getDouble();
            killAuraMiss = (float) config.get("combat", "killAuraMiss", 20.0).getDouble();
            triggerBotEnabled = config.get("combat", "triggerBot", false).getBoolean();
            triggerBotMiss = (float) config.get("combat", "triggerBotMiss", 20.0).getDouble();

            fullbrightEnabled = config.get("v", "fullbright", false).getBoolean();
            fovOverride = config.get("v", "fovOverride", false).getBoolean();
            customFov = (float) config.get("v", "customFov", 70.0).getDouble();
            skyColorEnabled = config.get("v", "skyColor", false).getBoolean();
            skyColor = config.get("v", "skyColorValue", 0x87CEEB).getInt();
            hitBoxEnabled = config.get("v", "hitBox", false).getBoolean();
            hitBoxColor = config.get("v", "hitBoxColor", 0x00FF00).getInt();
            hitBoxTargetColor = config.get("v", "hitBoxTargetColor", 0xFF0000).getInt();
            disFireEnabled = config.get("v", "disFire", true).getBoolean();

            keysInfEnabled = config.get("hud", "keysInf", false).getBoolean();
            keysInfCorner = config.get("hud", "keysInfCorner", 0).getInt();
            keysInfColor = config.get("hud", "keysInfColor", 0xFFFFFF).getInt();
            coordsEnabled = config.get("hud", "coords", false).getBoolean();
            coordsCorner = config.get("hud", "coordsCorner", 0).getInt();
            coordsColor = config.get("hud", "coordsColor", 0xFFFFFF).getInt();
            fpsEnabled = config.get("hud", "fps", false).getBoolean();
            fpsCorner = config.get("hud", "fpsCorner", 1).getInt();
            fpsColor = config.get("hud", "fpsColor", 0xFFFFFF).getInt();
            pingEnabled = config.get("hud", "ping", false).getBoolean();
            pingCorner = config.get("hud", "pingCorner", 1).getInt();
            pingColor = config.get("hud", "pingColor", 0xFFFFFF).getInt();
            armorHudEnabled = config.get("hud", "armorHud", false).getBoolean();
            potionTimerEnabled = config.get("hud", "potionTimer", false).getBoolean();

            if (config.hasChanged()) config.save();
        } catch (Exception e) { LOG.error(e); }
    }

    public static void saveConfig() {
        if (config == null) return;
        try {
            config.get("combat", "killAura", false).set(killAuraEnabled);
            config.get("combat", "killAuraFov", 30.0).set(killAuraFov);
            config.get("combat", "killAuraMiss", 20.0).set(killAuraMiss);
            config.get("combat", "triggerBot", false).set(triggerBotEnabled);
            config.get("combat", "triggerBotMiss", 20.0).set(triggerBotMiss);

            config.get("v", "fullbright", false).set(fullbrightEnabled);
            config.get("v", "fovOverride", false).set(fovOverride);
            config.get("v", "customFov", 70.0).set(customFov);
            config.get("v", "skyColor", false).set(skyColorEnabled);
            config.get("v", "skyColorValue", 0x87CEEB).set(skyColor);
            config.get("v", "hitBox", false).set(hitBoxEnabled);
            config.get("v", "hitBoxColor", 0x00FF00).set(hitBoxColor);
            config.get("v", "hitBoxTargetColor", 0xFF0000).set(hitBoxTargetColor);
            config.get("v", "disFire", true).set(disFireEnabled);

            config.get("hud", "keysInf", false).set(keysInfEnabled);
            config.get("hud", "keysInfCorner", 0).set(keysInfCorner);
            config.get("hud", "keysInfColor", 0xFFFFFF).set(keysInfColor);
            config.get("hud", "coords", false).set(coordsEnabled);
            config.get("hud", "coordsCorner", 0).set(coordsCorner);
            config.get("hud", "coordsColor", 0xFFFFFF).set(coordsColor);
            config.get("hud", "fps", false).set(fpsEnabled);
            config.get("hud", "fpsCorner", 1).set(fpsCorner);
            config.get("hud", "fpsColor", 0xFFFFFF).set(fpsColor);
            config.get("hud", "ping", false).set(pingEnabled);
            config.get("hud", "pingCorner", 1).set(pingCorner);
            config.get("hud", "pingColor", 0xFFFFFF).set(pingColor);
            config.get("hud", "armorHud", false).set(armorHudEnabled);
            config.get("hud", "potionTimer", false).set(potionTimerEnabled);
            config.save();
        } catch (Exception e) { LOG.error(e); }
    }

    public static class SunsetGui extends GuiScreen {

        private static final int W = 280, H = 300, TITLE_H = 20;
        private static final int HEADER_OFF = TITLE_H + 5, ROW = 14;

        private int x, y, dragX, dragY;
        private boolean dragging;
        private int scroll, contentH;

        private final List<Btn> buttons = new ArrayList<>();
        private final List<Integer> headerOffsets = new ArrayList<>();
        private final List<String> headerNames = new ArrayList<>();

        @Override
        public void initGui() {
            x = width / 2 - W / 2;
            y = height / 2 - H / 2;
            scroll = 0; dragging = false;
            layout();
        }

        private void layout() {
            Map<String, Boolean> state = new HashMap<>();
            for (Btn b : buttons) state.put(b.name, b.expanded);

            buttons.clear(); headerNames.clear(); headerOffsets.clear();
            int off = 0;

            headerNames.add("Combat"); headerOffsets.add(off); off += ROW;
            off = addWithState(new Btn("KillAura", () -> killAuraEnabled, v -> {
                killAuraEnabled = v;
                if (v) triggerBotEnabled = false;
            }, new Sl("FOV", () -> killAuraFov, v -> killAuraFov = v, 1f, 180f),
                    new Sl("Miss %", () -> killAuraMiss, v -> killAuraMiss = v, 0f, 100f)), off, state);
            off = addWithState(new Btn("TriggerBot", () -> triggerBotEnabled, v -> {
                triggerBotEnabled = v;
                if (v) killAuraEnabled = false;
            }, new Sl("Miss %", () -> triggerBotMiss, v -> triggerBotMiss = v, 0f, 100f)), off, state);
            off += 4;

            headerNames.add("Visual"); headerOffsets.add(off); off += ROW;
            off = addWithState(new Btn("Fullbright", () -> fullbrightEnabled, v -> fullbrightEnabled = v), off, state);
            off = addWithState(new Btn("FOV", () -> fovOverride, v -> fovOverride = v,
                    new Sl("Value", () -> customFov, v -> customFov = v, 10f, 130f)), off, state);
            off = addWithState(new Btn("SkyColor", () -> skyColorEnabled, v -> skyColorEnabled = v,
                    new Cl("Sky", () -> skyColor, v -> skyColor = v)), off, state);
            off = addWithState(new Btn("HitBox", () -> hitBoxEnabled, v -> hitBoxEnabled = v,
                    new Cl("Self", () -> hitBoxColor, v -> hitBoxColor = v),
                    new Cl("Target", () -> hitBoxTargetColor, v -> hitBoxTargetColor = v)), off, state);
            off = addWithState(new Btn("DisFire", () -> disFireEnabled, v -> disFireEnabled = v), off, state);
            off += 4;

            headerNames.add("HUD"); headerOffsets.add(off); off += ROW;
            off = addWithState(new Btn("KeysInf", () -> keysInfEnabled, v -> keysInfEnabled = v,
                    new Sl("Corner", () -> (float) keysInfCorner, v -> keysInfCorner = (int) v, 0f, 3f),
                    new Cl("Text", () -> keysInfColor, v -> keysInfColor = v)), off, state);
            off = addWithState(new Btn("Coords", () -> coordsEnabled, v -> coordsEnabled = v,
                    new Sl("Corner", () -> (float) coordsCorner, v -> coordsCorner = (int) v, 0f, 3f),
                    new Cl("Text", () -> coordsColor, v -> coordsColor = v)), off, state);
            off = addWithState(new Btn("FPS", () -> fpsEnabled, v -> fpsEnabled = v,
                    new Sl("Corner", () -> (float) fpsCorner, v -> fpsCorner = (int) v, 0f, 3f),
                    new Cl("Text", () -> fpsColor, v -> fpsColor = v)), off, state);
            off = addWithState(new Btn("Ping", () -> pingEnabled, v -> pingEnabled = v,
                    new Sl("Corner", () -> (float) pingCorner, v -> pingCorner = (int) v, 0f, 3f),
                    new Cl("Text", () -> pingColor, v -> pingColor = v)), off, state);
            off = addWithState(new Btn("ArmorHUD", () -> armorHudEnabled, v -> armorHudEnabled = v), off, state);
            off = addWithState(new Btn("PotionTimer", () -> potionTimerEnabled, v -> potionTimerEnabled = v), off, state);

            contentH = off;
        }

        private int addWithState(Btn b, int off, Map<String, Boolean> state) {
            Boolean exp = state.get(b.name);
            if (exp != null) b.expanded = exp;
            b.offset = off;
            buttons.add(b);
            return off + ROW + (b.expanded ? (b.sliders.size() + b.colors.size()) * 12 : 0);
        }

        private int viewH() { return H - HEADER_OFF; }

        @Override
        public void drawScreen(int mx, int my, float pt) {
            drawRect(0, 0, width, height, new Color(0, 0, 0, 140).getRGB());
            drawRect(x, y, x + W, y + H, 0xC0000000);
            drawRect(x, y, x + W, y + TITLE_H, 0xFF1E1E1E);
            drawString(fontRenderer, "Sunset 4.0", x + 6, y + 6, 0xFFFFFF);

            for (int i = 0; i < headerNames.size(); i++) {
                fontRenderer.drawString(headerNames.get(i), x + 8,
                        y + HEADER_OFF + headerOffsets.get(i) + scroll, 0xAAAAAA);
            }
            for (Btn b : buttons) b.draw(x + 5, y + HEADER_OFF + b.offset + scroll, mx, my);

            int vh = viewH();
            if (contentH > vh) {
                int bx = x + W - 5, by = y + HEADER_OFF;
                drawRect(bx, by, bx + 3, by + vh, 0x55555555);
                int thumbH = Math.max(20, (int)(vh * ((float)vh / contentH)));
                int maxScroll = contentH - vh;
                float sr = maxScroll > 0 ? (float)-scroll / maxScroll : 0;
                int ty = by + (int)((vh - thumbH) * sr);
                drawRect(bx, ty, bx + 3, ty + thumbH, 0xFF3399FF);
            }
            super.drawScreen(mx, my, pt);
        }

        private boolean inside(int mx, int my) { return mx >= x && mx <= x + W && my >= y && my <= y + H; }

        @Override
        protected void mouseClicked(int mx, int my, int btn) throws java.io.IOException {
            if (!inside(mx, my)) { super.mouseClicked(mx, my, btn); return; }
            if (btn == 0 && my >= y && my <= y + TITLE_H) {
                dragging = true; dragX = mx - x; dragY = my - y; return;
            }

            List<Btn> copy = new ArrayList<>(buttons);
            boolean needLayout = false;
            for (Btn b : copy) {
                boolean before = b.expanded;
                b.click(mx, my, btn, x + 5, y + HEADER_OFF + b.offset + scroll);
                if (b.expanded != before) needLayout = true;
            }
            if (needLayout) layout();

            super.mouseClicked(mx, my, btn);
        }

        @Override
        protected void mouseReleased(int mx, int my, int state) {
            dragging = false;
            for (Btn b : buttons) b.release();
            super.mouseReleased(mx, my, state);
        }

        @Override
        protected void mouseClickMove(int mx, int my, int btn, long dt) {
            if (dragging) { x = mx - dragX; y = my - dragY; }
            super.mouseClickMove(mx, my, btn, dt);
        }

        @Override
        public void handleMouseInput() throws java.io.IOException {
            super.handleMouseInput();
            int w = Mouse.getDWheel();
            if (w == 0) return;
            int mx = Mouse.getEventX() * width / mc.displayWidth;
            int my = height - Mouse.getEventY() * height / mc.displayHeight - 1;
            if (!inside(mx, my)) return;
            scroll += w > 0 ? 10 : -10;
            if (scroll > 0) scroll = 0;
            int min = Math.min(0, -(contentH - viewH()));
            if (scroll < min) scroll = min;
        }

        @Override
        public void onGuiClosed() { saveConfig(); super.onGuiClosed(); }

        @Override
        public boolean doesGuiPauseGame() { return false; }
    }

    public static class Btn {
        public interface BG { boolean get(); }
        public interface BS { void set(boolean v); }
        final String name; final BG get; final BS set;
        final List<Sl> sliders = new ArrayList<>();
        final List<Cl> colors = new ArrayList<>();
        int offset; boolean expanded;
        public Btn(String n, BG g, BS s, Object... items) {
            name = n; get = g; set = s;
            for (Object o : items) {
                if (o instanceof Sl) sliders.add((Sl) o);
                else if (o instanceof Cl) colors.add((Cl) o);
            }
        }
        void draw(int ax, int ay, int mx, int my) {
            Minecraft mc = Minecraft.getMinecraft();
            boolean hover = mx >= ax && mx <= ax + 170 && my >= ay && my <= ay + 12;
            Gui.drawRect(ax, ay, ax + 170, ay + 12, hover ? 0xBB282828 : 0xAA181818);
            int c = (get != null && get.get()) ? 0xFF00FF00
