package com.glowmod.render;

import org.joml.Matrix4f;

public final class FrameData {

    public static final Matrix4f projection = new Matrix4f();
    public static final Matrix4f modelView  = new Matrix4f();

    public static double camX, camY, camZ;
    public static float  partialTick;
    public static boolean valid = false;

    private FrameData() {}

    public static void update(Matrix4f proj, Matrix4f mv, double cx, double cy, double cz, float pt) {
        projection.set(proj);
        modelView.set(mv);
        camX = cx; camY = cy; camZ = cz;
        partialTick = pt;
        valid = true;
    }

    public static boolean isValid() { return valid; }
}
