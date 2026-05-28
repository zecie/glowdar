package com.glowmod.gui;

import org.cef.callback.CefCommandLine;
import org.cef.handler.CefAppHandlerAdapter;

public class FastCefAppHandler extends CefAppHandlerAdapter {

    public FastCefAppHandler() {
        super(null);
    }

    @Override
    public void onBeforeCommandLineProcessing(String processType, CefCommandLine commandLine) {
        if (processType.isEmpty()) {
            commandLine.appendSwitchWithValue("off-screen-frame-rate", "240");
        }
    }
}
