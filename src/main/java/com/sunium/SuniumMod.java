package com.sunium;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Mod(modid = "sunium", name = "Sunium", version = "5.0",
        acceptedMinecraftVersions = "[1.12.2]")
public class SuniumMod {

    private static final Logger LOG = LogManager.getLogger("Sunium");
    public static KeyBinding guiKey;

    public static boolean autoRenderDistance = true;
    public static final int MIN_RENDER_DIST = 4;
    public static final int MAX_RENDER_DIST = 12;
    public static final int AUTO_DIST_THRESHOLD = 45;
    private static int savedRenderDistance = -1;

    public static boolean backgroundFpsEnabled = true;
    public static final int BACKGROUND_FPS = 30;
    private static int savedLimitFramerate = -1;

    public static boolean autoParticles = true;
    public static final int AUTO_PARTICLES_THRESHOLD = 40;
    private static int savedParticleSetting = -1;

    public static boolean recipeCleaner = true;
    public static boolean textureAnimOff = false;

    public static boolean dropMerger = true;
    public static final float MERGE_RADIUS = 2.0f;
    private static long lastMergeTick = 0;

    private static Minecraft mc() { return Minecraft.getMinecraft(); }

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        LOG.info("Sunium preInit");
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        guiKey = new KeyBinding("Open Sunium GUI", org.lwjgl.input.Keyboard.KEY_RSHIFT, "Sunium");
        ClientRegistry.registerKeyBinding(guiKey);
        MinecraftForge.EVENT_BUS.register(this);
        LOG.info("Sunium initialized");
    }

    @SubscribeEvent
    public void onKey(InputEvent.KeyInputEvent event) {
        if (guiKey == null) return;
        if (guiKey.isPressed() && mc().currentScreen == null) {
            mc().displayGuiScreen(new SuniumGui());
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = mc();
        if (mc.gameSettings == null) return;

        if (autoRenderDistance) {
            int fps = Minecraft.getDebugFPS();
            if (savedRenderDistance < 0) savedRenderDistance = mc.gameSettings.renderDistanceChunks;
            if (fps > 0 && fps < AUTO_DIST_THRESHOLD) {
                if (mc.gameSettings.renderDistanceChunks > MIN_RENDER_DIST) {
                    mc.gameSettings.renderDistanceChunks--;
                }
            } else if (fps > AUTO_DIST_THRESHOLD + 15) {
                if (mc.gameSettings.renderDistanceChunks < MAX_RENDER_DIST) {
                    mc.gameSettings.renderDistanceChunks++;
                }
            }
        } else if (savedRenderDistance >= 0) {
            mc.gameSettings.renderDistanceChunks = savedRenderDistance;
            savedRenderDistance = -1;
        }

        if (backgroundFpsEnabled) {
            if (!mc.isGameFocused()) {
                if (savedLimitFramerate < 0) savedLimitFramerate = mc.gameSettings.limitFramerate;
                if (mc.gameSettings.limitFramerate > BACKGROUND_FPS) {
                    mc.gameSettings.limitFramerate = BACKGROUND_FPS;
                }
            } else if (savedLimitFramerate >= 0) {
                mc.gameSettings.limitFramerate = savedLimitFramerate;
                savedLimitFramerate = -1;
            }
        } else if (savedLimitFramerate >= 0) {
            mc.gameSettings.limitFramerate = savedLimitFramerate;
            savedLimitFramerate = -1;
        }

        if (autoParticles) {
            int fps = Minecraft.getDebugFPS();
            if (savedParticleSetting < 0) savedParticleSetting = mc.gameSettings.particleSetting;
            if (fps > 0 && fps < AUTO_PARTICLES_THRESHOLD) {
                if (mc.gameSettings.particleSetting < 2) {
                    mc.gameSettings.particleSetting = 2;
                }
            } else if (fps > AUTO_PARTICLES_THRESHOLD + 20) {
                mc.gameSettings.particleSetting = savedParticleSetting;
            }
        } else if (savedParticleSetting >= 0) {
            mc.gameSettings.particleSetting = savedParticleSetting;
            savedParticleSetting = -1;
        }

        if (dropMerger && mc.world != null && mc.player != null) {
            long now = mc.world.getTotalWorldTime();
            if (now - lastMergeTick >= 20) {
                lastMergeTick = now;
                mergeDrops(mc);
            }
        }
    }

    private static void mergeDrops(Minecraft mc) {
        List<Entity> items = new ArrayList<>();
        for (Entity e : mc.world.loadedEntityList) {
            if (e instanceof EntityItem || e instanceof EntityXPOrb) items.add(e);
        }

        boolean[] removed = new boolean[items.size()];
        for (int i = 0; i < items.size(); i++) {
            if (removed[i]) continue;
            Entity base = items.get(i);
            int count = base instanceof EntityItem ? ((EntityItem) base).getItem().getCount() : 1;

            for (int j = i + 1; j < items.size(); j++) {
                if (removed[j]) continue;
                Entity other = items.get(j);

                if (base.getClass() != other.getClass()) continue;
                if (base.getDistance(other) > MERGE_RADIUS) continue;

                if (base instanceof EntityItem) {
                    EntityItem b = (EntityItem) base;
                    EntityItem o = (EntityItem) other;
                    if (!net.minecraft.item.ItemStack.areItemStacksEqual(b.getItem(), o.getItem())) continue;
                    if (!net.minecraft.item.ItemStack.areItemStackTagsEqual(b.getItem(), o.getItem())) continue;
                    count += o.getItem().getCount();
                    b.getItem().setCount(Math.min(count, 64));
                    removed[j] = true;
                    other.setDead();
                } else {
                    EntityXPOrb b = (EntityXPOrb) base;
                    EntityXPOrb o = (EntityXPOrb) other;
                    b.xpValue += o.xpValue;
                    removed[j] = true;
                    other.setDead();
                }
            }
        }
    }

    @SubscribeEvent
    public void onWorldLoad(WorldEvent.Load event) {
        if (!recipeCleaner) return;
        if (!event.getWorld().isRemote) return;

        try {
            java.lang.reflect.Field f = CraftingManager.class.getDeclaredField("recipes");
            f.setAccessible(true);
            List<IRecipe> recipes = (List<IRecipe>) f.get(CraftingManager.getInstance());
            if (recipes != null) {
                int before = recipes.size();
                recipes.clear();
                LOG.info("RecipeCleaner cleared " + before + " recipes");
            }
        } catch (Exception e) {
            LOG.error("RecipeCleaner failed", e);
        }
    }

    @SubscribeEvent
    public void onTextureStitch(TextureStitchEvent.Pre event) {
        if (!textureAnimOff) return;
        try {
            java.lang.reflect.Field f = net.minecraft.client.renderer.texture.TextureMap.class
                    .getDeclaredField("animatedTextureMap");
            f.setAccessible(true);
            Object map = f.get(event.getMap());
            if (map instanceof Map) {
                ((Map) map).clear();
            }
        } catch (Exception ignored) {}
    }

    public static class SuniumGui extends GuiScreen {

        private int x, y, dragX, dragY;
        private boolean dragging;

        private final List<Btn> buttons = new ArrayList<>();
        private final List<Integer> headerOffsets = new ArrayList<>();
        private final List<String> headerNames = new ArrayList<>();

        private static final int W = 260, H = 200, TITLE_H = 18;
        private static final int HEADER_OFF = TITLE_H + 6;
        private static final int ROW = 14;

        @Override
        public void initGui() {
            x = width / 2 - W / 2;
            y = height / 2 - H / 2;
            dragging = false;
            layout();
        }

        private void layout() {
            Map<String, Boolean> state = new HashMap<>();
            for (Btn b : buttons) state.put(b.name, b.expanded);

            buttons.clear(); headerNames.clear(); headerOffsets.clear();
            int off = 0;

            headerNames.add("Performance"); headerOffsets.add(off); off += ROW;
            off = add(new Btn("AutoRenderDistance", () -> autoRenderDistance, v -> autoRenderDistance = v), off);
            off = add(new Btn("BackgroundFPS", () -> backgroundFpsEnabled, v -> backgroundFpsEnabled = v), off);
            off = add(new Btn("AutoParticles", () -> autoParticles, v -> autoParticles = v), off);
            off += 4;

            headerNames.add("World"); headerOffsets.add(off); off += ROW;
            off = add(new Btn("RecipeCleaner", () -> recipeCleaner, v -> recipeCleaner = v), off);
            off = add(new Btn("TextureAnimOff", () -> textureAnimOff, v -> textureAnimOff = v), off);
            off = add(new Btn("DropMerger", () -> dropMerger, v -> dropMerger = v), off);
        }

        private int add(Btn b, int off) {
            b.offset = off;
            buttons.add(b);
            return off + ROW;
        }

        @Override
        public void drawScreen(int mx, int my, float pt) {
            drawRect(0, 0, width, height, new Color(0, 0, 0, 140).getRGB());
            drawRect(x, y, x + W, y + H, 0xC0000000);
            drawRect(x, y, x + W, y + TITLE_H, 0xFF1E1E1E);
            drawString(fontRenderer, "Sunium", x + 6, y + 5, 0xFFFFFF);

            for (int i = 0; i < headerNames.size(); i++) {
                fontRenderer.drawString(headerNames.get(i), x + 8,
                        y + HEADER_OFF + headerOffsets.get(i), 0xAAAAAA);
            }
            for (Btn b : buttons) b.draw(x + 5, y + HEADER_OFF + b.offset, mx, my);

            super.drawScreen(mx, my, pt);
        }

        private boolean inside(int mx, int my) { return mx >= x && mx <= x + W && my >= y && my <= y + H; }

        @Override
        protected void mouseClicked(int mx, int my, int btn) throws java.io.IOException {
            if (!inside(mx, my)) { super.mouseClicked(mx, my, btn); return; }
            if (btn == 0 && my >= y && my <= y + TITLE_H) {
                dragging = true; dragX = mx - x; dragY = my - y; return;
            }
            for (Btn b : buttons) {
                b.click(mx, my, btn, x + 5, y + HEADER_OFF + b.offset);
            }
            super.mouseClicked(mx, my, btn);
        }

        @Override
        protected void mouseReleased(int mx, int my, int state) {
            dragging = false;
            super.mouseReleased(mx, my, state);
        }

        @Override
        protected void mouseClickMove(int mx, int my, int btn, long dt) {
            if (dragging) { x = mx - dragX; y = my - dragY; }
            super.mouseClickMove(mx, my, btn, dt);
        }

        @Override
        public boolean doesGuiPauseGame() { return false; }
    }

    public static class Btn {
        public interface BG { boolean get(); }
        public interface BS { void set(boolean v); }
        final String name; final BG get; final BS set;
        int offset; boolean expanded;
        public Btn(String n, BG g, BS s, Object... items) {
            name = n; get = g; set = s;
        }
        void draw(int ax, int ay, int mx, int my) {
            Minecraft mc = Minecraft.getMinecraft();
            boolean hover = mx >= ax && mx <= ax + 170 && my >= ay && my <= ay + 12;
            Gui.drawRect(ax, ay, ax + 170, ay + 12, hover ? 0xBB282828 : 0xAA181818);
            int c = (get != null && get.get()) ? 0xFF00FF00 : 0xFFFF5555;
            if (get == null) c = 0xFFFFFFFF;
            mc.fontRenderer.drawString(name, ax + 3, ay + 2, c);
        }
        void click(int mx, int my, int btn, int ax, int ay) {
            if (mx >= ax && mx <= ax + 170 && my >= ay && my <= ay + 12) {
                if (btn == 0 && get != null) set.set(!get.get());
            }
        }
    }
}
