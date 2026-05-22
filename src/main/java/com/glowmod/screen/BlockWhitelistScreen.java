package com.glowmod.screen;

import com.glowmod.GlowConfig;
import com.glowmod.GlowModClient;
import com.glowmod.render.BlockOutlineRenderer;
import com.glowmod.render.GuiRenderer;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.block.Block;
import org.lwjgl.glfw.GLFW;

import java.util.*;
import java.util.stream.StreamSupport;

public class BlockWhitelistScreen extends Screen {

    // ── Fixed layout constants ─────────────────────────────────────────────────
    private static final int MAX_PW    = 640;
    private static final int MAX_PH    = 420;
    private static final int HEADER_H  = 42;
    private static final int SIDEBAR_W = 140;
    private static final int PANEL_GAP = 5;
    private static final int PAD       = 16;
    private static final int RADIUS    = 8;
    private static final int ROW_H     = 18;
    private static final int SWATCH    = 13;
    private static final int PILL_W    = 40;
    private static final int PILL_H    = 18;
    private static final int SEC_GAP   = 12;

    // ── Base colors (never change) ─────────────────────────────────────────────
    private static final int C_DIM     = 0xBB000008;
    private static final int C_BG      = 0xFF0B0D17;
    private static final int C_HEADER  = 0xFF0D0F1C;
    private static final int C_SIDEBAR = 0xFF07080F;
    private static final int C_SURFACE = 0xFF111421;
    private static final int C_BORDER  = 0xFF1C2039;
    private static final int C_TEXT    = 0xFFE2E8F0;
    private static final int C_MUTED   = 0xFF63748B;
    private static final int C_DIM_TXT = 0xFF2D3452;
    private static final int C_GREEN   = 0xFF22C55E;
    private static final int C_RED     = 0xFFEF4444;
    private static final int C_HOV     = 0x0AFFFFFF;

    // ── Theme presets (0xRRGGBB) ──────────────────────────────────────────────
    private static final int[]    THEMES = {
        0x6366F1, 0x06B6D4, 0xA855F7, 0xEC4899,
        0x14B8A6, 0xF59E0B, 0x22C55E, 0xEF4444
    };
    private static final String[] THEME_NAMES = {
        "Indigo", "Cyan", "Purple", "Pink",
        "Teal",   "Amber","Green",  "Red"
    };

    // ── Dynamic accent helpers ─────────────────────────────────────────────────
    private int cA()   { return 0xFF000000 | GlowConfig.guiAccentColor; }
    private int cAL()  {
        int c = GlowConfig.guiAccentColor;
        return 0xFF000000
            | (Math.min(255, ((c >> 16) & 0xFF) + 28) << 16)
            | (Math.min(255, ((c >>  8) & 0xFF) + 28) <<  8)
            |  Math.min(255, (c & 0xFF) + 28);
    }
    private int cABG() { return (0x1A << 24) | (GlowConfig.guiAccentColor & 0xFFFFFF); }

