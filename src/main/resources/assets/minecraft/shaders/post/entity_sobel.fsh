#version 330

uniform sampler2D InSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec4 s = texture(InSampler, texCoord);
    if (s.a < 0.01) {
        fragColor = vec4(0.0);
        return;
    }
    fragColor = vec4(s.rgb, min(s.a * 1.6, 1.0));
}
