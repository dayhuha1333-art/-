package com.sunset;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.RayTraceResult;
import net.minecraftforge.client.event.RenderBlockOverlayEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Mod(modid = "sunset", name = "Sunset", version = "1.0",
        acceptedMinecraftVersions = "[1.12.2]")
public class SunsetMod {

    public static final String MODID = "sunset";
    public static final String NAME = "Sunset";
    public static final String VERSION = "1.0";

    private static final Logger LOG = LogManager.getLogger("Sunset");

    public static KeyBinding guiKey;
    private static Configuration config;

    // ===================== МОДУЛИ =====================
    public static boolean killAuraEnabled = false;
    public static float killAuraFov = 30f;
    public static float killAuraMiss = 20f;

    public static boolean triggerBotEnabled = false;
    public static float triggerBotMiss = 20f;

    public static boolean antiKbEnabled = false;
    public static float antiKbPercent = 0f;

    public static boolean espEnabled = false;
    public static int espColor = 0x00FF00;
    public static boolean espInvisible = true;

    public static boolean disFireEnabled = false;

    private static long killAuraLast = 0;
    private static long triggerBotLast = 0;
    private static final Map<String, Boolean> expandedCache = new HashMap<>();

    private static Minecraft mc() { return Minecraft.getMinecraft(); }

    // ===================== ИНИЦИАЛИЗАЦИЯ =====================
    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        loadConfig();
        LOG.info("Sunset preInit done");
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        guiKey = new KeyBinding("Open Sunset GUI", Keyboard.KEY_RSHIFT, "Sunset");
        ClientRegistry.registerKeyBinding(guiKey);
        MinecraftForge.EVENT_BUS.register(this);
        LOG.info("Sunset initialized, keybind registered");
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

    @SubscribeEvent
    public void onRenderWorld(RenderWorldLastEvent event) {
        if (espEnabled) doEsp(event.getPartialTicks());
    }

    @SubscribeEvent
    public void onKnockback(LivingKnockBackEvent event) {
        if (!antiKbEnabled) return;
        Minecraft mc = mc();
        if (mc.player == null) return;
        if (!event.getEntity().world.isRemote) return;
        if (event.getEntityLiving() != mc.player) return;
        event.setStrength(event.getStrength() * (antiKbPercent / 100f));
    }

    @SubscribeEvent
    public void onRenderOverlay(RenderBlockOverlayEvent event) {
        if (!disFireEnabled) return;
        Minecraft mc = mc();
        if (event.getOverlayType() != RenderBlockOverlayEvent.OverlayType.FIRE) return;
        if (event.getPlayer() == null) return;
        if (event.getPlayer() != mc.player) return;
        event.setCanceled(true);
    }

