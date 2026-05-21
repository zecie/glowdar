package com.glowmod.render;

import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;

public class GlowGuiRenderer {

    private static int prog        = -1;
    private static int vao         = -1;
    private static int vbo         = -1;

    private static int uRes, uColor, uRectPos, uRectSize, uRadius, uShadow, uShadowAlpha;

    private static final String VERT =
        "#version 150\n" +
        "in vec2 Pos;\n" +
        "out vec2 vPos;\n" +
        "uniform vec2 uRes;\n" +
        "void main() {\n" +
        "  vPos = Pos;\n" +
        "  vec2 ndc = Pos / uRes * 2.0 - 1.0;\n" +
        "  gl_Position = vec4(ndc.x, -ndc.y, 0.0, 1.0);\n" +
        "}\n";

    private static final String FRAG =
        "#version 150\n" +
        "in vec2 vPos;\n" +
        "out vec4 fragColor;\n" +
        "uniform vec4 uColor;\n" +
        "uniform vec2 uRectPos;\n" +
        "uniform vec2 uRectSize;\n" +
        "uniform float uRadius;\n" +
        "uniform float uShadow;\n" +
        "uniform float uShadowAlpha;\n" +
        "float sdf(vec2 p, vec2 b, float r) {\n" +
        "  vec2 q = abs(p) - b + r;\n" +
        "  return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;\n" +
        "}\n" +
        "void main() {\n" +
        "  vec2 c = vPos - uRectPos - uRectSize * 0.5;\n" +
        "  float d = sdf(c, uRectSize * 0.5, uRadius);\n" +
        "  float fill = 1.0 - smoothstep(-1.0, 0.0, d);\n" +
        "  vec4 col = vec4(uColor.rgb, uColor.a * fill);\n" +
        "  if (uShadow > 0.0) {\n" +
        "    vec2 sc = c - vec2(0.0, uShadow * 0.25);\n" +
        "    float sd = sdf(sc, uRectSize * 0.5 + uShadow * 0.5, uRadius + uShadow * 0.3);\n" +
        "    float sf = (1.0 - smoothstep(-uShadow, uShadow, sd)) * (1.0 - fill) * uShadowAlpha;\n" +
        "    col = mix(vec4(0.0, 0.0, 0.0, sf), col, col.a);\n" +
        "  }\n" +
        "  fragColor = col;\n" +
        "}\n";

    private static void init() {
        if (prog != -1) return;

        int v = compile(GL20.GL_VERTEX_SHADER, VERT);
        int f = compile(GL20.GL_FRAGMENT_SHADER, FRAG);

        prog = GL20.glCreateProgram();
        GL20.glAttachShader(prog, v);
        GL20.glAttachShader(prog, f);
        GL20.glBindAttribLocation(prog, 0, "Pos");
        GL20.glLinkProgram(prog);
        if (GL20.glGetProgrami(prog, GL20.GL_LINK_STATUS) == GL11.GL_FALSE)
            throw new RuntimeException("GlowGuiRenderer link: " + GL20.glGetProgramInfoLog(prog));
        GL20.glDeleteShader(v);
        GL20.glDeleteShader(f);

        uRes         = GL20.glGetUniformLocation(prog, "uRes");
        uColor       = GL20.glGetUniformLocation(prog, "uColor");
        uRectPos     = GL20.glGetUniformLocation(prog, "uRectPos");
        uRectSize    = GL20.glGetUniformLocation(prog, "uRectSize");
        uRadius      = GL20.glGetUniformLocation(prog, "uRadius");
        uShadow      = GL20.glGetUniformLocation(prog, "uShadow");
        uShadowAlpha = GL20.glGetUniformLocation(prog, "uShadowAlpha");

        vao = GL30.glGenVertexArrays();
        vbo = GL15.glGenBuffers();
        GL30.glBindVertexArray(vao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, 12 * Float.BYTES, GL15.GL_DYNAMIC_DRAW);
        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(0, 2, GL11.GL_FLOAT, false, 0, 0L);
        GL30.glBindVertexArray(0);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
    }

    private static int compile(int type, String src) {
        int id = GL20.glCreateShader(type);
        GL20.glShaderSource(id, src);
        GL20.glCompileShader(id);
        if (GL20.glGetShaderi(id, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE)
            throw new RuntimeException("GlowGuiRenderer shader compile: " + GL20.glGetShaderInfoLog(id));
        return id;
    }

    public static void rect(float x, float y, float w, float h, float r, int argb, float shadow) {
        init();

        Minecraft mc = Minecraft.getInstance();
        float sw = (float) mc.getWindow().getGuiScaledWidth();
        float sh = (float) mc.getWindow().getGuiScaledHeight();

        float pad = shadow + r + 2;
        float qx = x - pad, qy = y - pad, qw = w + pad * 2, qh = h + pad * 2;

        FloatBuffer buf = MemoryUtil.memAllocFloat(12);
        buf.put(qx     ).put(qy     )
           .put(qx + qw).put(qy     )
           .put(qx + qw).put(qy + qh)
           .put(qx + qw).put(qy + qh)
           .put(qx     ).put(qy + qh)
           .put(qx     ).put(qy     );
        buf.flip();

        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, 0, buf);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        MemoryUtil.memFree(buf);

        int prevProg = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        boolean depthWasEnabled = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        GL20.glUseProgram(prog);
        GL20.glUniform2f(uRes,      sw, sh);
        GL20.glUniform2f(uRectPos,  x, y);
        GL20.glUniform2f(uRectSize, w, h);
        GL20.glUniform1f(uRadius,   r);
        GL20.glUniform1f(uShadow,   shadow);
        GL20.glUniform1f(uShadowAlpha, 0.55f);
        GL20.glUniform4f(uColor,
            ((argb >> 16) & 0xFF) / 255f,
            ((argb >>  8) & 0xFF) / 255f,
            ( argb        & 0xFF) / 255f,
            ((argb >> 24) & 0xFF) / 255f);

        GL30.glBindVertexArray(vao);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);
        GL30.glBindVertexArray(0);

        GL20.glUseProgram(prevProg);
        if (depthWasEnabled) GL11.glEnable(GL11.GL_DEPTH_TEST);
    }
}
