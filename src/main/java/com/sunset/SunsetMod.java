package com.sunset;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.RayTraceResult;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.client.event.RenderBlockOverlayEvent;
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
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Mod(modid = "sunset", name = "Sunset", version = "2.1",
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

    public static boolean disFireEnabled = true;

    public static boolean fovOverride = false;
    public static float customFov = 70f;

    public static boolean skyColorEnabled = false;
    public static int skyColor = 0x87CEEB;

    public static boolean hitBoxEnabled = false;
    public static int hitBoxColor = 0x00FF00;
    public static int hitBoxTargetColor = 0xFF0000;

    public static boolean keysInfEnabled = false;
    public static int keysInfCorner = 0;
    public static int keysInfColor = 0xFFFFFF;

    private static long killAuraLast = 0;
    private static long triggerBotLast = 0;

    private static Minecraft mc() { return Minecraft.getMinecraft(); }

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        loadConfig();
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        guiKey = new KeyBinding("Open Sunset GUI", Keyboard.KEY_RSHIFT, "Sunset");
        ClientRegistry.registerKeyBinding(guiKey);
        MinecraftForge.EVENT_BUS.register(this);
        LOG.info("Sunset 2.1 initialized");
    }

    @SubscribeEvent
    public void onKey(InputEvent.KeyInputEvent event) {
        if (guiKey == null) return;
        if (guiKey.isPressed() && mc().currentScreen == null) {
            mc().displayGuiScreen(new SunsetGui());
        }
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (killAuraEnabled) doKillAura();
        if (triggerBotEnabled) doTriggerBot();
    }

    private static void doKillAura() {
        Minecraft mc = mc();
        if (mc.player == null || mc.world == null || mc.playerController == null) return;
        if (!mc.player.isEntityAlive()) return;

        EntityLivingBase target = null;
        double bestDist = Double.MAX_VALUE;

        for (net.minecraft.entity.Entity e : mc.world.getEntitiesWithinAABB(
                EntityLivingBase.class, mc.player.getEntityBoundingBox().grow(4.5))) {
            if (!(e instanceof EntityLivingBase)) continue;
            EntityLivingBase living = (EntityLivingBase) e;
            if (living == mc.player) continue;
            if (!living.isEntityAlive()) continue;
            if (!mc.player.canEntityBeSeen(living)) continue;
            if (!isInFov(mc, living, killAuraFov)) continue;

            double dist = mc.player.getDistance(living);
            if (dist < bestDist) { bestDist = dist; target = living; }
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
    public void onRenderBlockOverlay(RenderBlockOverlayEvent event) {
        if (!disFireEnabled) return;
        if (event.getOverlayType() != RenderBlockOverlayEvent.OverlayType.FIRE) return;
        if (event.getPlayer() == null) return;
        if (event.getPlayer() != mc().player) return;
        event.setCanceled(true);
    }

    @SubscribeEvent
    public void onFov(EntityViewRenderEvent.FOVModifier event) {
        if (!fovOverride) return;
        event.setFOV(customFov);
    }

    @SubscribeEvent
    public void onFogColors(EntityViewRenderEvent.FogColors event) {
        if (!skyColorEnabled) return;
        float r = ((skyColor >> 16) & 0xFF) / 255f;
        float g = ((skyColor >> 8) & 0xFF) / 255f;
        float b = (skyColor & 0xFF) / 255f;
        event.setRed(r);
        event.setGreen(g);
        event.setBlue(b);
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

        net.minecraft.entity.Entity target = (mc.objectMouseOver != null
                && mc.objectMouseOver.typeOfHit == RayTraceResult.Type.ENTITY)
                ? mc.objectMouseOver.entityHit : null;

        net.minecraft.client.renderer.GlStateManager.pushMatrix();
        net.minecraft.client.renderer.GlStateManager.pushAttrib();
        net.minecraft.client.renderer.GlStateManager.disableTexture2D();
        net.minecraft.client.renderer.GlStateManager.disableDepth();
        net.minecraft.client.renderer.GlStateManager.disableLighting();
        net.minecraft.client.renderer.GlStateManager.enableBlend();
        net.minecraft.client.renderer.GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GL11.glLineWidth(2f);

        for (net.minecraft.entity.Entity e : mc.world.loadedEntityList) {
            if (!(e instanceof net.minecraft.entity.player.EntityPlayer)) continue;
            if (e == mc.player) continue;

            double x = e.lastTickPosX + (e.posX - e.lastTickPosX) * pt - vx;
            double y = e.lastTickPosY + (e.posY - e.lastTickPosY) * pt - vy;
            double z = e.lastTickPosZ + (e.posZ - e.lastTickPosZ) * pt - vz;

            net.minecraft.util.math.AxisAlignedBB bb = e.getEntityBoundingBox()
                    .offset(-e.posX, -e.posY, -e.posZ)
                    .offset(x, y, z)
                    .grow(0.05);

            int color = (e == target) ? hitBoxTargetColor : hitBoxColor;
            float r = ((color >> 16) & 0xFF) / 255f;
            float g = ((color >> 8) & 0xFF) / 255f;
            float b = (color & 0xFF) / 255f;

            net.minecraft.client.renderer.GlStateManager.color(r, g, b, 1f);
            net.minecraft.client.renderer.RenderGlobal.drawSelectionBoundingBox(bb, r, g, b, 1f);
        }

        net.minecraft.client.renderer.GlStateManager.color(1f, 1f, 1f, 1f);
        GL11.glLineWidth(1f);
        net.minecraft.client.renderer.GlStateManager.disableBlend();
        net.minecraft.client.renderer.GlStateManager.enableLighting();
        net.minecraft.client.renderer.GlStateManager.enableDepth();
        net.minecraft.client.renderer.GlStateManager.enableTexture2D();
        net.minecraft.client.renderer.GlStateManager.popAttrib();
        net.minecraft.client.renderer.GlStateManager.popMatrix();
    }

    @SubscribeEvent
    public void onRenderOverlay(net.minecraftforge.client.event.RenderGameOverlayEvent.Post event) {
        if (event.getType() != net.minecraftforge.client.event.RenderGameOverlayEvent.ElementType.ALL) return;
        if (!keysInfEnabled) return;
        Minecraft mc = mc();
        if (mc.player == null) return;

        net.minecraft.client.gui.ScaledResolution sr = new net.minecraft.client.gui.ScaledResolution(mc);
        int sw = sr.getScaledWidth();
        int sh = sr.getScaledHeight();

        List<String> lines = new ArrayList<>();
        if (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)) lines.add("L.Shift");
        if (Keyboard.isKeyDown(Keyboard.KEY_W)) lines.add("W");
        if (Keyboard.isKeyDown(Keyboard.KEY_A)) lines.add("A");
        if (Keyboard.isKeyDown(Keyboard.KEY_S)) lines.add("S");
        if (Keyboard.isKeyDown(Keyboard.KEY_D)) lines.add("D");
        if (Mouse.isButtonDown(1)) lines.add("RMB");
        if (Keyboard.isKeyDown(Keyboard.KEY_SPACE)) lines.add("Space");

        if (lines.isEmpty()) return;

        int lineH = 10;
        int boxW = 60;
        int boxH = lines.size() * lineH + 6;
        int pad = 6;

        int x, y;
        switch (keysInfCorner) {
            case 0: x = pad; y = pad; break;
            case 1: x = sw - boxW - pad; y = pad; break;
            case 2: x = pad; y = sh - boxH - pad; break;
            default: x = sw - boxW - pad; y = sh - boxH - pad; break;
        }

        Gui.drawRect(x, y, x + boxW, y + boxH, 0x80000000);
        for (int i = 0; i < lines.size(); i++) {
            mc.fontRenderer.drawString(lines.get(i), x + 4, y + 3 + i * lineH, keysInfColor);
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

            disFireEnabled = config.get("visual", "disFire", true).getBoolean();
            fovOverride = config.get("visual", "fovOverride", false).getBoolean();
            customFov = (float) config.get("visual", "customFov", 70.0).getDouble();
            skyColorEnabled = config.get("visual", "skyColor", false).getBoolean();
            skyColor = config.get("visual", "skyColorValue", 0x87CEEB).getInt();
            hitBoxEnabled = config.get("visual", "hitBox", false).getBoolean();
            hitBoxColor = config.get("visual", "hitBoxColor", 0x00FF00).getInt();
            hitBoxTargetColor = config.get("visual", "hitBoxTargetColor", 0xFF0000).getInt();

            keysInfEnabled = config.get("hud", "keysInf", false).getBoolean();
            keysInfCorner = config.get("hud", "keysInfCorner", 0).getInt();
            keysInfColor = config.get("hud", "keysInfColor", 0xFFFFFF).getInt();

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

            config.get("visual", "disFire", true).set(disFireEnabled);
            config.get("visual", "fovOverride", false).set(fovOverride);
            config.get("visual", "customFov", 70.0).set(customFov);
            config.get("visual", "skyColor", false).set(skyColorEnabled);
            config.get("visual", "skyColorValue", 0x87CEEB).set(skyColor);
            config.get("visual", "hitBox", false).set(hitBoxEnabled);
            config.get("visual", "hitBoxColor", 0x00FF00).set(hitBoxColor);
            config.get("visual", "hitBoxTargetColor", 0xFF0000).set(hitBoxTargetColor);

            config.get("hud", "keysInf", false).set(keysInfEnabled);
            config.get("hud", "keysInfCorner", 0).set(keysInfCorner);
            config.get("hud", "keysInfColor", 0xFFFFFF).set(keysInfColor);
            config.save();
        } catch (Exception e) { LOG.error(e); }
    }

    public static class SunsetGui extends GuiScreen {

        private static final int W = 280, H = 300, TITLE_H = 20;
        private static final int HEADER_OFF = TITLE_H + 5, ROW = 14;

        private int x, y, dragX, dragY;
        private boolean dragging;
        private int scroll, contentH;

        private final List<GuiButtonEntry> buttons = new ArrayList<>();
        private final List<Integer> headerOffsets = new ArrayList<>();
        private final List<String> headerNames = new ArrayList<>();

        @Override
        public void initGui() {
            x = width / 2 - W / 2;
            y = height / 2 - H / 2;
            scroll = 0;
            dragging = false;
            layout();
        }

        private void layout() {
            buttons.clear();
            headerNames.clear();
            headerOffsets.clear();

            int off = 0;

            headerNames.add("Combat");
            headerOffsets.add(off);
            off += ROW;

            GuiButtonEntry ka = new GuiButtonEntry("KillAura", () -> killAuraEnabled, v -> {
                killAuraEnabled = v;
                if (v) triggerBotEnabled = false;
            }, new SliderEntry("FOV", () -> killAuraFov, v -> killAuraFov = v, 1f, 180f),
                    new SliderEntry("Miss %", () -> killAuraMiss, v -> killAuraMiss = v, 0f, 100f));
            ka.offset = off;
            buttons.add(ka);
            off += ROW + expandedHeight(ka);

            GuiButtonEntry tb = new GuiButtonEntry("TriggerBot", () -> triggerBotEnabled, v -> {
                triggerBotEnabled = v;
                if (v) killAuraEnabled = false;
            }, new SliderEntry("Miss %", () -> triggerBotMiss, v -> triggerBotMiss = v, 0f, 100f));
            tb.offset = off;
            buttons.add(tb);
            off += ROW + expandedHeight(tb);

            off += 4;

            headerNames.add("Visual");
            headerOffsets.add(off);
            off += ROW;

            GuiButtonEntry df = new GuiButtonEntry("DisFire", () -> disFireEnabled, v -> disFireEnabled = v);
            df.offset = off; buttons.add(df); off += ROW + expandedHeight(df);

            GuiButtonEntry fv = new GuiButtonEntry("FOV", () -> fovOverride, v -> fovOverride = v,
                    new SliderEntry("Value", () -> customFov, v -> customFov = v, 10f, 130f));
            fv.offset = off; buttons.add(fv); off += ROW + expandedHeight(fv);

            GuiButtonEntry sc = new GuiButtonEntry("SkyColor", () -> skyColorEnabled, v -> skyColorEnabled = v,
                    new ColorEntry("Sky", () -> skyColor, v -> skyColor = v));
            sc.offset = off; buttons.add(sc); off += ROW + expandedHeight(sc);

            GuiButtonEntry hb = new GuiButtonEntry("HitBox", () -> hitBoxEnabled, v -> hitBoxEnabled = v,
                    new ColorEntry("Self", () -> hitBoxColor, v -> hitBoxColor = v),
                    new ColorEntry("Target", () -> hitBoxTargetColor, v -> hitBoxTargetColor = v));
            hb.offset = off; buttons.add(hb); off += ROW + expandedHeight(hb);

            off += 4;

            headerNames.add("HUD");
            headerOffsets.add(off);
            off += ROW;

            GuiButtonEntry ki = new GuiButtonEntry("KeysInf", () -> keysInfEnabled, v -> keysInfEnabled = v,
                    new SliderEntry("Corner", () -> (float) keysInfCorner, v -> keysInfCorner = (int) v, 0f, 3f),
                    new ColorEntry("Text", () -> keysInfColor, v -> keysInfColor = v));
            ki.offset = off; buttons.add(ki); off += ROW + expandedHeight(ki);

            contentH = off;
        }

        private int expandedHeight(GuiButtonEntry b) {
            if (!b.expanded) return 0;
            return (b.sliders.size() + b.colors.size()) * 12;
        }

        private int viewH() { return H - HEADER_OFF; }

        @Override
        public void drawScreen(int mouseX, int mouseY, float pt) {
            drawRect(0, 0, width, height, new Color(0, 0, 0, 140).getRGB());
            drawRect(x, y, x + W, y + H, 0xC0000000);
            drawRect(x, y, x + W, y + TITLE_H, 0xFF1E1E1E);
            drawString(fontRenderer, "Sunset 2.1", x + 6, y + 6, 0xFFFFFF);

            for (int i = 0; i < headerNames.size(); i++) {
                fontRenderer.drawString(headerNames.get(i), x + 8, y + HEADER_OFF + headerOffsets.get(i) + scroll, 0xAAAAAA);
            }
            for (GuiButtonEntry b : buttons) {
                b.draw(x + 5, y + HEADER_OFF + b.offset + scroll, mouseX, mouseY);
            }

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
            super.drawScreen(mouseX, mouseY, pt);
        }

        private boolean inside(int mx, int my) { return mx >= x && mx <= x + W && my >= y && my <= y + H; }

        @Override
        protected void mouseClicked(int mx, int my, int btn) throws java.io.IOException {
            if (!inside(mx, my)) { super.mouseClicked(mx, my, btn); return; }
            if (btn == 0 && my >= y && my <= y + TITLE_H) {
                dragging = true; dragX = mx - x; dragY = my - y; return;
            }
            for (GuiButtonEntry b : buttons) {
                boolean before = b.expanded;
                b.click(mx, my, btn, x + 5, y + HEADER_OFF + b.offset + scroll);
                if (b.expanded != before) layout();
            }
            super.mouseClicked(mx, my, btn);
        }

        @Override
        protected void mouseReleased(int mx, int my, int state) {
            dragging = false;
            for (GuiButtonEntry b : buttons) b.release();
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
            int vh = viewH();
            int min = Math.min(0, -(contentH - vh));
            if (scroll < min) scroll = min;
        }

        @Override
        public void onGuiClosed() { saveConfig(); super.onGuiClosed(); }

        @Override
        public boolean doesGuiPauseGame() { return false; }
    }

    public static class GuiButtonEntry {
        public interface BGet { boolean get(); }
        public interface BSet { void set(boolean v); }

        final String name;
        final BGet get; final BSet set;
        final List<SliderEntry> sliders = new ArrayList<>();
        final List<ColorEntry> colors = new ArrayList<>();
        int offset;
        boolean expanded;

        public GuiButtonEntry(String name, BGet g, BSet s, Object... items) {
            this.name = name; this.get = g; this.set = s;
            for (Object o : items) {
                if (o instanceof SliderEntry) sliders.add((SliderEntry) o);
                else if (o instanceof ColorEntry) colors.add((ColorEntry) o);
            }
        }

        void draw(int ax, int ay, int mx, int my) {
            Minecraft mc = Minecraft.getMinecraft();
            boolean hover = mx >= ax && mx <= ax + 170 && my >= ay && my <= ay + 12;
            Gui.drawRect(ax, ay, ax + 170, ay + 12, hover ? 0xBB282828 : 0xAA181818);
            int c = (get != null && get.get()) ? 0xFF00FF00 : 0xFFFF5555;
            if (get == null) c = 0xFFFFFFFF;
            mc.fontRenderer.drawString(name, ax + 3, ay + 2, c);

            if (expanded) {
                int sy = ay + 14;
                for (SliderEntry s : sliders) { s.draw(ax + 5, sy, mx); sy += 12; }
                for (ColorEntry ce : colors) { ce.draw(ax + 5, sy, mx, my); sy += 12; }
            }
        }

        void click(int mx, int my, int btn, int ax, int ay) {
            if (mx >= ax && mx <= ax + 170 && my >= ay && my <= ay + 12) {
                if (btn == 0 && get != null) set.set(!get.get());
                else if (btn == 1) expanded = !expanded;
                return;
            }
            if (!expanded) return;
            int sy = ay + 14;
            for (SliderEntry s : sliders) {
                if (btn == 0 && mx >= ax + 5 && mx <= ax + 145 && my >= sy && my <= sy + 10) s.drag = true;
                sy += 12;
            }
            for (ColorEntry ce : colors) {
                ce.click(mx, my, btn, ax + 5, sy);
                sy += 12;
            }
        }

        void release() { for (SliderEntry s : sliders) s.drag = false; }
    }

    public static class SliderEntry {
        public interface FGet { float get(); }
        public interface FSet { void set(float v); }

        final String name;
        final FGet get; final FSet set;
        final float min, max;
        boolean drag;

        public SliderEntry(String n, FGet g, FSet s, float mn, float mx) {
            name = n; get = g; set = s; min = mn; max = mx;
        }

        void draw(int sx, int sy, int mx) {
            Minecraft mc = Minecraft.getMinecraft();
            int w = 140, h = 10;
            float v = get.get();
            float p = Math.max(0, Math.min(1, (v - min) / (max - min)));
            Gui.drawRect(sx, sy, sx + w, sy + h, 0xAA282828);
            Gui.drawRect(sx, sy, (int)(sx + w * p), sy + h, 0xFF3399FF);
            String val = (max - min) > 10 ? String.format("%.0f", v) : String.format("%.1f", v);
            mc.fontRenderer.drawString(name + ": " + val, sx + 2, sy + 1, 0xFFFFFF);
            if (drag) {
                float np = Math.max(0, Math.min(1, (float)(mx - sx) / w));
                set.set(min + np * (max - min));
            }
        }
    }

    public static class ColorEntry {
        public interface IGet { int get(); }
        public interface ISet { void set(int v); }

        static final int[] PRESETS = {
            0xFF0000, 0x00FF00, 0x0000FF, 0xFFFF00,
            0xFF00FF, 0x00FFFF, 0xFFFFFF, 0x000000,
            0x3399FF, 0x87CEEB, 0xAAAAAA, 0xFF8000
        };

        final String name;
        final IGet get; final ISet set;

        public ColorEntry(String n, IGet g, ISet s) { name = n; get = g; set = s; }

        void draw(int sx, int sy, int mx, int my) {
            Minecraft mc = Minecraft.getMinecraft();
            int v = get.get();
            Gui.drawRect(sx, sy, sx + 160, sy + 10, 0xAA282828);
            int sq = 9;
            for (int i = 0; i < PRESETS.length; i++) {
                int px = sx + 2 + i * (sq + 1);
                Gui.drawRect(px, sy + 1, px + sq, sy + 9, PRESETS[i] | 0xFF000000);
                boolean hov = mx >= px && mx <= px + sq && my >= sy + 1 && my <= sy + 9;
                if (hov) Gui.drawRect(px, sy + 9, px + sq, sy + 10, 0xFFFFFFFF);
            }
            Gui.drawRect(sx + 145, sy + 2, sx + 155, sy + 8, v | 0xFF000000);
        }

        void click(int mx, int my, int btn, int ax, int ay) {
            if (my < ay || my > ay + 10) return;
            int sq = 9;
            for (int i = 0; i < PRESETS.length; i++) {
                int px = ax + 2 + i * (sq + 1);
                if (btn == 0 && mx >= px && mx <= px + sq) { set.set(PRESETS[i]); return; }
            }
            if (mx >= ax + 145 && mx <= ax + 160) {
                if (btn == 0) set.set(shiftHue(get.get(), 1f / 24f));
                else if (btn == 1) set.set(shiftHue(get.get(), -1f / 24f));
            }
        }

        static int shiftHue(int rgb, float d) {
            int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
            float[] hsv = Color.RGBtoHSB(r, g, b, null);
            hsv[0] = (hsv[0] + d + 1f) % 1f;
            return Color.HSBtoRGB(hsv[0], Math.max(0.85f, hsv[1]), Math.max(0.85f, hsv[2])) & 0xFFFFFF;
        }
    }
}