    // Multiply a color's alpha by guiOpacity so panels become see-through.
    private int tinted(int argb) {
        int a = Math.round(((argb >> 24) & 0xFF) * GlowConfig.guiOpacity);
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    // ── Color picker ──────────────────────────────────────────────────────────
    private static final int PK_SIZE  = 120;
    private static final int PK_HUE_H = 12;
    private static final int PK_PAD   = 12;
    private static final int PK_W     = PK_SIZE + PK_PAD * 2;
    private static final int PK_H     = PK_PAD + PK_SIZE + PK_PAD + PK_HUE_H + PK_PAD + 14 + PK_PAD;

    // ── Pages ─────────────────────────────────────────────────────────────────
    private enum Page { GLOW, OUTLINES, PERF, SETTINGS }
    private record NavItem(Page page, String icon, String label) {}
    private static final NavItem[] NAV = {
        new NavItem(Page.GLOW,     "★", "Glow"),
        new NavItem(Page.OUTLINES, "▣", "Outlines"),
        new NavItem(Page.PERF,     "⚡", "Performance"),
        new NavItem(Page.SETTINGS, "✶", "Settings"),
    };
    private Page activePage = Page.GLOW;

    // ── Whitelist / search ────────────────────────────────────────────────────
    private EditBox searchBox;
    private List<Block>         blockResults   = new ArrayList<>();
    private List<EntityType<?>> mobResults     = new ArrayList<>();
    private List<String>        whitelistCache = new ArrayList<>();
    private final Map<String, String>  nameCache  = new LinkedHashMap<>();
    private final Map<String, Integer> colorCache = new LinkedHashMap<>();
    private int selectedResult = 0;
    private int listScroll     = 0;

    // ── Toggle animations ─────────────────────────────────────────────────────
    private float animPlayer  = 0f;
    private float animMob     = 0f;
    private float animOutline = 0f;

    // ── Toggle hit areas (set during render) ──────────────────────────────────
    private int playerToggleY = -1, mobToggleY = -1, outlineToggleY = -1;
    private int playerSwatchX, playerSwatchY, mobSwatchX, mobSwatchY;

    // ── Keybind ───────────────────────────────────────────────────────────────
    private boolean capturingKey = false;

    // ── Sliders ───────────────────────────────────────────────────────────────
    private record SliderDef(String id, int x, int trackY, int w, float min, float max) {
        float valueAt(int mx) {
            return min + Math.max(0f, Math.min(1f, (float)(mx - x) / w)) * (max - min);
        }
    }
    private final List<SliderDef> frameSliders = new ArrayList<>();
    private String activeSlider = null;

    // ── Theme swatches (set during render) ────────────────────────────────────
    private record SwatchDef(int color, int x, int y, int w, int h) {}
    private final List<SwatchDef> frameSwatches = new ArrayList<>();

    // ── Color picker ──────────────────────────────────────────────────────────
    private String  pickerTarget = null;
    private float   pickerH, pickerS = 1f, pickerV = 1f;
    private int     pkX, pkY;
    private boolean dragSV, dragHue;

    // ── Computed layout (recalculated in init) ────────────────────────────────
    private int pw, ph;
    private int px, py;
    private int sideX, sideY, sideH;
    private int contX, contY, contW, contH;

    private final Screen parent;

    public BlockWhitelistScreen(Screen parent) {
        super(Component.empty());
        this.parent = parent;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Init
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    protected void init() {
        // Fit within screen with 20px margin on each side, capped at MAX_PW/PH.
        pw = Math.max(380, Math.min(MAX_PW, width  - 40));
        ph = Math.max(280, Math.min(MAX_PH, height - 40));
        px = (width  - pw) / 2;
        py = (height - ph) / 2;

        // Three separate panels: header bar, sidebar, content.
        int bodyY = py + HEADER_H + PANEL_GAP;
        int bodyH = ph - HEADER_H - PANEL_GAP;

        sideX = px;
        sideY = bodyY;
        sideH = bodyH;

        contX = px + SIDEBAR_W + PANEL_GAP;
        contY = bodyY;
        contW = pw - SIDEBAR_W - PANEL_GAP;
        contH = bodyH;

        animPlayer  = GlowConfig.playerGlowEnabled  ? 1f : 0f;
        animMob     = GlowConfig.mobGlowEnabled      ? 1f : 0f;
        animOutline = GlowConfig.blockOutlineEnabled ? 1f : 0f;

        rebuildCache();
        rebuildSearchBox();
    }

    private void rebuildSearchBox() {
        if (searchBox != null) removeWidget(searchBox);
        searchBox = new EditBox(font, contX + PAD + 6, contY + PAD + 6,
                contW - PAD * 2 - 12, 10, Component.empty());
        searchBox.setMaxLength(64);
        searchBox.setBordered(false);
        searchBox.setResponder(q -> { selectedResult = 0; refreshResults(q); });
        addWidget(searchBox);
        setInitialFocus(searchBox);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Render
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float delta) {
        g.fill(0, 0, width, height, C_DIM);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float delta) {
        float step = delta * 0.28f;
        animPlayer  += ((GlowConfig.playerGlowEnabled  ? 1f : 0f) - animPlayer)  * step;
        animMob     += ((GlowConfig.mobGlowEnabled      ? 1f : 0f) - animMob)     * step;
        animOutline += ((GlowConfig.blockOutlineEnabled ? 1f : 0f) - animOutline) * step;
        frameSliders.clear();
        frameSwatches.clear();
        super.render(g, mx, my, delta);

        // Shadows (draw all before panels so they don't overlap panel fill)
        GuiRenderer.shadow(g, px,    py,    pw,      HEADER_H, RADIUS, 8);
        GuiRenderer.shadow(g, sideX, sideY, SIDEBAR_W, sideH,  RADIUS, 8);
        GuiRenderer.shadow(g, contX, contY, contW,   contH,    RADIUS, 8);

        // Panels
        GuiRenderer.roundedRect(g, px,    py,    pw,        HEADER_H, RADIUS, tinted(C_HEADER));
        GuiRenderer.roundedRect(g, sideX, sideY, SIDEBAR_W, sideH,    RADIUS, tinted(C_SIDEBAR));
        GuiRenderer.roundedRect(g, contX, contY, contW,     contH,    RADIUS, tinted(C_BG));

        drawHeader(g, mx, my);
        drawSidebar(g, mx, my);
        drawContent(g, mx, my, delta);

        if (pickerTarget != null) renderPicker(g, mx, my);
    }

    // ── Header ────────────────────────────────────────────────────────────────

    private void drawHeader(GuiGraphics g, int mx, int my) {
        // Thin gradient accent line at top of header panel
        g.fillGradient(px + RADIUS, py, px + pw / 2, py + 2, cA(), cAL());
        g.fillGradient(px + pw / 2, py, px + pw - RADIUS, py + 2, cAL(), cA() & 0x00FFFFFF);

        // Bottom separator
        g.fill(px + RADIUS, py + HEADER_H - 1, px + pw - RADIUS, py + HEADER_H, C_BORDER);

        // Title + badge
        g.drawString(font, "GlowMod", px + PAD, py + (HEADER_H - 8) / 2, C_TEXT, false);
        String badge = "v1.0";
        int bx = px + PAD + font.width("GlowMod") + 8;
        int by = py + (HEADER_H - 12) / 2;
        GuiRenderer.roundedRect(g, bx, by, font.width(badge) + 10, 12, 4, tinted(C_SURFACE));
        g.drawString(font, badge, bx + 5, by + 2, C_MUTED, false);

        // Active page label (right-aligned)
        String lbl = switch (activePage) {
            case GLOW     -> "★  Glow";
            case OUTLINES -> "▣  Outlines";
            case PERF     -> "⚡  Performance";
            case SETTINGS -> "✶  Settings";
        };
        g.drawString(font, lbl, px + pw - PAD - font.width(lbl), py + (HEADER_H - 8) / 2, C_MUTED, false);
    }

    // ── Sidebar ───────────────────────────────────────────────────────────────

    private static final int NAV_H   = 38;
    private static final int NAV_GAP = 3;

    private void drawSidebar(GuiGraphics g, int mx, int my) {
        int ny = sideY + 8;
        for (NavItem nav : NAV) {
            boolean active = nav.page() == activePage;
            boolean hov    = !active && mx >= sideX && mx < sideX + SIDEBAR_W
                          && my >= ny && my < ny + NAV_H;

            if (active) {
                GuiRenderer.roundedRect(g, sideX + 2, ny, SIDEBAR_W - 4, NAV_H, 6, tinted(cABG()));
                g.fill(sideX + 2, ny + 5, sideX + 5, ny + NAV_H - 5, cA());
            } else if (hov) {
                GuiRenderer.roundedRect(g, sideX + 2, ny, SIDEBAR_W - 4, NAV_H, 6, C_HOV);
            }

            int ty = ny + (NAV_H - 8) / 2;
            g.drawString(font, nav.icon(),  sideX + 14, ty, active ? cA()   : (hov ? C_TEXT : C_MUTED), false);
            g.drawString(font, nav.label(), sideX + 28, ty, active ? C_TEXT : (hov ? C_TEXT : C_MUTED), false);
            ny += NAV_H + NAV_GAP;
        }

        // Thin separator below nav items
        int sepY = sideY + 8 + NAV.length * (NAV_H + NAV_GAP) + 4;
        g.fill(sideX + PAD, sepY, sideX + SIDEBAR_W - PAD, sepY + 1, C_BORDER);
    }

    private int navTopY(int i) { return sideY + 8 + i * (NAV_H + NAV_GAP); }

    // ── Content dispatch ──────────────────────────────────────────────────────

    private void drawContent(GuiGraphics g, int mx, int my, float delta) {
        switch (activePage) {
            case GLOW     -> drawGlowPage(g, mx, my, delta);
            case OUTLINES -> drawOutlinesPage(g, mx, my, delta);
            case PERF     -> drawPerfPage(g, mx, my);
            case SETTINGS -> drawSettingsPage(g, mx, my);
        }
    }

    // ── Glow page ─────────────────────────────────────────────────────────────

    private void drawGlowPage(GuiGraphics g, int mx, int my, float delta) {
        int x = contX + PAD, w = contW - PAD * 2, y = contY + PAD;

        sectionLabel(g, x, y, "PLAYER GLOW"); y += 14;
        playerToggleY = y;
        y = featureRow(g, mx, my, x, y, w, "Player Glow", animPlayer,
                GlowConfig.playerGlowEnabled, GlowConfig.playerGlowColor, "playerGlow");
        playerSwatchX = x + w - PILL_W - 12 - SWATCH - 4;
        playerSwatchY = playerToggleY + (26 - SWATCH) / 2;

        y += SEC_GAP; divider(g, x, y, w); y += SEC_GAP;

        sectionLabel(g, x, y, "MOB GLOW"); y += 14;
        mobToggleY = y;
        y = featureRow(g, mx, my, x, y, w, "Mob Glow", animMob,
                GlowConfig.mobGlowEnabled, GlowConfig.mobGlowColor, "mobGlow");
        mobSwatchX = x + w - PILL_W - 12 - SWATCH - 4;
        mobSwatchY = mobToggleY + (26 - SWATCH) / 2;

        y += SEC_GAP; divider(g, x, y, w); y += SEC_GAP;
        sectionLabel(g, x, y, "MOB WHITELIST"); y += 14;
        drawWhitelistArea(g, mx, my, delta, x, y, w, contY + contH - 4, true);
    }

    // ── Outlines page ─────────────────────────────────────────────────────────

    private void drawOutlinesPage(GuiGraphics g, int mx, int my, float delta) {
        int x = contX + PAD, w = contW - PAD * 2, y = contY + PAD;

        sectionLabel(g, x, y, "BLOCK OUTLINES"); y += 14;
        outlineToggleY = y;
        y = featureRow(g, mx, my, x, y, w, "Block Outlines", animOutline,
                GlowConfig.blockOutlineEnabled, -1, null);

        y += 10;
        slider(g, mx, my, x, y, w, "outlineOpacity","Opacity",
               GlowConfig.outlineOpacity * 100f, 0f, 100f, "%.0f%%"); y += 36;

        divider(g, x, y, w); y += SEC_GAP;
        sectionLabel(g, x, y, "BLOCK WHITELIST"); y += 14;
        drawWhitelistArea(g, mx, my, delta, x, y, w, contY + contH - 4, false);
    }

    // ── Performance page ──────────────────────────────────────────────────────

    private void drawPerfPage(GuiGraphics g, int mx, int my) {
        int x = contX + PAD, w = contW - PAD * 2, y = contY + PAD;

        sectionLabel(g, x, y, "ENTITY GLOW"); y += 16;
        slider(g, mx, my, x, y, w, "glowRange",   "Glow Range",
               GlowConfig.glowRange, 16f, 256f, "%.0f blocks");     y += 38;
        slider(g, mx, my, x, y, w, "maxEntities", "Max Glowing Entities",
               GlowConfig.maxGlowEntities, 1f, 64f, "%.0f");        y += 38;

        divider(g, x, y, w); y += SEC_GAP;
        sectionLabel(g, x, y, "BLOCK OUTLINES"); y += 16;
        slider(g, mx, my, x, y, w, "scanRate",     "Chunk Scan Rate",
               GlowConfig.chunkScanRate, 1f, 10f, "%.0f / tick");   y += 38;
        slider(g, mx, my, x, y, w, "refreshInterval", "Outline Refresh Interval",
               GlowConfig.chunkRefreshInterval / 20f, 1f, 30f, "%.0f s"); y += 38;

        divider(g, x, y, w); y += 10;
        g.drawString(font, "Lower values reduce CPU usage with many entities or blocks.", x, y, C_MUTED, false);
    }

    // ── Settings page ─────────────────────────────────────────────────────────

    private void drawSettingsPage(GuiGraphics g, int mx, int my) {
        int x = contX + PAD, w = contW - PAD * 2, y = contY + PAD;

        sectionLabel(g, x, y, "APPEARANCE"); y += 16;
        slider(g, mx, my, x, y, w, "guiOpacity", "Panel Opacity",
               GlowConfig.guiOpacity * 100f, 20f, 100f, "%.0f%%");  y += 38;

        divider(g, x, y, w); y += SEC_GAP;
        sectionLabel(g, x, y, "ACCENT COLOR"); y += 14;

        // Theme swatches — 4 per row
        int swW = (w - 3 * 6) / 4;
        int swH = 26;
        for (int i = 0; i < THEMES.length; i++) {
            int col = i % 4, row = i / 4;
            int sx = x + col * (swW + 6);
            int sy = y + row * (swH + 6);
            boolean selected = GlowConfig.guiAccentColor == THEMES[i];
            boolean hov      = mx >= sx && mx < sx + swW && my >= sy && my < sy + swH;

            int fill = 0xFF000000 | THEMES[i];
            GuiRenderer.roundedRect(g, sx, sy, swW, swH, 5, fill);
            if (selected) {
                g.fill(sx - 2, sy - 2, sx + swW + 2, sy + swH + 2, 0x00000000); // transparent frame
                g.fill(sx - 2, sy - 2, sx + swW + 2, sy - 1,       0xFFFFFFFF);
                g.fill(sx - 2, sy + swH + 1, sx + swW + 2, sy + swH + 2, 0xFFFFFFFF);
                g.fill(sx - 2, sy - 2, sx - 1,             sy + swH + 2, 0xFFFFFFFF);
                g.fill(sx + swW + 1, sy - 2, sx + swW + 2, sy + swH + 2, 0xFFFFFFFF);
            } else if (hov) {
                g.fill(sx, sy, sx + swW, sy + swH, 0x44FFFFFF);
            }
            // Name label inside swatch
            int nameColor = selected ? 0xFFFFFFFF : 0xCCFFFFFF;
            int nameX = sx + (swW - font.width(THEME_NAMES[i])) / 2;
            g.drawString(font, THEME_NAMES[i], nameX, sy + (swH - 8) / 2, nameColor, false);

            frameSwatches.add(new SwatchDef(THEMES[i], sx, sy, swW, swH));
        }
        y += ((THEMES.length + 3) / 4) * (swH + 6) + 4;

        divider(g, x, y, w); y += SEC_GAP;
        sectionLabel(g, x, y, "KEYBIND"); y += 14;

        String kLabel = capturingKey
                ? "Press any key..."
                : KeyBindingHelper.getBoundKeyOf(GlowModClient.openGuiKey).getDisplayName().getString();
        int btnW = Math.max(110, font.width(kLabel) + 24);
        int btnH = 24;
        boolean kHov = mx >= x && mx < x + btnW && my >= y && my < y + btnH;

        g.fill(x - 1, y - 1, x + btnW + 1, y + btnH + 1, C_BORDER);
        GuiRenderer.roundedRect(g, x, y, btnW, btnH, 5,
                capturingKey ? tinted(0xFF181A2E) : tinted(kHov ? C_SURFACE : C_HEADER));
        if (capturingKey) g.fill(x, y, x + btnW, y + btnH, 0x22FFFFFF);
        g.drawString(font, kLabel, x + (btnW - font.width(kLabel)) / 2, y + (btnH - 8) / 2,
                capturingKey ? cA() : C_TEXT, false);
        y += btnH + 8;
        g.drawString(font, "Click then press a key to rebind.", x, y, C_MUTED, false);
    }

    // ── Shared UI helpers ─────────────────────────────────────────────────────

    private void sectionLabel(GuiGraphics g, int x, int y, String text) {
        g.drawString(font, text, x, y, C_MUTED, false);
    }

    private void divider(GuiGraphics g, int x, int y, int w) {
        g.fill(x, y, x + w, y + 1, C_BORDER);
    }

    private int featureRow(GuiGraphics g, int mx, int my,
                            int x, int y, int w,
                            String label, float anim, boolean on, int color, String pickerId) {
        int rowH = 26;
        g.drawString(font, label, x, y + (rowH - 8) / 2, on ? C_TEXT : C_MUTED, false);

        if (color >= 0 && pickerId != null) {
            int sx = x + w - PILL_W - 12 - SWATCH;
            int sy = y + (rowH - SWATCH) / 2;
            boolean swHov = mx >= sx - 2 && mx < sx + SWATCH + 2 && my >= sy - 2 && my < sy + SWATCH + 2;
            g.fill(sx - 1, sy - 1, sx + SWATCH + 1, sy + SWATCH + 1, C_BORDER);
            g.fill(sx, sy, sx + SWATCH, sy + SWATCH, color | 0xFF000000);
            if (swHov || pickerId.equals(pickerTarget))
                g.fill(sx - 2, sy - 2, sx + SWATCH + 2, sy + SWATCH + 2, 0x44FFFFFF);
        }

        int pillX = x + w - PILL_W, pillY = y + (rowH - PILL_H) / 2;
        GuiRenderer.roundedRect(g, pillX, pillY, PILL_W, PILL_H, PILL_H / 2f, lerpRgb(C_SURFACE, C_GREEN, anim));
        int knobSz = PILL_H - 4, knobX = pillX + 2 + (int)((PILL_W - PILL_H) * anim);
        GuiRenderer.roundedRect(g, knobX, pillY + 2, knobSz, knobSz, knobSz / 2f, 0xFFFFFFFF);

        return y + rowH;
    }

    private void slider(GuiGraphics g, int mx, int my,
                        int x, int y, int w,
                        String id, String label, float val, float min, float max, String fmt) {
        String vs = String.format(fmt, val);
        g.drawString(font, label, x, y, C_TEXT, false);
        g.drawString(font, vs, x + w - font.width(vs), y, C_MUTED, false);

        int th = 3, ty = y + 16, thumbR = 5;
        float frac  = (max > min) ? (val - min) / (max - min) : 0f;
        int   fillW = (int)(frac * w);
        int   thumbCX = x + Math.max(thumbR, Math.min(w - thumbR, fillW));

        GuiRenderer.roundedRect(g, x, ty, w, th, 2f, C_BORDER);
        if (fillW > 2) GuiRenderer.roundedRect(g, x, ty, fillW, th, 2f, cA());

        int midY = ty + th / 2;
        boolean tHov = id.equals(activeSlider) ||
                (mx >= thumbCX - thumbR - 4 && mx <= thumbCX + thumbR + 4 &&
                 my >= midY - thumbR - 4    && my <= midY + thumbR + 4);
        GuiRenderer.roundedRect(g, thumbCX - thumbR, midY - thumbR,
                thumbR * 2, thumbR * 2, thumbR, tHov ? 0xFFFFFFFF : 0xFFCBD5F5);

        frameSliders.add(new SliderDef(id, x, ty, w, min, max));
    }

    // ── Whitelist area ────────────────────────────────────────────────────────

    private void drawWhitelistArea(GuiGraphics g, int mx, int my, float delta,
                                    int x, int y, int w, int maxY, boolean mobs) {
        int sbH = 20;
        GuiRenderer.roundedRect(g, x, y, w, sbH, 4, tinted(C_SURFACE));
        g.fill(x, y + sbH - 1, x + w, y + sbH, searchBox.isFocused() ? cA() : C_BORDER);
        if (searchBox.getValue().isEmpty())
            g.drawString(font, mobs ? "Search mobs..." : "Search blocks...", x + 7, y + 6, C_DIM_TXT, false);
        searchBox.setX(x + 7); searchBox.setY(y + 5); searchBox.setWidth(w - 14);
        searchBox.render(g, mx, my, delta);
        y += sbH + 4;

        int halfW = (w - 4) / 2;
        int col2X = x + halfW + 4;
        g.fill(x + halfW + 1, y, x + halfW + 2, maxY, C_BORDER);

        // Left: results
        List<?> results = mobs ? mobResults : blockResults;
        int ly = y;
        for (int i = 0; i < results.size() && ly + ROW_H <= maxY; i++, ly += ROW_H) {
            String name = mobs
                    ? ((EntityType<?>)results.get(i)).getDescription().getString()
                    : ((Block)results.get(i)).getName().getString();
            boolean sel = i == selectedResult;
            boolean hov = mx >= x && mx < x + halfW && my >= ly && my < ly + ROW_H;
            if (sel) {
                GuiRenderer.roundedRect(g, x, ly, halfW, ROW_H - 2, 3, tinted(C_SURFACE));
                g.fill(x, ly + 2, x + 3, ly + ROW_H - 4, cA());
            } else if (hov) {
                g.fill(x, ly, x + halfW, ly + ROW_H - 1, C_HOV);
            }
            String disp = font.width(name) > halfW - 14
                    ? font.plainSubstrByWidth(name, halfW - 20) + "…" : name;
            g.drawString(font, disp, x + 8, ly + (ROW_H - 8) / 2, sel || hov ? C_TEXT : C_MUTED, false);
        }
        if (results.isEmpty() && !searchBox.getValue().isEmpty())
            g.drawString(font, "no results", x + 8, y + 4, C_DIM_TXT, false);

        // Right: whitelist
        List<String> wl = whitelistCache;
        int vis = Math.max(1, (maxY - y) / ROW_H);
        listScroll = Math.min(listScroll, Math.max(0, wl.size() - vis));

        if (wl.isEmpty()) {
            g.drawString(font, "nothing added yet", col2X + 6, y + 4, C_DIM_TXT, false);
        } else {
            int wy = y;
            for (int i = listScroll; i < wl.size() && wy + ROW_H <= maxY; i++, wy += ROW_H) {
                String id      = wl.get(i);
                String name    = nameCache.getOrDefault(id, id);
                boolean enabled = !(mobs ? GlowConfig.disabledMobs : GlowConfig.disabledBlocks).contains(id);
                boolean hov    = mx >= col2X && mx < col2X + halfW && my >= wy && my < wy + ROW_H;
                if (hov) g.fill(col2X, wy, col2X + halfW, wy + ROW_H - 1, C_HOV);

                // Toggle dot (8×8)
                int dotSz = 8;
                int dotX  = col2X + 4;
                int dotY  = wy + (ROW_H - dotSz) / 2;
                g.fill(dotX - 1, dotY - 1, dotX + dotSz + 1, dotY + dotSz + 1, C_BORDER);
                g.fill(dotX, dotY, dotX + dotSz, dotY + dotSz, enabled ? cA() : C_SURFACE);

                int rmX  = col2X + halfW - 12;
                boolean rmH = mx >= rmX - 3 && mx < rmX + 10 && my >= wy && my < wy + ROW_H;

                // Color swatch (blocks only), shifted right of toggle
                int textX = dotX + dotSz + 4;
                if (!mobs) {
                    int c  = colorCache.getOrDefault(id, 0x00FF00) | 0xFF000000;
                    int sx = textX, sy = wy + (ROW_H - SWATCH) / 2;
                    boolean swH = mx >= sx - 2 && mx < sx + SWATCH + 2 && my >= sy - 2 && my < sy + SWATCH + 2;
                    g.fill(sx - 1, sy - 1, sx + SWATCH + 1, sy + SWATCH + 1, C_BORDER);
                    g.fill(sx, sy, sx + SWATCH, sy + SWATCH, enabled ? c : 0xFF3D3D3D);
                    if (swH || id.equals(pickerTarget)) g.fill(sx - 2, sy - 2, sx + SWATCH + 2, sy + SWATCH + 2, 0x44FFFFFF);
                    textX = sx + SWATCH + 4;
                }

                int nameCol  = enabled ? (hov ? C_TEXT : C_MUTED) : C_DIM_TXT;
                int textMaxW = rmX - textX - 4;
                String disp  = font.width(name) > textMaxW ? font.plainSubstrByWidth(name, textMaxW - 6) + "…" : name;
                g.drawString(font, disp, textX, wy + (ROW_H - 8) / 2, nameCol, false);
                g.drawString(font, "✕", rmX, wy + (ROW_H - 8) / 2, rmH ? C_RED : C_DIM_TXT, false);
            }
        }
    }

    // ── Color picker ──────────────────────────────────────────────────────────

    private void renderPicker(GuiGraphics g, int mx, int my) {
        GuiRenderer.shadow(g, pkX, pkY, PK_W, PK_H, 6, 5);
        g.fill(pkX - 1, pkY - 1, pkX + PK_W + 1, pkY + PK_H + 1, C_BORDER);
        GuiRenderer.roundedRect(g, pkX, pkY, PK_W, PK_H, 6, tinted(C_SURFACE));

        int svX = pkX + PK_PAD, svY = pkY + PK_PAD;
        float[] hr = hsvToRgb(pickerH, 1f, 1f);
        for (int col = 0; col < PK_SIZE; col++) {
            float s   = (float)col / (PK_SIZE - 1);
            int   top = 0xFF000000
                    | ((int)((1f + (hr[0]-1f)*s)*255) << 16)
                    | ((int)((1f + (hr[1]-1f)*s)*255) <<  8)
                    | (int)((1f + (hr[2]-1f)*s)*255);
            g.fillGradient(svX + col, svY, svX + col + 1, svY + PK_SIZE, top, 0xFF000000);
        }
        int cx = svX + Math.round(pickerS * (PK_SIZE - 1));
        int cy = svY + Math.round((1f - pickerV) * (PK_SIZE - 1));
        GuiRenderer.roundedRect(g, cx - 4, cy - 4, 8, 8, 4, 0xAAFFFFFF);
        g.fill(cx - 3, cy - 1, cx + 3, cy + 1, 0xFF000000);
        g.fill(cx - 1, cy - 3, cx + 1, cy + 3, 0xFF000000);

        int hueY = svY + PK_SIZE + PK_PAD;
        for (int col = 0; col < PK_SIZE; col++) {
            float[] c = hsvToRgb((float)col / PK_SIZE, 1f, 1f);
            g.fill(svX + col, hueY, svX + col + 1, hueY + PK_HUE_H,
                    0xFF000000 | ((int)(c[0]*255) << 16) | ((int)(c[1]*255) << 8) | (int)(c[2]*255));
        }
        int hx = svX + Math.round(pickerH * (PK_SIZE - 1));
        g.fill(hx - 1, hueY - 2, hx + 1, hueY + PK_HUE_H + 2, 0xFFFFFFFF);

        int pY = hueY + PK_HUE_H + PK_PAD;
        int cur = hsvToInt(pickerH, pickerS, pickerV);
        g.fill(svX - 1, pY - 1, svX + 15, pY + 13, C_BORDER);
        g.fill(svX, pY, svX + 14, pY + 12, cur);
        g.drawString(font, String.format("#%06X", cur & 0xFFFFFF), svX + 20, pY + 2, C_MUTED, false);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Input
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean captured) {
        if (captured || event.button() != 0) return super.mouseClicked(event, captured);
        int mx = (int)event.x(), my = (int)event.y();

        if (capturingKey) { capturingKey = false; return true; }

        // Color picker
        if (pickerTarget != null) {
            int svX = pkX + PK_PAD, svY = pkY + PK_PAD, hueY = svY + PK_SIZE + PK_PAD;
            if (mx >= svX && mx <= svX + PK_SIZE && my >= svY && my <= svY + PK_SIZE) {
                dragSV = true;
                pickerS = clamp01((float)(mx - svX) / PK_SIZE);
                pickerV = clamp01(1f - (float)(my - svY) / PK_SIZE);
                applyPicker(); return true;
            }
            if (mx >= svX && mx <= svX + PK_SIZE && my >= hueY && my <= hueY + PK_HUE_H) {
                dragHue = true;
                pickerH = clamp01((float)(mx - svX) / PK_SIZE);
                applyPicker(); return true;
            }
            if (mx < pkX || mx > pkX + PK_W || my < pkY || my > pkY + PK_H) { pickerTarget = null; return true; }
            return true;
        }

        // Sliders
        for (SliderDef s : frameSliders) {
            if (my >= s.trackY() - 10 && my <= s.trackY() + 14 &&
                mx >= s.x() - 6 && mx <= s.x() + s.w() + 6) {
                activeSlider = s.id(); applySlider(s, mx); return true;
            }
        }

        // Theme swatches
        for (SwatchDef sw : frameSwatches) {
            if (mx >= sw.x() && mx < sw.x() + sw.w() && my >= sw.y() && my < sw.y() + sw.h()) {
                GlowConfig.guiAccentColor = sw.color(); GlowConfig.save(); return true;
            }
        }

        // Sidebar nav
        for (int i = 0; i < NAV.length; i++) {
            int iy = navTopY(i);
            if (mx >= sideX && mx < sideX + SIDEBAR_W && my >= iy && my < iy + NAV_H) {
                switchPage(NAV[i].page()); return true;
            }
        }

        // Feature toggle rows
        if (activePage == Page.GLOW) {
            int pillX = contX + PAD + (contW - PAD * 2) - PILL_W;
            if (playerToggleY >= 0 && my >= playerToggleY && my < playerToggleY + 26) {
                if (mx >= playerSwatchX - 2 && mx < playerSwatchX + SWATCH + 2
                        && my >= playerSwatchY - 2 && my < playerSwatchY + SWATCH + 2)
                    openPicker("playerGlow", GlowConfig.playerGlowColor, mx, my + 14);
                else if (mx >= pillX) { GlowConfig.playerGlowEnabled = !GlowConfig.playerGlowEnabled; GlowConfig.save(); }
                return true;
            }
            if (mobToggleY >= 0 && my >= mobToggleY && my < mobToggleY + 26) {
                if (mx >= mobSwatchX - 2 && mx < mobSwatchX + SWATCH + 2
                        && my >= mobSwatchY - 2 && my < mobSwatchY + SWATCH + 2)
                    openPicker("mobGlow", GlowConfig.mobGlowColor, mx, my + 14);
                else if (mx >= pillX) { GlowConfig.mobGlowEnabled = !GlowConfig.mobGlowEnabled; GlowConfig.save(); }
                return true;
            }
        }
        if (activePage == Page.OUTLINES && outlineToggleY >= 0) {
            int pillX = contX + PAD + (contW - PAD * 2) - PILL_W;
            if (my >= outlineToggleY && my < outlineToggleY + 26 && mx >= pillX) {
                GlowConfig.blockOutlineEnabled = !GlowConfig.blockOutlineEnabled; GlowConfig.save(); return true;
            }
        }

        // Whitelist
        handleWhitelistClick(mx, my);

        // Keybind button (settings page)
        if (activePage == Page.SETTINGS) {
            // Approximate keybind button position — just capture if click is roughly where it would be
            int kbY = contY + PAD + 38 + SEC_GAP + 14 + ((THEMES.length + 3) / 4) * (26 + 6) + 4 + SEC_GAP + 14;
            if (my >= kbY && my < kbY + 24 && mx >= contX + PAD && mx < contX + PAD + 200) {
                capturingKey = true; return true;
            }
        }

        // Close on outside click
        if (mx < px || mx > px + pw || my < py || my > py + ph) { onClose(); return true; }
        return super.mouseClicked(event, captured);
    }

    private void handleWhitelistClick(int mx, int my) {
        if (activePage != Page.GLOW && activePage != Page.OUTLINES) return;
        boolean mobs = activePage == Page.GLOW;

        int x  = contX + PAD, w = contW - PAD * 2;
        int sbY = searchBox.getY() - 5;
        int y   = sbY + 20 + 4;
        int halfW = (w - 4) / 2, col2X = x + halfW + 4;
        int maxY  = contY + contH - 4;

        List<?> results = mobs ? mobResults : blockResults;
        if (mx >= x && mx < x + halfW) {
            int row = (my - y) / ROW_H;
            if (row >= 0 && row < results.size()) {
                if (mobs) addMob((EntityType<?>)results.get(row));
                else addBlock((Block)results.get(row));
            }
            return;
        }
        if (mx >= col2X && mx < col2X + halfW) {
            int wy = y;
            for (int i = listScroll; i < whitelistCache.size() && wy + ROW_H <= maxY; i++, wy += ROW_H) {
                if (my < wy || my >= wy + ROW_H) continue;
                String id = whitelistCache.get(i);
                int rmX = col2X + halfW - 12;
                if (mx >= rmX - 3) { removeEntry(id); return; }

                // Toggle dot (8×8 at col2X+4, matches render)
                int dotSz = 8, dotX = col2X + 4, dotY = wy + (ROW_H - dotSz) / 2;
                if (mx >= dotX - 1 && mx < dotX + dotSz + 1 && my >= dotY - 1 && my < dotY + dotSz + 1) {
                    java.util.Set<String> disabled = mobs ? GlowConfig.disabledMobs : GlowConfig.disabledBlocks;
                    if (!disabled.remove(id)) disabled.add(id);
                    GlowConfig.save();
                    if (!mobs) BlockOutlineRenderer.rescanAll();
                    return;
                }

                if (!mobs) {
                    // swatch is now at col2X+16 (past toggle dot), matching render
                    int sx = col2X + 16, sy = wy + (ROW_H - SWATCH) / 2;
                    if (mx >= sx - 2 && mx < sx + SWATCH + 2 && my >= sy - 2 && my < sy + SWATCH + 2) {
                        openPicker(id, colorCache.getOrDefault(id, GlowConfig.defaultOutlineColor), mx, my + 10);
                        return;
                    }
                }
            }
        }
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double dx, double dy) {
        if (event.button() != 0) return super.mouseDragged(event, dx, dy);
        int mx = (int)event.x(), my = (int)event.y();
        if (dragSV) { pickerS = clamp01((float)(mx-(pkX+PK_PAD))/PK_SIZE); pickerV = clamp01(1f-(float)(my-(pkY+PK_PAD))/PK_SIZE); applyPicker(); return true; }
        if (dragHue) { pickerH = clamp01((float)(mx-(pkX+PK_PAD))/PK_SIZE); applyPicker(); return true; }
        if (activeSlider != null) {
            for (SliderDef s : frameSliders) { if (s.id().equals(activeSlider)) { applySlider(s, mx); return true; } }
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        dragSV = false; dragHue = false; activeSlider = null;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (mx >= contX + contW / 2 + 4 && my >= contY && my < contY + contH) {
            int vis = Math.max(1, (contH - 60) / ROW_H);
            listScroll = Math.max(0, Math.min(listScroll + (sy > 0 ? -1 : 1), Math.max(0, whitelistCache.size() - vis)));
            return true;
        }
        if (mx >= contX && mx < contX + contW / 2 && my >= contY && my < contY + contH) {
            int sz = activePage == Page.GLOW ? mobResults.size() : blockResults.size();
            selectedResult = Math.max(0, Math.min(selectedResult + (sy > 0 ? -1 : 1), sz - 1));
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int k = event.key();
        if (capturingKey) {
            if (k == GLFW.GLFW_KEY_ESCAPE) { capturingKey = false; return true; }
            GlowModClient.openGuiKey.setKey(InputConstants.Type.KEYSYM.getOrCreate(k));
            KeyMapping.resetMapping();
            assert minecraft != null; minecraft.options.save();
            capturingKey = false; return true;
        }
        // Same key that opens the GUI closes it while it's open.
        com.mojang.blaze3d.platform.InputConstants.Key bound =
                KeyBindingHelper.getBoundKeyOf(GlowModClient.openGuiKey);
        if (bound.getType() == com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM
                && bound.getValue() == k) {
            onClose(); return true;
        }
        if (k == GLFW.GLFW_KEY_ESCAPE) { if (pickerTarget != null) { pickerTarget = null; return true; } onClose(); return true; }
        if (k == GLFW.GLFW_KEY_ENTER || k == GLFW.GLFW_KEY_KP_ENTER) { addFromInput(); return true; }
        int sz = activePage == Page.GLOW ? mobResults.size() : blockResults.size();
        if (k == GLFW.GLFW_KEY_UP)   { selectedResult = Math.max(0, selectedResult - 1); return true; }
        if (k == GLFW.GLFW_KEY_DOWN) { selectedResult = Math.min(sz - 1, selectedResult + 1); return true; }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() { assert minecraft != null; minecraft.setScreen(parent); }

    // ─────────────────────────────────────────────────────────────────────────
    // Logic helpers
    // ─────────────────────────────────────────────────────────────────────────

    private void switchPage(Page p) {
        if (activePage == p) return;
        activePage = p; searchBox.setValue("");
        blockResults = new ArrayList<>(); mobResults = new ArrayList<>();
        selectedResult = 0; listScroll = 0; rebuildCache();
    }

    private void applySlider(SliderDef s, int mx) {
        float val = s.valueAt(mx);
        switch (s.id()) {
            case "outlineOpacity" -> GlowConfig.outlineOpacity     = val / 100f;
            case "lineWidth"      -> GlowConfig.outlineLineWidth   = val;
            case "glowRange"      -> GlowConfig.glowRange          = val;
            case "maxEntities"    -> GlowConfig.maxGlowEntities    = Math.round(val);
            case "scanRate"          -> GlowConfig.chunkScanRate          = Math.round(val);
            case "refreshInterval"   -> GlowConfig.chunkRefreshInterval   = Math.round(val * 20f);
            case "guiOpacity"     -> GlowConfig.guiOpacity         = val / 100f;
        }
        GlowConfig.save();
    }

    private void openPicker(String id, int color, int ax, int ay) {
        pickerTarget = id;
        float[] hsv = rgbToHsv(color & 0xFFFFFF);
        pickerH = hsv[0]; pickerS = hsv[1]; pickerV = hsv[2];
        pkX = Math.max(4, Math.min(ax, width  - PK_W - 4));
        pkY = Math.max(4, Math.min(ay, height - PK_H - 4));
    }

    private void applyPicker() {
        if (pickerTarget == null) return;
        int color = hsvToInt(pickerH, pickerS, pickerV) & 0xFFFFFF;
        switch (pickerTarget) {
            case "playerGlow" -> { GlowConfig.playerGlowColor = color; GlowConfig.save(); }
            case "mobGlow"    -> { GlowConfig.mobGlowColor    = color; GlowConfig.save(); }
            default -> { GlowConfig.blockWhitelist.put(pickerTarget, color); GlowConfig.save(); BlockOutlineRenderer.rescanAll(); rebuildCache(); }
        }
    }

    private void refreshResults(String query) {
        String q = query.toLowerCase().trim();
        if (q.isEmpty()) { blockResults = new ArrayList<>(); mobResults = new ArrayList<>(); return; }
        boolean mobs = activePage == Page.GLOW;
        if (mobs) {
            mobResults = StreamSupport.stream(BuiltInRegistries.ENTITY_TYPE.spliterator(), false)
                    .filter(et -> et.getCategory() != MobCategory.MISC)
                    .filter(et -> { Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(et); if (id == null || GlowConfig.mobGlowWhitelist.contains(id.toString())) return false; return id.toString().contains(q) || et.getDescription().getString().toLowerCase().contains(q); })
                    .limit(16).toList();
        } else {
            blockResults = StreamSupport.stream(BuiltInRegistries.BLOCK.spliterator(), false)
                    .filter(b -> { Identifier id = BuiltInRegistries.BLOCK.getKey(b); if (id == null || GlowConfig.blockWhitelist.containsKey(id.toString())) return false; return id.toString().contains(q) || b.getName().getString().toLowerCase().contains(q); })
                    .limit(16).toList();
        }
    }

    private void addBlock(Block block) {
        Identifier id = BuiltInRegistries.BLOCK.getKey(block); if (id == null) return;
        if (!GlowConfig.blockWhitelist.containsKey(id.toString())) { GlowConfig.blockWhitelist.put(id.toString(), GlowConfig.defaultOutlineColor); GlowConfig.save(); BlockOutlineRenderer.rescanAll(); rebuildCache(); }
        searchBox.setValue(""); blockResults = new ArrayList<>(); selectedResult = 0;
    }

    private void addMob(EntityType<?> et) {
        Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(et); if (id == null) return;
        if (GlowConfig.mobGlowWhitelist.add(id.toString())) { GlowConfig.save(); rebuildCache(); }
        searchBox.setValue(""); mobResults = new ArrayList<>(); selectedResult = 0;
    }

    private void addFromInput() {
        if (activePage == Page.GLOW) {
            if (!mobResults.isEmpty()) addMob(mobResults.get(Math.min(selectedResult, mobResults.size()-1)));
        } else if (activePage == Page.OUTLINES) {
            if (!blockResults.isEmpty()) { addBlock(blockResults.get(Math.min(selectedResult, blockResults.size()-1))); return; }
            String t = searchBox.getValue().trim(); Identifier id = Identifier.tryParse(t);
            if (id != null) { Block b = BuiltInRegistries.BLOCK.getValue(id); if (b != null) addBlock(b); }
        }
    }

    private void removeEntry(String id) {
        if (activePage == Page.GLOW) GlowConfig.mobGlowWhitelist.remove(id);
        else { GlowConfig.blockWhitelist.remove(id); BlockOutlineRenderer.rescanAll(); }
        GlowConfig.save(); rebuildCache();
        listScroll = Math.min(listScroll, Math.max(0, whitelistCache.size() - Math.max(1, (contH - 60) / ROW_H)));
    }

    private void rebuildCache() {
        nameCache.clear(); colorCache.clear();
        boolean mobs = activePage == Page.GLOW;
        if (mobs) {
            whitelistCache = new ArrayList<>(GlowConfig.mobGlowWhitelist);
            for (String id : whitelistCache) { Identifier p = Identifier.tryParse(id); EntityType<?> et = p != null ? BuiltInRegistries.ENTITY_TYPE.getValue(p) : null; nameCache.put(id, et != null ? et.getDescription().getString() : id); }
        } else {
            whitelistCache = new ArrayList<>(GlowConfig.blockWhitelist.keySet());
            for (String id : whitelistCache) { Identifier p = Identifier.tryParse(id); Block b = p != null ? BuiltInRegistries.BLOCK.getValue(p) : null; nameCache.put(id, b != null ? b.getName().getString() : id); colorCache.put(id, GlowConfig.blockWhitelist.getOrDefault(id, GlowConfig.defaultOutlineColor)); }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Math helpers
    // ─────────────────────────────────────────────────────────────────────────

    private static float clamp01(float v) { return Math.max(0f, Math.min(1f, v)); }

    private static float[] rgbToHsv(int rgb) {
        float r=((rgb>>16)&0xFF)/255f, gv=((rgb>>8)&0xFF)/255f, b=(rgb&0xFF)/255f;
        float max=Math.max(r,Math.max(gv,b)), min=Math.min(r,Math.min(gv,b)), d=max-min;
        float h=0, s=max==0?0:d/max, v=max;
        if (d!=0){if(max==r)h=(gv-b)/d+(gv<b?6:0);else if(max==gv)h=(b-r)/d+2;else h=(r-gv)/d+4;h/=6f;}
        return new float[]{h,s,v};
    }

    private static int hsvToInt(float h, float s, float v) {
        float[] c=hsvToRgb(h,s,v); return 0xFF000000|((int)(c[0]*255)<<16)|((int)(c[1]*255)<<8)|(int)(c[2]*255);
    }

    private static float[] hsvToRgb(float h, float s, float v) {
        if(s==0) return new float[]{v,v,v};
        float hh=h*6; int i=(int)hh; float f=hh-i;
        float p=v*(1-s),q=v*(1-f*s),t=v*(1-(1-f)*s);
        return switch(i%6){case 0->new float[]{v,t,p};case 1->new float[]{q,v,p};case 2->new float[]{p,v,t};case 3->new float[]{p,q,v};case 4->new float[]{t,p,v};default->new float[]{v,p,q};};
    }

    private static int lerpRgb(int a, int b, float t) {
        int ar=(a>>16)&0xFF,ag=(a>>8)&0xFF,ab=a&0xFF,br=(b>>16)&0xFF,bg=(b>>8)&0xFF,bb=b&0xFF;
        return 0xFF000000|((int)(ar+(br-ar)*t)<<16)|((int)(ag+(bg-ag)*t)<<8)|(int)(ab+(bb-ab)*t);
    }
}
