#version 150

// Grabado de imprenta: las luces se quedan en papel, los medios tonos se traman en diagonal, las sombras van en tinta y
// el borde de la figura lleva un contorno de tinta.

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;

in vec2 texCoord0;

out vec4 fragColor;

float alphaAt(vec2 uv) {
    return texture(Sampler0, uv).a;
}

void main() {
    vec4 color = texture(Sampler0, texCoord0);
    vec2 texel = 1.0 / vec2(textureSize(Sampler0, 0));
    float edge = 0.0;
    if (color.a < 0.1) {
        // fuera de la figura: solo el contorno
        float near = max(max(alphaAt(texCoord0 + vec2(texel.x, 0.0)), alphaAt(texCoord0 - vec2(texel.x, 0.0))),
                         max(alphaAt(texCoord0 + vec2(0.0, texel.y)), alphaAt(texCoord0 - vec2(0.0, texel.y))));
        if (near < 0.1) {
            discard;
        }
        edge = 1.0;
    }
    float light = dot(color.rgb, vec3(0.299, 0.587, 0.114));
    vec2 p = gl_FragCoord.xy;
    float hatchA = mod(p.x + p.y, 4.0) < 1.5 ? 1.0 : 0.0;
    float hatchB = mod(p.x - p.y, 4.0) < 1.5 ? 1.0 : 0.0;
    float ink;
    if (edge > 0.5) {
        ink = 1.0;
    } else if (light > 0.70) {
        ink = 0.0;
    } else if (light > 0.48) {
        ink = hatchA * 0.85;
    } else if (light > 0.28) {
        ink = max(hatchA, hatchB);
    } else {
        ink = 1.0;
    }
    vec3 paper = vec3(0.87, 0.86, 0.83);
    vec3 tinta = vec3(0.10, 0.10, 0.10);
    fragColor = vec4(mix(paper, tinta, ink), 1.0) * ColorModulator;
}
