package com.glowmod.gui;

import com.cinemamod.mcef.MCEF;
import com.cinemamod.mcef.MCEFBrowser;
import org.cef.CefApp;

public final class BrowserHost {

    private static MCEFBrowser browser;
    private static boolean creating = false;
    private static int width = 1280;
    private static int height = 720;

    private BrowserHost() {}

    public static void init() {
        try {
            CefApp.addAppHandler(new FastCefAppHandler());
        } catch (IllegalStateException ignored) {}

        MCEF.scheduleForInit(success -> {
            if (!success) {
                System.err.println("[glowdar] MCEF init failed; browser will not be created");
                return;
            }
            try {
                MCEF.getSettings().setBrowserPreloadEnabled(false);
            } catch (Throwable t) {
                System.err.println("[glowdar] could not disable preload: " + t.getMessage());
            }
            try {
                WebAssets.ensureExtracted();
            } catch (Throwable t) {
                System.err.println("[glowdar] WebAssets extraction failed at startup");
                t.printStackTrace();
            }
            ConfigBridge.registerHandlers();
            createAsync();
        });
    }

    private static synchronized void createAsync() {
        if (browser != null || creating) return;
        creating = true;
        try {
            String url = WebAssets.indexUrl();
            browser = new FastBrowser(MCEF.getClient(), url, true);
            browser.createImmediately();
            browser.resize(width, height);
            ConfigBridge.attach(browser);
            System.out.println("[glowdar] browser ready at " + url);
        } catch (Throwable t) {
            System.err.println("[glowdar] failed to create browser");
            t.printStackTrace();
            browser = null;
        } finally {
            creating = false;
        }
    }

    public static MCEFBrowser get() {
        if (browser == null && MCEF.isInitialized()) {
            createAsync();
        }
        return browser;
    }

    public static boolean isReady() {
        return browser != null && browser.isTextureReady();
    }

    public static void resize(int w, int h) {
        width = Math.max(1, w);
        height = Math.max(1, h);
        if (browser != null) browser.resize(width, height);
    }

}