    // ===================== KILLAURA =====================
    private static void doKillAura() {
        Minecraft mc = mc();
        if (mc.player == null || mc.world == null || mc.playerController == null) return;
        if (!mc.player.isEntityAlive()) return;
        if (triggerBotEnabled) return;

        List<EntityLivingBase> targets = new ArrayList<>();
        for (EntityLivingBase e : mc.world.getEntitiesWithinAABB(EntityLivingBase.class,
                mc.player.getEntityBoundingBox().grow(4.5))) {
            if (e == mc.player) continue;
            if (!e.isEntityAlive()) continue;
            if (!mc.player.canEntityBeSeen(e)) continue;
            if (!isInFov(mc, e, killAuraFov)) continue;
            targets.add(e);
        }

        if (targets.isEmpty()) return;
        targets.sort((a, b) -> Double.compare(mc.player.getDistance(a), mc.player.getDistance(b)));
        EntityLivingBase target = targets.get(0);

        long now = System.currentTimeMillis();
        int delay = randInt(10, 80);
        if (now - killAuraLast < delay) return;
        killAuraLast = now;

        if (chance(killAuraMiss)) return;

        mc.playerController.attackEntity(mc.player, target);
        mc.player.swingArm(EnumHand.MAIN_HAND);
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

    private static void doEsp(float partialTicks) {
        Minecraft mc = mc();
        if (mc.world == null || mc.player == null) return;

        float r = ((espColor >> 16) & 0xFF) / 255f;
        float g = ((espColor >> 8) & 0xFF) / 255f;
        float b = (espColor & 0xFF) / 255f;

        double vx = mc.getRenderManager().viewerPosX;
        double vy = mc.getRenderManager().viewerPosY;
        double vz = mc.getRenderManager().viewerPosZ;

        GlStateManager.pushMatrix();
        GlStateManager.pushAttrib();
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        GL11.glLineWidth(2f);
        GlStateManager.color(r, g, b, 1f);

        for (Entity e : mc.world.loadedEntityList) {
            if (e == mc.player) continue;
            if (!(e instanceof EntityPlayer)) continue;
            if (e.isInvisible() && !espInvisible) continue;

            double x = e.lastTickPosX + (e.posX - e.lastTickPosX) * partialTicks;
            double y = e.lastTickPosY + (e.posY - e.lastTickPosY) * partialTicks;
            double z = e.lastTickPosZ + (e.posZ - e.lastTickPosZ) * partialTicks;

            AxisAlignedBB bb = e.getEntityBoundingBox()
                    .offset(x - e.posX - vx, y - e.posY - vy, z - e.posZ - vz)
                    .grow(0.1);

            RenderGlobal.drawSelectionBoundingBox(bb, r, g, b, 1f);
        }

        GlStateManager.popAttrib();
        GlStateManager.popMatrix();
        GL11.glLineWidth(1f);
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

    // ===================== КОНФИГ =====================
    private static void loadConfig() {
        try {
            File file = new File("config/sunset.cfg");
            config = new Configuration(file);
            config.load();

            killAuraEnabled = config.get("killaura", "enabled", false).getBoolean();
            killAuraFov = (float) config.get("killaura", "fov", 30.0).getDouble();
            killAuraMiss = (float) config.get("killaura", "miss", 20.0).getDouble();

            triggerBotEnabled = config.get("triggerbot", "enabled", false).getBoolean();
            triggerBotMiss = (float) config.get("triggerbot", "miss", 20.0).getDouble();

            antiKbEnabled = config.get("antikb", "enabled", false).getBoolean();
            antiKbPercent = (float) config.get("antikb", "percent", 0.0).getDouble();

            espEnabled = config.get("esp", "enabled", false).getBoolean();
            espColor = config.get("esp", "color", 0x00FF00).getInt();
            espInvisible = config.get("esp", "invisible", true).getBoolean();

            disFireEnabled = config.get("disfire", "enabled", false).getBoolean();

            if (config.hasChanged()) config.save();
        } catch (Exception e) {
            LOG.error("Failed to load Sunset config", e);
        }
    }

    public static void saveConfig() {
        if (config == null) return;
        try {
            config.get("killaura", "enabled", false).set(killAuraEnabled);
            config.get("killaura", "fov", 30.0).set(killAuraFov);
            config.get("killaura", "miss", 20.0).set(killAuraMiss);

            config.get("triggerbot", "enabled", false).set(triggerBotEnabled);
            config.get("triggerbot", "miss", 20.0).set(triggerBotMiss);

            config.get("antikb", "enabled", false).set(antiKbEnabled);
            config.get("antikb", "percent", 0.0).set(antiKbPercent);

            config.get("esp", "enabled", false).set(espEnabled);
            config.get("esp", "color", 0x00FF00).set(espColor);
            config.get("esp", "invisible", true).set(espInvisible);

            config.get("disfire", "enabled", false).set(disFireEnabled);

            config.save();
        } catch (Exception e) {
            LOG.error("Failed to save Sunset config", e);
        }
    }

    // ===================== GUI =====================
    public static class SunsetGui extends GuiScreen {

        private static final int WINDOW_W = 250;
        private static final int WINDOW_H = 240;
        private static final int TITLE_H = 15;
        private static final int HEADER_OFFSET = TITLE_H + 5;
        private static final int ROW_H = 14;

        private int x, y, dragX, dragY;
        private boolean dragging = false;
        private int scroll = 0;

        private final List<ButtonEntry> buttons = new ArrayList<>();
        private final List<HeaderEntry> headers = new ArrayList<>();
        private int contentHeight = 0;

        @Override
        public void initGui() {
            x = width / 2 - WINDOW_W / 2;
            y = height / 2 - WINDOW_H / 2;
            scroll = 0;
            dragging = false;
            computeLayout();
        }

        private int layoutOff;

        private void addHeader(String name) {
            headers.add(new HeaderEntry(name, layoutOff));
            layoutOff += ROW_H;
        }

        private void addModule(ButtonEntry b) {
            b.offset = layoutOff;
            buttons.add(b);
            layoutOff += ROW_H;
        }

        private void addGap() { layoutOff += 4; }

        private void computeLayout() {
            buttons.clear();
            headers.clear();
            layoutOff = 0;

            addHeader("Combat");
            addModule(new ButtonEntry("KillAura",
                    () -> triggerBotEnabled = false,
                    () -> killAuraEnabled, v -> killAuraEnabled = v,
                    new SliderDef("FOV", () -> killAuraFov, v -> killAuraFov = v, 1f, 180f),
                    new SliderDef("Miss %", () -> killAuraMiss, v -> killAuraMiss = v, 0f, 100f)));

            addModule(new ButtonEntry("TriggerBot",
                    () -> killAuraEnabled = false,
                    () -> triggerBotEnabled, v -> triggerBotEnabled = v,
                    new SliderDef("Miss %", () -> triggerBotMiss, v -> triggerBotMiss = v, 0f, 100f)));

            addModule(new ButtonEntry("AntiKnockback", null,
                    () -> antiKbEnabled, v -> antiKbEnabled = v,
                    new SliderDef("Percent", () -> antiKbPercent, v -> antiKbPercent = v, 0f, 100f)));
            addGap();

            addHeader("Render");
            addModule(new ButtonEntry("ESP", null,
                    () -> espEnabled, v -> espEnabled = v,
                    new ColorEntry("Color", () -> espColor, v -> espColor = v),
                    new BooleanEntry("Invisible", () -> espInvisible, v -> espInvisible = v)));

            addModule(new ButtonEntry("DisFire", null,
                    () -> disFireEnabled, v -> disFireEnabled = v));
            addGap();

            contentHeight = layoutOff;
        }

        private int viewHeight() { return WINDOW_H - HEADER_OFFSET; }

        @Override
        public void drawScreen(int mouseX, int mouseY, float partialTicks) {
            drawRect(0, 0, width, height, new Color(0, 0, 0, 120).getRGB());
            drawRect(x, y, x + WINDOW_W, y + WINDOW_H, new Color(0, 0, 0, 200).getRGB());
            drawRect(x, y, x + WINDOW_W, y + TITLE_H, new Color(30, 30, 30, 255).getRGB());
            drawString(fontRenderer, "Sunset", x + 5, y + 4, 0xFFFFFF);

            for (HeaderEntry h : headers) {
                fontRenderer.drawString(h.name, x + 8, y + HEADER_OFFSET + h.offset + scroll, 0xAAAAAA);
            }

            for (ButtonEntry b : buttons) {
                b.draw(x + 5, y + HEADER_OFFSET + b.offset + scroll, mouseX, mouseY);
            }

            int viewH = viewHeight();
            if (contentHeight > viewH) {
                int barX = x + WINDOW_W - 5;
                int barY = y + HEADER_OFFSET;
                int barH = viewH;
                drawRect(barX, barY, barX + 3, barY + barH, new Color(60, 60, 60, 150).getRGB());

                float ratio = (float) viewH / contentHeight;
                int thumbH = Math.max(20, (int) (barH * ratio));
                int maxScroll = contentHeight - viewH;
                float scrollRatio = maxScroll > 0 ? (float) -scroll / maxScroll : 0;
                int thumbY = barY + (int) ((barH - thumbH) * scrollRatio);
                drawRect(barX, thumbY, barX + 3, thumbY + thumbH, new Color(0, 150, 255, 200).getRGB());
            }

            super.drawScreen(mouseX, mouseY, partialTicks);
        }

        private boolean isInsideWindow(int mx, int my) {
            return mx >= x && mx <= x + WINDOW_W && my >= y && my <= y + WINDOW_H;
        }

        @Override
        protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws java.io.IOException {
            if (!isInsideWindow(mouseX, mouseY)) {
                super.mouseClicked(mouseX, mouseY, mouseButton);
                return;
            }

            if (mouseButton == 0 && mouseY >= y && mouseY <= y + TITLE_H) {
                dragging = true;
                dragX = mouseX - x;
                dragY = mouseY - y;
                return;
            }

            for (ButtonEntry b : buttons) {
                b.mouseClicked(mouseX, mouseY, mouseButton,
                        x + 5, y + HEADER_OFFSET + b.offset + scroll);
            }

            super.mouseClicked(mouseX, mouseY, mouseButton);
        }

        @Override
        protected void mouseReleased(int mouseX, int mouseY, int state) {
            dragging = false;
            for (ButtonEntry b : buttons) b.mouseReleased(mouseX, mouseY, state);
            super.mouseReleased(mouseX, mouseY, state);
        }

        @Override
        protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
            if (dragging) {
                x = mouseX - dragX;
                y = mouseY - dragY;
            }
            super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
        }

        @Override
        public void handleMouseInput() throws java.io.IOException {
            super.handleMouseInput();
            int wheel = Mouse.getDWheel();
            if (wheel == 0) return;

            int mx = Mouse.getEventX() * width / mc().displayWidth;
            int my = height - Mouse.getEventY() * height / mc().displayHeight - 1;
            if (!isInsideWindow(mx, my)) return;

            scroll += wheel > 0 ? 10 : -10;
            if (scroll > 0) scroll = 0;
            int viewH = viewHeight();
            int minScroll = Math.min(0, -(contentHeight - viewH));
            if (scroll < minScroll) scroll = minScroll;
        }

        @Override
        public void onGuiClosed() {
            saveConfig();
            super.onGuiClosed();
        }

        @Override
        public boolean doesGuiPauseGame() { return false; }
    }

    private static class HeaderEntry {
        final String name;
        final int offset;
        HeaderEntry(String n, int o) { name = n; offset = o; }
    }

    private interface BoolGet { boolean get(); }
    private interface BoolSet { void set(boolean v); }
    private interface FloatGet { float get(); }
    private interface FloatSet { void set(float v); }
    private interface IntGet { int get(); }
    private interface IntSet { void set(int v); }

    private static class SliderDef {
        final String name;
        final FloatGet getter;
        final FloatSet setter;
        final float min, max;
        boolean dragging = false;
        SliderDef(String n, FloatGet g, FloatSet s, float mn, float mx) {
            name = n; getter = g; setter = s; min = mn; max = mx;
        }
    }

    private static class BooleanEntry {
        final String name;
        final BoolGet getter;
        final BoolSet setter;
        BooleanEntry(String n, BoolGet g, BoolSet s) {
            name = n; getter = g; setter = s;
        }
    }

    private static class ColorEntry {
        final String name;
        final IntGet getter;
        final IntSet setter;

        static final int[] PRESETS = {
                0x00FF00, 0xFF0000, 0x00AAFF, 0xFFFF00,
                0xFF00FF, 0x00FFFF, 0xFFFFFF, 0xFF8000
        };

        ColorEntry(String n, IntGet g, IntSet s) {
            name = n; getter = g; setter = s;
        }

        static int shiftHue(int rgb, float deltaHue) {
            int r = (rgb >> 16) & 0xFF;
            int g = (rgb >> 8) & 0xFF;
            int b = rgb & 0xFF;

            float[] hsv = java.awt.Color.RGBtoHSB(r, g, b, null);
            hsv[0] = (hsv[0] + deltaHue + 1f) % 1f;
            int out = java.awt.Color.HSBtoRGB(hsv[0], Math.max(0.85f, hsv[1]), Math.max(0.85f, hsv[2]));
            return out & 0xFFFFFF;
        }
    }

    private static class ButtonEntry {
        final String name;
        final Runnable exclusiveRunnable;
        int offset;
        final BoolGet enabledGet;
        final BoolSet enabledSet;
        final List<SliderDef> sliders = new ArrayList<>();
        final List<BooleanEntry> booleans = new ArrayList<>();
        final List<ColorEntry> colors = new ArrayList<>();
        boolean expanded = false;

        ButtonEntry(String name, Runnable exclusiveRunnable, BoolGet g, BoolSet s, Object... entries) {
            this.name = name;
            this.exclusiveRunnable = exclusiveRunnable;
            this.enabledGet = g;
            this.enabledSet = s;
            for (Object o : entries) {
                if (o instanceof SliderDef) sliders.add((SliderDef) o);
                else if (o instanceof BooleanEntry) booleans.add((BooleanEntry) o);
                else if (o instanceof ColorEntry) colors.add((ColorEntry) o);
            }
            Boolean cached = expandedCache.get(name);
            if (cached != null) expanded = cached;
        }

        void draw(int absX, int absY, int mouseX, int mouseY) {
            Minecraft mc = mc();
            boolean hover = mouseX >= absX && mouseX <= absX + 150 && mouseY >= absY && mouseY <= absY + 12;
            Gui.drawRect(absX, absY, absX + 150, absY + 12,
                    hover ? new Color(40, 40, 40, 200).getRGB() : new Color(20, 20, 20, 200).getRGB());
            int color = enabledGet.get() ? 0x00FF00 : 0xFF0000;
            mc.fontRenderer.drawString(name, absX + 3, absY + 2, color);

            if (expanded) {
                int sy = absY + 14;
                for (SliderDef s : sliders) { drawSlider(mc, s, absX + 5, sy, mouseX); sy += 12; }
                for (BooleanEntry b : booleans) { drawBoolean(mc, b, absX + 5, sy); sy += 12; }
                for (ColorEntry c : colors) { drawColor(mc, c, absX + 5, sy, mouseX, mouseY); sy += 12; }
            }
        }

        private void drawSlider(Minecraft mc, SliderDef s, int sx, int sy, int mouseX) {
            int w = 100, h = 10;
            float val = s.getter.get();
            float percent = (val - s.min) / (s.max - s.min);
            percent = Math.max(0, Math.min(1, percent));

            Gui.drawRect(sx, sy, sx + w, sy + h, new Color(40, 40, 40, 200).getRGB());
            Gui.drawRect(sx, sy, (int) (sx + w * percent), sy + h, new Color(0, 150, 255, 200).getRGB());
            mc.fontRenderer.drawString(s.name + ": " + String.format("%.1f", val), sx + 2, sy + 1, 0xFFFFFF);

            if (s.dragging) {
                float np = (float) (mouseX - sx) / (float) w;
                np = Math.max(0, Math.min(1, np));
                s.setter.set(s.min + np * (s.max - s.min));
            }
        }

        private void drawBoolean(Minecraft mc, BooleanEntry b, int sx, int sy) {
            Gui.drawRect(sx, sy, sx + 100, sy + 10, new Color(40, 40, 40, 200).getRGB());
            boolean on = b.getter.get();
            Gui.drawRect(sx + 2, sy + 2, sx + 10, sy + 8,
                    on ? new Color(0, 200, 0, 255).getRGB() : new Color(80, 80, 80, 255).getRGB());
            mc.fontRenderer.drawString(b.name + ": " + (on ? "ON" : "OFF"), sx + 15, sy + 1, 0xFFFFFF);
        }

        private void drawColor(Minecraft mc, ColorEntry c, int sx, int sy, int mouseX, int mouseY) {
            int val = c.getter.get();
            Gui.drawRect(sx, sy, sx + 115, sy + 10, new Color(40, 40, 40, 200).getRGB());

            for (int i = 0; i < ColorEntry.PRESETS.length; i++) {
                int px = sx + 2 + i * 10;
                Gui.drawRect(px, sy + 2, px + 8, sy + 8, ColorEntry.PRESETS[i] | 0xFF000000);
                boolean hover = mouseX >= px && mouseX <= px + 8
                        && mouseY >= sy + 2 && mouseY <= sy + 8;
                if (hover) Gui.drawRect(px, sy + 8, px + 8, sy + 10, 0xFFFFFFFF);
            }

            Gui.drawRect(sx + 84, sy + 2, sx + 96, sy + 8, val | 0xFF000000);
            mc.fontRenderer.drawString("+/−", sx + 98, sy + 1, 0xAAAAAA);
        }

        void mouseClicked(int mouseX, int mouseY, int button, int absX, int absY) {
            if (mouseX >= absX && mouseX <= absX + 150 && mouseY >= absY && mouseY <= absY + 12) {
                if (button == 0) {
                    boolean newState = !enabledGet.get();
                    enabledSet.set(newState);
                    if (newState && exclusiveRunnable != null) exclusiveRunnable.run();
                } else if (button == 1) {
                    expanded = !expanded;
                    expandedCache.put(name, expanded);
                }
                return;
            }

            if (expanded) {
                int sy = absY + 14;

                for (SliderDef s : sliders) {
                    if (button == 0 && mouseX >= absX + 5 && mouseX <= absX + 105
                            && mouseY >= sy && mouseY <= sy + 10) {
                        s.dragging = true;
                    }
                    sy += 12;
                }

                for (BooleanEntry b : booleans) {
                    if (button == 0 && mouseX >= absX + 5 && mouseX <= absX + 105
                            && mouseY >= sy && mouseY <= sy + 10) {
                        b.setter.set(!b.getter.get());
                    }
                    sy += 12;
                }

                for (ColorEntry c : colors) {
                    if (mouseY >= sy && mouseY <= sy + 10) {
                        for (int i = 0; i < ColorEntry.PRESETS.length; i++) {
                            int px = absX + 5 + 2 + i * 10;
                            if (button == 0 && mouseX >= px && mouseX <= px + 8
                                    && mouseY >= sy + 2 && mouseY <= sy + 8) {
                                c.setter.set(ColorEntry.PRESETS[i]);
                                return;
                            }
                        }
                        if (mouseY >= sy + 2 && mouseY <= sy + 8
                                && mouseX >= absX + 5 + 98 && mouseX <= absX + 5 + 113) {
                            if (button == 0) c.setter.set(ColorEntry.shiftHue(c.getter.get(), 1f / 24f));
                            else if (button == 1) c.setter.set(ColorEntry.shiftHue(c.getter.get(), -1f / 24f));
                            return;
                        }
                    }
                    sy += 12;
                }
            }
        }

        void mouseReleased(int mouseX, int mouseY, int button) {
            for (SliderDef s : sliders) s.dragging = false;
        }
    }
}
