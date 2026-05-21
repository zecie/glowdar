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

    private static final int MAX_RES = 16;
    private static final int ROW_H   = 16;
    private static final int FEAT_H  = 32;
    private static final int PILL_W  = 36;
    private static final int PILL_H  = 16;
    private static final int SWATCH  = 12;
    private static final int PAD     = 18;
    private static final int RADIUS  = 8;

    private static final int C_DIM     = 0xB0000000;
    private static final int C_BG      = 0xFF0D1117;
    private static final int C_HEADER  = 0xFF161B22;
    private static final int C_SURFACE = 0xFF161B22;
    private static final int C_BORDER  = 0xFF30363D;
    private static final int C_TEXT    = 0xFFE6EDF3;
    private static final int C_MUTED   = 0xFF7D8590;
    private static final int C_DIM_TXT = 0xFF484F58;
    private static final int C_ACCENT  = 0xFF2F81F7;
    private static final int C_GREEN   = 0xFF3FB950;
    private static final int C_RED     = 0xFFF85149;
    private static final int C_HOV     = 0x0CFFFFFF;
    private static final int C_SEL     = 0x16FFFFFF;

    private static final int PK_SIZE  = 120;
    private static final int PK_HUE_H = 12;
    private static final int PK_PAD   = 12;
    private static final int PK_W     = PK_SIZE + PK_PAD * 2;
    private static final int PK_H     = PK_PAD + PK_SIZE + PK_PAD + PK_HUE_H + PK_PAD + 12 + PK_PAD;

    private final Screen parent;
    private EditBox searchBox;
    private boolean mobMode = false;

    private List<Block>         blockResults   = new ArrayList<>();
    private List<EntityType<?>> mobResults     = new ArrayList<>();
    private List<String>        whitelistCache = new ArrayList<>();
    private Map<String, String>  nameCache     = new LinkedHashMap<>();
    private Map<String, Integer> colorCache    = new LinkedHashMap<>();
    private int  selectedResult = 0;
    private int  listScroll     = 0;
    private boolean capturingKey = false;

    private float animPlayer  = 0f;
    private float animMob     = 0f;
    private float animOutline = 0f;

    private String  pickerTarget = null;
    private float   pickerH = 0f, pickerS = 1f, pickerV = 1f;
    private int     pkX, pkY;
    private boolean dragSV = false, dragHue = false;

    private int px, py, pw, ph;
    private int headerH, featSectionY, featSectionH;
    private int tabY, tabH, contentY, contentH;
    private int splitX, footerY, footerH;

    private int rowPlayerY, rowMobY, rowOutlineY;
    private int keyBtnX, keyBtnW, keyBtnY;

    public BlockWhitelistScreen(Screen parent) {
        super(Component.empty());
        this.parent = parent;
    }

    @Override
    protected void init() {
        pw = Math.min(600, width  - 80);
        ph = Math.min(420, height - 80);
        px = (width  - pw) / 2;
        py = (height - ph) / 2;

        headerH      = 50;
        featSectionY = py + headerH;
        featSectionH = FEAT_H * 3;
        tabY         = featSectionY + featSectionH;
        tabH         = 24;
        footerH      = 34;
        footerY      = py + ph - footerH;
        contentY     = tabY + tabH;
        contentH     = footerY - contentY;
        splitX       = px + pw / 2;

        animPlayer  = GlowConfig.playerGlowEnabled  ? 1f : 0f;
        animMob     = GlowConfig.mobGlowEnabled      ? 1f : 0f;
        animOutline = GlowConfig.blockOutlineEnabled ? 1f : 0f;

        rebuildCache();

        searchBox = new EditBox(font, px + PAD + 6, contentY + 9, splitX - px - PAD * 2 - 12, 10, Component.empty());
        searchBox.setMaxLength(100);
        searchBox.setBordered(false);
        searchBox.setResponder(q -> { selectedResult = 0; refreshResults(q); });
        addWidget(searchBox);
        setInitialFocus(searchBox);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float delta) {
        g.fill(0, 0, width, height, C_DIM);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float delta) {
        float step = delta * 0.3f;
        animPlayer  += ((GlowConfig.playerGlowEnabled  ? 1f : 0f) - animPlayer)  * step;
        animMob     += ((GlowConfig.mobGlowEnabled      ? 1f : 0f) - animMob)     * step;
        animOutline += ((GlowConfig.blockOutlineEnabled ? 1f : 0f) - animOutline) * step;

        super.render(g, mx, my, delta);

        GuiRenderer.shadow(g, px, py, pw, ph, RADIUS, 8);
        GuiRenderer.roundedRect(g, px, py, pw, ph, RADIUS, C_BG);

        drawHeader(g, mx, my);
        drawFeatureSection(g, mx, my);
        drawTabs(g, mx, my);
        drawContent(g, mx, my, delta);
        drawFooter(g, mx, my);

        if (pickerTarget != null) renderPicker(g, mx, my);
    }

    private void drawHeader(GuiGraphics g, int mx, int my) {
        GuiRenderer.roundedRect(g, px, py, pw, headerH, RADIUS, C_HEADER);
        g.fill(px + RADIUS, py + headerH - 1, px + pw - RADIUS, py + headerH, C_BORDER);

        g.drawString(font, "GlowMod", px + PAD, py + (headerH - 8) / 2, C_TEXT, false);

        String kLabel = capturingKey ? "press a key..." : keyName();
        keyBtnW = font.width(kLabel) + 18;
        keyBtnX = px + pw - keyBtnW - PAD;
        keyBtnY = py + (headerH - 18) / 2;
        boolean kHov = mx >= keyBtnX && mx < keyBtnX + keyBtnW && my >= keyBtnY && my < keyBtnY + 18;
        GuiRenderer.roundedRect(g, keyBtnX, keyBtnY, keyBtnW, 18, 5, kHov ? 0xFF1C2128 : 0xFF0D1117);
        g.fill(keyBtnX, keyBtnY, keyBtnX + keyBtnW, keyBtnY + 18, capturingKey ? 0x22FFFFFF : 0);
        g.fill(keyBtnX - 1, keyBtnY - 1, keyBtnX + keyBtnW + 1, keyBtnY + 19, C_BORDER);
        GuiRenderer.roundedRect(g, keyBtnX, keyBtnY, keyBtnW, 18, 5, kHov ? 0xFF1C2128 : 0xFF0D1117);
        g.drawString(font, kLabel, keyBtnX + 9, keyBtnY + 5, capturingKey ? C_ACCENT : C_MUTED, false);
    }

    private void drawFeatureSection(GuiGraphics g, int mx, int my) {
        g.fill(px, featSectionY, px + pw, featSectionY + featSectionH, C_SURFACE);
        g.fill(px, featSectionY + featSectionH, px + pw, featSectionY + featSectionH + 1, C_BORDER);

        rowPlayerY  = featSectionY;
        rowMobY     = featSectionY + FEAT_H;
        rowOutlineY = featSectionY + FEAT_H * 2;

        renderFeatureRow(g, mx, my, rowPlayerY,  "Player Glow",    animPlayer,  GlowConfig.playerGlowEnabled,  GlowConfig.playerGlowColor, "playerGlow");
        renderFeatureRow(g, mx, my, rowMobY,     "Mob Glow",       animMob,     GlowConfig.mobGlowEnabled,     GlowConfig.mobGlowColor,    "mobGlow");
        renderFeatureRow(g, mx, my, rowOutlineY, "Block Outlines", animOutline, GlowConfig.blockOutlineEnabled, -1,                         null);
    }

    private void drawTabs(GuiGraphics g, int mx, int my) {
        g.fill(px, tabY, px + pw, tabY + tabH, C_BG);
        g.fill(px, tabY + tabH, px + pw, tabY + tabH + 1, C_BORDER);
        String[] tabs = {"blocks", "mobs"};
        int tx = px + PAD;
        for (int i = 0; i < tabs.length; i++) {
            boolean active = (i == 1) == mobMode;
            int tw = font.width(tabs[i]) + 18;
            boolean hov = mx >= tx && mx < tx + tw && my >= tabY && my < tabY + tabH;
            if (active)   g.fill(tx, tabY, tx + tw, tabY + tabH, C_SEL);
            else if (hov) g.fill(tx, tabY, tx + tw, tabY + tabH, C_HOV);
            if (active)   g.fill(tx, tabY + tabH - 2, tx + tw, tabY + tabH, C_ACCENT);
            g.drawString(font, tabs[i], tx + 9, tabY + (tabH - 8) / 2, active ? C_TEXT : C_MUTED, false);
            tx += tw + 4;
        }
    }

    private void drawContent(GuiGraphics g, int mx, int my, float delta) {
        g.fill(px, contentY, px + pw, footerY, C_BG);
        g.fill(splitX, contentY, splitX + 1, footerY, C_BORDER);
        renderSearchCol(g, mx, my, delta);
        renderListCol(g, mx, my);
    }

    private void drawFooter(GuiGraphics g, int mx, int my) {
        g.fill(px, footerY, px + pw, footerY + 1, C_BORDER);
        g.fill(px, footerY + 1, px + pw, py + ph, C_HEADER);
        int cx = px + pw / 2;
        boolean fHov = mx >= cx - 36 && mx < cx + 36 && my >= footerY && my < footerY + footerH;
        if (fHov) {
            GuiRenderer.roundedRect(g, cx - 36, footerY + 6, 72, footerH - 12, 5, 0xFF1C2128);
        }
        g.drawCenteredString(font, "close", cx, footerY + (footerH - 8) / 2, fHov ? C_TEXT : C_MUTED);
    }

    private void renderFeatureRow(GuiGraphics g, int mx, int my, int y,
                                   String label, float anim, boolean on, int color, String pickerId) {
        boolean hov = mx >= px && mx < px + pw && my >= y && my < y + FEAT_H;
        if (hov) g.fill(px, y, px + pw, y + FEAT_H, C_HOV);
        g.fill(px, y + FEAT_H - 1, px + pw, y + FEAT_H, C_BORDER);

        g.drawString(font, label, px + PAD, y + (FEAT_H - 8) / 2, on ? C_TEXT : C_MUTED, false);

        int pillX = px + pw - PAD - PILL_W;
        int pillY = y + (FEAT_H - PILL_H) / 2;
        int pillColor = lerpRgb(0xFF30363D, C_GREEN, anim);
        int pillBgColor = lerpRgb(0xFF161B22, C_GREEN, anim * 0.15f);
        GuiRenderer.roundedRect(g, pillX, pillY, PILL_W, PILL_H, PILL_H / 2f, pillColor);
        int knobSize = PILL_H - 4;
        int knobX = pillX + 2 + (int)((PILL_W - PILL_H) * anim);
        int knobY = pillY + 2;
        GuiRenderer.roundedRect(g, knobX, knobY, knobSize, knobSize, knobSize / 2f, 0xFFFFFFFF);

        if (color >= 0 && pickerId != null) {
            int sx = pillX - 10 - SWATCH;
            int sy = y + (FEAT_H - SWATCH) / 2;
            boolean swHov = pickerTarget == null && mx >= sx - 2 && mx < sx + SWATCH + 2 && my >= sy - 2 && my < sy + SWATCH + 2;
            GuiRenderer.roundedRect(g, sx - 1, sy - 1, SWATCH + 2, SWATCH + 2, 3, C_BORDER);
            GuiRenderer.roundedRect(g, sx, sy, SWATCH, SWATCH, 3, color | 0xFF000000);
            if (swHov || pickerId.equals(pickerTarget)) g.fill(sx - 2, sy - 2, sx + SWATCH + 2, sy + SWATCH + 2, 0x33FFFFFF);
        }
    }

    private void renderSearchCol(GuiGraphics g, int mx, int my, float delta) {
        int colW  = splitX - px;
        int sbBgY = contentY + 6;

        GuiRenderer.roundedRect(g, px + PAD, sbBgY, colW - PAD * 2, 22, 5, C_SURFACE);
        g.fill(px + PAD, sbBgY + 21, splitX - PAD, sbBgY + 22, searchBox.isFocused() ? C_ACCENT : C_BORDER);

        if (searchBox.getValue().isEmpty()) {
            g.drawString(font, mobMode ? "Search mobs..." : "Search blocks...", px + PAD + 6, sbBgY + 7, C_DIM_TXT, false);
        }
        searchBox.setX(px + PAD + 6);
        searchBox.setY(sbBgY + 6);
        searchBox.setWidth(colW - PAD * 2 - 12);
        searchBox.render(g, mx, my, delta);

        List<?> results = mobMode ? mobResults : blockResults;
        if (!results.isEmpty()) {
            int listY = sbBgY + 28;
            int listH = contentH - 36;
            int y = listY;
            for (int i = 0; i < results.size() && y + ROW_H <= listY + listH; i++, y += ROW_H) {
                String name = mobMode ? ((EntityType<?>)results.get(i)).getDescription().getString()
                                      : ((Block)results.get(i)).getName().getString();
                boolean sel = i == selectedResult;
                boolean hov = mx >= px && mx < splitX && my >= y && my < y + ROW_H;
                if (sel) {
                    GuiRenderer.roundedRect(g, px + PAD - 2, y, splitX - px - PAD * 2 + 4, ROW_H - 2, 4, 0xFF1C2128);
                    g.fill(px + PAD - 2, y + 2, px + PAD, y + ROW_H - 4, C_ACCENT);
                } else if (hov) {
                    g.fill(px, y, splitX, y + ROW_H - 1, C_HOV);
                }
                g.drawString(font, name, px + PAD + 6, y + (ROW_H - 8) / 2, sel || hov ? C_TEXT : C_MUTED, false);
            }
        } else if (!searchBox.getValue().isEmpty()) {
            g.drawString(font, "no results", px + PAD + 4, contentY + 36, C_DIM_TXT, false);
        }
    }

    private void renderListCol(GuiGraphics g, int mx, int my) {
        List<String> list = whitelistCache;
        int vis = Math.max(1, contentH / ROW_H);
        listScroll = Math.min(listScroll, Math.max(0, list.size() - vis));

        int x0   = splitX + 1;
        int colW  = px + pw - x0;
        boolean hasScroll = list.size() > vis;
        int textMaxW = colW - PAD * 2 - (mobMode ? 0 : SWATCH + 6) - 20 - (hasScroll ? 10 : 0);

        if (list.isEmpty()) {
            g.drawString(font, "nothing added yet", x0 + PAD, contentY + 10, C_DIM_TXT, false);
            return;
        }

        int y = contentY + 4;
        for (int i = listScroll; i < list.size() && y + ROW_H <= contentY + contentH; i++, y += ROW_H) {
            String id   = list.get(i);
            String name = nameCache.getOrDefault(id, id);
            boolean hov = mx >= x0 && mx < x0 + colW && my >= y && my < y + ROW_H;
            if (hov) g.fill(x0, y, x0 + colW, y + ROW_H - 1, C_HOV);

            int removeX  = x0 + colW - PAD - (hasScroll ? 10 : 0) - 6;
            boolean btnHov = mx >= removeX - 2 && mx < removeX + 8 && my >= y && my < y + ROW_H;

            int textX = x0 + PAD;
            if (!mobMode) {
                int c  = colorCache.getOrDefault(id, 0x00FF00) | 0xFF000000;
                int sx = x0 + PAD, sy = y + (ROW_H - SWATCH) / 2;
                boolean swHov = mx >= sx - 2 && mx < sx + SWATCH + 2 && my >= sy - 2 && my < sy + SWATCH + 2;
                GuiRenderer.roundedRect(g, sx - 1, sy - 1, SWATCH + 2, SWATCH + 2, 3, C_BORDER);
                GuiRenderer.roundedRect(g, sx, sy, SWATCH, SWATCH, 3, c);
                if (swHov || id.equals(pickerTarget)) g.fill(sx - 2, sy - 2, sx + SWATCH + 2, sy + SWATCH + 2, 0x33FFFFFF);
                textX = sx + SWATCH + 5;
            }
            String disp = font.width(name) > textMaxW ? font.plainSubstrByWidth(name, textMaxW - 6) + "…" : name;
            g.drawString(font, disp, textX, y + (ROW_H - 8) / 2, hov ? C_TEXT : C_MUTED, false);
            g.drawString(font, "✕", removeX, y + (ROW_H - 8) / 2, btnHov ? C_RED : C_DIM_TXT, false);
        }

        if (hasScroll) {
            int sbX    = x0 + colW - 4;
            int totalH = contentH - 4;
            int thumbH = Math.max(8, totalH * vis / list.size());
            int thumbY = contentY + 4 + (totalH - thumbH) * listScroll / Math.max(1, list.size() - vis);
            g.fill(sbX, contentY + 4, sbX + 2, contentY + 4 + totalH, C_BORDER);
            g.fill(sbX, thumbY, sbX + 2, thumbY + thumbH, 0xFF484F58);
        }
    }

    private void renderPicker(GuiGraphics g, int mx, int my) {
        GuiRenderer.shadow(g, pkX, pkY, PK_W, PK_H, 6, 5);
        GuiRenderer.roundedRect(g, pkX, pkY, PK_W, PK_H, 6, C_SURFACE);
        g.fill(pkX, pkY, pkX + PK_W, pkY + PK_H, 0x00000000);
        g.fill(pkX - 1, pkY - 1, pkX + PK_W + 1, pkY + PK_H + 1, C_BORDER);
        GuiRenderer.roundedRect(g, pkX, pkY, PK_W, PK_H, 6, C_SURFACE);

        int svX = pkX + PK_PAD, svY = pkY + PK_PAD;

        float[] hr = hsvToRgb(pickerH, 1f, 1f);
        for (int col = 0; col < PK_SIZE; col++) {
            float s   = (float) col / (PK_SIZE - 1);
            int   top = 0xFF000000
                | ((int)((1f + (hr[0] - 1f) * s) * 255) << 16)
                | ((int)((1f + (hr[1] - 1f) * s) * 255) << 8)
                | (int)((1f + (hr[2] - 1f) * s) * 255);
            g.fillGradient(svX + col, svY, svX + col + 1, svY + PK_SIZE, top, 0xFF000000);
        }

        int cx2 = svX + Math.round(pickerS * (PK_SIZE - 1));
        int cy2 = svY + Math.round((1f - pickerV) * (PK_SIZE - 1));
        GuiRenderer.roundedRect(g, cx2 - 5, cy2 - 5, 10, 10, 5, 0xAAFFFFFF);
        g.fill(cx2 - 4, cy2 - 1, cx2 + 4, cy2 + 1, 0xFF000000);
        g.fill(cx2 - 1, cy2 - 4, cx2 + 1, cy2 + 4, 0xFF000000);

        int hueY = svY + PK_SIZE + PK_PAD;
        for (int col = 0; col < PK_SIZE; col++) {
            float[] c = hsvToRgb((float) col / PK_SIZE, 1f, 1f);
            g.fill(svX + col, hueY, svX + col + 1, hueY + PK_HUE_H,
                    0xFF000000 | ((int)(c[0]*255) << 16) | ((int)(c[1]*255) << 8) | (int)(c[2]*255));
        }
        int hx = svX + Math.round(pickerH * (PK_SIZE - 1));
        g.fill(hx - 1, hueY - 2, hx + 1, hueY + PK_HUE_H + 2, 0xFFFFFFFF);

        int pY  = hueY + PK_HUE_H + PK_PAD;
        int cur = hsvToInt(pickerH, pickerS, pickerV);
        GuiRenderer.roundedRect(g, svX - 1, pY - 1, 18, 14, 3, C_BORDER);
        GuiRenderer.roundedRect(g, svX, pY, 16, 12, 3, cur);
        g.drawString(font, String.format("#%06X", cur & 0xFFFFFF), svX + 22, pY + 2, C_MUTED, false);
    }

    private static float[] rgbToHsv(int rgb) {
        float r=((rgb>>16)&0xFF)/255f, g=((rgb>>8)&0xFF)/255f, b=(rgb&0xFF)/255f;
        float max=Math.max(r,Math.max(g,b)), min=Math.min(r,Math.min(g,b)), d=max-min;
        float h=0, s=max==0?0:d/max, v=max;
        if (d!=0) {
            if (max==r) h=(g-b)/d+(g<b?6:0);
            else if (max==g) h=(b-r)/d+2;
            else h=(r-g)/d+4;
            h/=6f;
        }
        return new float[]{h,s,v};
    }

    private static int hsvToInt(float h, float s, float v) {
        float[] c=hsvToRgb(h,s,v);
        return 0xFF000000|((int)(c[0]*255)<<16)|((int)(c[1]*255)<<8)|(int)(c[2]*255);
    }

    private static float[] hsvToRgb(float h, float s, float v) {
        if (s==0) return new float[]{v,v,v};
        float hh=h*6; int i=(int)hh; float f=hh-i;
        float p=v*(1-s),q=v*(1-f*s),t=v*(1-(1-f)*s);
        return switch(i%6){
            case 0->new float[]{v,t,p}; case 1->new float[]{q,v,p};
            case 2->new float[]{p,v,t}; case 3->new float[]{p,q,v};
            case 4->new float[]{t,p,v}; default->new float[]{v,p,q};
        };
    }

    private static int lerpRgb(int a, int b, float t) {
        int ar=(a>>16)&0xFF,ag=(a>>8)&0xFF,ab=a&0xFF;
        int br=(b>>16)&0xFF,bg=(b>>8)&0xFF,bb=b&0xFF;
        return 0xFF000000|((int)(ar+(br-ar)*t)<<16)|((int)(ag+(bg-ag)*t)<<8)|(int)(ab+(bb-ab)*t);
    }

    private void openPicker(String id, int color, int ax, int ay) {
        pickerTarget=id;
        float[] hsv=rgbToHsv(color&0xFFFFFF);
        pickerH=hsv[0]; pickerS=hsv[1]; pickerV=hsv[2];
        pkX=Math.max(4,Math.min(ax, width-PK_W-4));
        pkY=Math.max(4,Math.min(ay, height-PK_H-4));
    }

    private void applyPicker() {
        if (pickerTarget==null) return;
        int color=hsvToInt(pickerH,pickerS,pickerV)&0xFFFFFF;
        switch (pickerTarget) {
            case "playerGlow" -> { GlowConfig.playerGlowColor=color; GlowConfig.save(); }
            case "mobGlow"    -> { GlowConfig.mobGlowColor   =color; GlowConfig.save(); }
            default -> { GlowConfig.blockWhitelist.put(pickerTarget,color); GlowConfig.save(); BlockOutlineRenderer.rescanAll(); rebuildCache(); }
        }
    }

    private void refreshResults(String query) {
        String q=query.toLowerCase().trim();
        if (q.isEmpty()) { blockResults=new ArrayList<>(); mobResults=new ArrayList<>(); return; }
        if (mobMode) {
            mobResults=StreamSupport.stream(BuiltInRegistries.ENTITY_TYPE.spliterator(),false)
                .filter(et->et.getCategory()!=MobCategory.MISC)
                .filter(et->{Identifier id=BuiltInRegistries.ENTITY_TYPE.getKey(et); if(id==null) return false;
                    if(GlowConfig.mobGlowWhitelist.contains(id.toString())) return false;
                    return id.toString().contains(q)||et.getDescription().getString().toLowerCase().contains(q);
                }).limit(MAX_RES).toList();
        } else {
            blockResults=StreamSupport.stream(BuiltInRegistries.BLOCK.spliterator(),false)
                .filter(b->{Identifier id=BuiltInRegistries.BLOCK.getKey(b); if(id==null) return false;
                    if(GlowConfig.blockWhitelist.containsKey(id.toString())) return false;
                    return id.toString().contains(q)||b.getName().getString().toLowerCase().contains(q);
                }).limit(MAX_RES).toList();
        }
    }

    private void addBlock(Block block) {
        Identifier id=BuiltInRegistries.BLOCK.getKey(block); if(id==null) return;
        if(!GlowConfig.blockWhitelist.containsKey(id.toString())) {
            GlowConfig.blockWhitelist.put(id.toString(),GlowConfig.defaultOutlineColor);
            GlowConfig.save(); BlockOutlineRenderer.rescanAll(); rebuildCache();
        }
        searchBox.setValue(""); blockResults=new ArrayList<>(); selectedResult=0;
    }

    private void addMob(EntityType<?> et) {
        Identifier id=BuiltInRegistries.ENTITY_TYPE.getKey(et); if(id==null) return;
        if(GlowConfig.mobGlowWhitelist.add(id.toString())) { GlowConfig.save(); rebuildCache(); }
        searchBox.setValue(""); mobResults=new ArrayList<>(); selectedResult=0;
    }

    private void addFromInput() {
        if (mobMode) {
            if(!mobResults.isEmpty()) addMob(mobResults.get(Math.min(selectedResult,mobResults.size()-1)));
        } else {
            if(!blockResults.isEmpty()) { addBlock(blockResults.get(Math.min(selectedResult,blockResults.size()-1))); return; }
            String t=searchBox.getValue().trim();
            Identifier id=Identifier.tryParse(t);
            if(id!=null) { Block b=BuiltInRegistries.BLOCK.getValue(id); if(b!=null) addBlock(b); }
        }
    }

    private void removeEntry(String id) {
        if(mobMode) GlowConfig.mobGlowWhitelist.remove(id);
        else { GlowConfig.blockWhitelist.remove(id); BlockOutlineRenderer.rescanAll(); }
        GlowConfig.save(); rebuildCache();
        listScroll=Math.min(listScroll,Math.max(0,whitelistCache.size()-Math.max(1,contentH/ROW_H)));
    }

    private void switchTab(boolean mobs) {
        if(mobMode==mobs) return;
        mobMode=mobs; selectedResult=0; listScroll=0;
        searchBox.setValue(""); blockResults=new ArrayList<>(); mobResults=new ArrayList<>();
        rebuildCache();
    }

    private void rebuildCache() {
        nameCache.clear(); colorCache.clear();
        if (mobMode) {
            whitelistCache=new ArrayList<>(GlowConfig.mobGlowWhitelist);
            for(String id:whitelistCache) {
                Identifier p=Identifier.tryParse(id);
                EntityType<?> et=p!=null?BuiltInRegistries.ENTITY_TYPE.getValue(p):null;
                nameCache.put(id,et!=null?et.getDescription().getString():id);
            }
        } else {
            whitelistCache=new ArrayList<>(GlowConfig.blockWhitelist.keySet());
            for(String id:whitelistCache) {
                Identifier p=Identifier.tryParse(id);
                Block b=p!=null?BuiltInRegistries.BLOCK.getValue(p):null;
                nameCache.put(id,b!=null?b.getName().getString():id);
                colorCache.put(id,GlowConfig.blockWhitelist.getOrDefault(id,GlowConfig.defaultOutlineColor));
            }
        }
    }

    private String keyName() {
        return KeyBindingHelper.getBoundKeyOf(GlowModClient.openGuiKey).getDisplayName().getString();
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean captured) {
        if(captured||event.button()!=0) return super.mouseClicked(event,captured);
        int mx=(int)event.x(), my=(int)event.y();

        if(capturingKey) { capturingKey=false; return true; }

        if(pickerTarget!=null) {
            int svX=pkX+PK_PAD, svY=pkY+PK_PAD, hueY=svY+PK_SIZE+PK_PAD;
            if(mx>=svX&&mx<=svX+PK_SIZE&&my>=svY&&my<=svY+PK_SIZE) {
                dragSV=true;
                pickerS=Math.max(0f,Math.min(1f,(float)(mx-svX)/PK_SIZE));
                pickerV=Math.max(0f,Math.min(1f,1f-(float)(my-svY)/PK_SIZE));
                applyPicker(); return true;
            }
            if(mx>=svX&&mx<=svX+PK_SIZE&&my>=hueY&&my<=hueY+PK_HUE_H) {
                dragHue=true;
                pickerH=Math.max(0f,Math.min(1f,(float)(mx-svX)/PK_SIZE));
                applyPicker(); return true;
            }
            if(mx<pkX||mx>pkX+PK_W||my<pkY||my>pkY+PK_H) { pickerTarget=null; return true; }
            return true;
        }

        if(mx>=keyBtnX&&mx<keyBtnX+keyBtnW&&my>=keyBtnY&&my<keyBtnY+18) { capturingKey=true; return true; }

        int pillX=px+pw-PAD-PILL_W;
        int swatchX=pillX-10-SWATCH;
        for(int i=0;i<3;i++) {
            int ry=(i==0?rowPlayerY:i==1?rowMobY:rowOutlineY);
            if(my<ry||my>=ry+FEAT_H) continue;
            int sy=ry+(FEAT_H-SWATCH)/2;
            if(i==0) {
                if(mx>=swatchX-2&&mx<swatchX+SWATCH+2&&my>=sy-2&&my<sy+SWATCH+2) { openPicker("playerGlow",GlowConfig.playerGlowColor,mx,my+14); return true; }
                GlowConfig.playerGlowEnabled=!GlowConfig.playerGlowEnabled; GlowConfig.save(); return true;
            } else if(i==1) {
                if(mx>=swatchX-2&&mx<swatchX+SWATCH+2&&my>=sy-2&&my<sy+SWATCH+2) { openPicker("mobGlow",GlowConfig.mobGlowColor,mx,my+14); return true; }
                GlowConfig.mobGlowEnabled=!GlowConfig.mobGlowEnabled; GlowConfig.save(); return true;
            } else {
                GlowConfig.blockOutlineEnabled=!GlowConfig.blockOutlineEnabled; GlowConfig.save(); return true;
            }
        }

        if(my>=tabY&&my<tabY+tabH) {
            int tx=px+PAD; int tw0=font.width("blocks")+18;
            if(mx>=tx&&mx<tx+tw0) { switchTab(false); return true; }
            tx+=tw0+4;
            if(mx>=tx&&mx<tx+font.width("mobs")+18) { switchTab(true); return true; }
        }

        int sbBgY=contentY+6;
        int listY0=sbBgY+28, listH=contentH-36;
        if(mx>=px&&mx<splitX&&my>=listY0&&my<listY0+listH) {
            int row=(my-listY0)/ROW_H;
            if(mobMode&&row>=0&&row<mobResults.size())    { addMob(mobResults.get(row));    return true; }
            if(!mobMode&&row>=0&&row<blockResults.size()) { addBlock(blockResults.get(row)); return true; }
        }

        int x0=splitX+1, colW2=px+pw-x0;
        boolean hasScroll=whitelistCache.size()>Math.max(1,contentH/ROW_H);
        int y=contentY+4;
        for(int i=listScroll;i<whitelistCache.size()&&y+ROW_H<=contentY+contentH;i++,y+=ROW_H) {
            if(my<y||my>=y+ROW_H||mx<x0||mx>=x0+colW2) continue;
            int removeX=x0+colW2-PAD-(hasScroll?10:0)-6;
            if(mx>=removeX-2) { removeEntry(whitelistCache.get(i)); return true; }
            if(!mobMode) {
                int sx=x0+PAD, sy=y+(ROW_H-SWATCH)/2;
                if(mx>=sx-2&&mx<sx+SWATCH+2&&my>=sy-2&&my<sy+SWATCH+2) {
                    openPicker(whitelistCache.get(i),colorCache.getOrDefault(whitelistCache.get(i),GlowConfig.defaultOutlineColor),mx,my+10);
                    return true;
                }
            }
            return true;
        }

        int cx=px+pw/2;
        if(mx>=cx-36&&mx<cx+36&&my>=footerY&&my<footerY+footerH) { onClose(); return true; }
        return super.mouseClicked(event,captured);
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event,double dx,double dy) {
        if(event.button()!=0) return super.mouseDragged(event,dx,dy);
        double mx=event.x(),my=event.y();
        if(dragSV) {
            pickerS=Math.max(0f,Math.min(1f,(float)(mx-(pkX+PK_PAD))/PK_SIZE));
            pickerV=Math.max(0f,Math.min(1f,1f-(float)(my-(pkY+PK_PAD))/PK_SIZE));
            applyPicker(); return true;
        }
        if(dragHue) {
            pickerH=Math.max(0f,Math.min(1f,(float)(mx-(pkX+PK_PAD))/PK_SIZE));
            applyPicker(); return true;
        }
        return super.mouseDragged(event,dx,dy);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        dragSV=false; dragHue=false; return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mx,double my,double sx,double sy) {
        if(mx>=splitX&&mx<px+pw&&my>=contentY&&my<contentY+contentH) {
            int vis=Math.max(1,contentH/ROW_H);
            listScroll=Math.max(0,Math.min(listScroll+(sy>0?-1:1),Math.max(0,whitelistCache.size()-vis)));
            return true;
        }
        if(mx>=px&&mx<splitX&&my>=contentY+28&&my<contentY+contentH) {
            int size=mobMode?mobResults.size():blockResults.size();
            selectedResult=Math.max(0,Math.min(selectedResult+(sy>0?-1:1),size-1));
            return true;
        }
        return super.mouseScrolled(mx,my,sx,sy);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int k=event.key();
        if(capturingKey) {
            if(k==GLFW.GLFW_KEY_ESCAPE) { capturingKey=false; return true; }
            GlowModClient.openGuiKey.setKey(InputConstants.Type.KEYSYM.getOrCreate(k));
            KeyMapping.resetMapping();
            assert minecraft!=null; minecraft.options.save();
            capturingKey=false; return true;
        }
        if(k==GLFW.GLFW_KEY_ESCAPE) { if(pickerTarget!=null){pickerTarget=null;return true;} onClose(); return true; }
        if(k==GLFW.GLFW_KEY_ENTER||k==GLFW.GLFW_KEY_KP_ENTER) { addFromInput(); return true; }
        int size=mobMode?mobResults.size():blockResults.size();
        if(k==GLFW.GLFW_KEY_UP)   { selectedResult=Math.max(0,selectedResult-1); return true; }
        if(k==GLFW.GLFW_KEY_DOWN) { selectedResult=Math.min(size-1,selectedResult+1); return true; }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() { assert minecraft!=null; minecraft.setScreen(parent); }
}
