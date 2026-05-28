package com.glowmod.gui;

import com.cinemamod.mcef.MCEFBrowser;
import com.cinemamod.mcef.MCEFClient;
import com.mojang.blaze3d.opengl.GlStateManager;
import net.minecraft.client.Minecraft;
import org.cef.browser.CefBrowser;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL21;

import java.awt.Rectangle;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

public class FastBrowser extends MCEFBrowser {

    private enum State { UNINIT, INIT_PENDING, READY, FAILED }

    private volatile State state = State.UNINIT;

    private final int[] pbos = new int[2];
    private int writeIdx = 0;
    private int readIdx = 1;
    private int pboWidth = 0;
    private int pboHeight = 0;
    private int uploadFrame = 0;
    private ByteBuffer copyBuf;

    private final AtomicBoolean uploadScheduled = new AtomicBoolean(false);

    public FastBrowser(MCEFClient client, String url, boolean transparent) {
        super(client, url, transparent);
    }

    @Override
    public void onPaint(CefBrowser browser, boolean popup,
                        Rectangle[] dirtyRects, ByteBuffer buffer, int w, int h) {
        if (popup || state == State.FAILED) {
            super.onPaint(browser, popup, dirtyRects, buffer, w, h);
            return;
        }

        if (state == State.READY && (w != pboWidth || h != pboHeight)) {
            state = State.UNINIT;
            uploadScheduled.set(false);
        }

        if (state != State.READY) {
            super.onPaint(browser, popup, dirtyRects, buffer, w, h);
            if (state == State.UNINIT) {
                state = State.INIT_PENDING;
                int sw = w, sh = h;
                Minecraft.getInstance().execute(() -> {
                    try {
                        initPbos(sw, sh);
                        state = State.READY;
                    } catch (Throwable t) {
                        System.err.println("[glowdar] PBO init failed, falling back: " + t);
                        state = State.FAILED;
                    }
                });
            }
            return;
        }

        if (!uploadScheduled.compareAndSet(false, true)) {
            return;
        }

        buffer.rewind();
        copyBuf.clear();
        copyBuf.put(buffer);
        copyBuf.flip();

        int fw = w, fh = h;
        Minecraft.getInstance().execute(() -> {
            try {
                uploadPbo(copyBuf, fw, fh);
            } catch (Throwable t) {
                System.err.println("[glowdar] PBO upload failed, reverting: " + t);
                state = State.FAILED;
            } finally {
                uploadScheduled.set(false);
            }
        });
    }

    private void initPbos(int w, int h) {
        String vendor = GL11.glGetString(GL11.GL_VENDOR);
        if (vendor != null && vendor.contains("Apple")) {
            throw new RuntimeException("GL_PIXEL_UNPACK_BUFFER not supported on Apple Metal OpenGL (gldCopyBufferSubData unimplemented)");
        }
        if (pboWidth > 0) {
            GL15.glDeleteBuffers(pbos);
        }
        GL15.glGenBuffers(pbos);
        long size = (long) w * h * 4;
        for (int pbo : pbos) {
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, pbo);
            GL15.glBufferData(GL21.GL_PIXEL_UNPACK_BUFFER, size, GL15.GL_STREAM_DRAW);
        }
        GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0);
        copyBuf = ByteBuffer.allocateDirect((int) size);
        pboWidth = w;
        pboHeight = h;
        uploadFrame = 0;
        writeIdx = 0;
        readIdx = 1;
    }

    private void uploadPbo(ByteBuffer pixels, int w, int h) {
        if (w != pboWidth || h != pboHeight) return;
        int texId = getRenderer().getTextureID();
        if (texId <= 0) return;

        GlStateManager._bindTexture(texId);

        if (uploadFrame > 0) {
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, pbos[readIdx]);
            GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, w, h,
                    GL12.GL_BGRA, GL11.GL_UNSIGNED_BYTE, 0L);
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0);
        }

        GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, pbos[writeIdx]);
        ByteBuffer mapped = GL15.glMapBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, GL15.GL_WRITE_ONLY);
        if (mapped != null) {
            pixels.rewind();
            mapped.put(pixels);
            GL15.glUnmapBuffer(GL21.GL_PIXEL_UNPACK_BUFFER);
        }
        GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0);

        GlStateManager._bindTexture(0);

        int tmp = writeIdx;
        writeIdx = readIdx;
        readIdx = tmp;
        uploadFrame++;
    }
}
