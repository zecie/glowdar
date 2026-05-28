#version 330

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform BlurConfig {
    vec2 BlurDir;
    float Radius;
};

uniform sampler2D InSampler;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec2 texel = 1.0 / InSize;
    vec2 step = texel * BlurDir;
    float sigma = 2.2;
    float r = 5.0;
    vec4 acc = vec4(0.0);
    float total = 0.0;
    for (float i = -r; i <= r; i += 1.0) {
        float w = exp(-(i * i) / (2.0 * sigma * sigma));
        acc += texture(InSampler, texCoord + step * i) * w;
        total += w;
    }
    fragColor = acc / total;
}
