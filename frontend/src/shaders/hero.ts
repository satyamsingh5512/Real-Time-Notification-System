/**
 * Hero fragment shader — a slow, technical "infrastructure light field".
 *
 * Design intent: reads as data moving through a system (soft volumetric gradient plus a
 * faint flowing mesh), NOT as a game. Everything is low-amplitude and low-frequency so
 * it stays legible behind text and never becomes visual noise.
 *
 * Uniforms:
 *   uTime       seconds since start (frozen when paused / reduced-motion)
 *   uResolution viewport in CSS pixels
 *   uMouse      normalized pointer (-1..1), smoothed on the CPU side
 *   uIntensity  0 when reduced-motion (renders a still, well-composed frame)
 */
export const heroVertexShader = /* glsl */ `
precision highp float;

attribute vec2 aPosition;

void main() {
  gl_Position = vec4(aPosition, 0.0, 1.0);
}
`;

export const heroFragmentShader = /* glsl */ `
precision highp float;

uniform vec2  uResolution;
uniform float uTime;
uniform vec2  uMouse;
uniform float uIntensity;

float hash(vec2 p) {
  return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
}

float noise(vec2 p) {
  vec2 i = floor(p);
  vec2 f = fract(p);
  vec2 u = f * f * (3.0 - 2.0 * f);

  float a = hash(i);
  float b = hash(i + vec2(1.0, 0.0));
  float c = hash(i + vec2(0.0, 1.0));
  float d = hash(i + vec2(1.0, 1.0));

  return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

float fbm(vec2 p) {
  float value = 0.0;
  float amplitude = 0.5;
  for (int i = 0; i < 4; i++) {
    value += amplitude * noise(p);
    p *= 2.02;
    amplitude *= 0.5;
  }
  return value;
}

void main() {
  vec2 uv = gl_FragCoord.xy / uResolution.xy;
  vec2 p = uv;
  p.x *= uResolution.x / uResolution.y;

  float t = uTime * 0.045;

  // Two counter-drifting fbm layers read as slowly moving infrastructure.
  float flowA = fbm(p * 2.1 + vec2(t, -t * 0.6));
  float flowB = fbm(p * 3.4 + vec2(-t * 0.8, t * 0.35));

  // Soft radial falloff keeps the centre bright and the edges calm.
  float radial = 1.0 - smoothstep(0.15, 1.15, length((uv - 0.5) * vec2(1.25, 1.0)));

  // Pointer parallax: a barely-perceptible bias toward the cursor.
  vec2 parallax = uMouse * 0.035;
  float depth = fbm(p * 1.6 + vec2(t * 0.4, t * 0.2) + parallax);

  vec3 base    = vec3(0.043, 0.063, 0.110);
  vec3 mid     = vec3(0.106, 0.208, 0.361);
  vec3 accent  = vec3(0.235, 0.435, 0.635);

  vec3 color = mix(base, mid, radial * (0.55 + 0.45 * flowA));
  color = mix(color, accent, depth * radial * 0.42);

  // A thin high-frequency band suggests structured data without becoming noise.
  float band = smoothstep(0.86, 1.0, flowB) * radial;
  color += vec3(0.08, 0.14, 0.20) * band;

  // Dithering prevents visible banding on large smooth gradients.
  float dither = (hash(gl_FragCoord.xy) - 0.5) / 255.0;
  color += dither;

  gl_FragColor = vec4(color, 1.0) * mix(0.82, 1.0, uIntensity);
}
`;

/**
 * Static fallback gradient (CSS, not GLSL). Shown when WebGL is unavailable, while the
 * context is still compiling, or when reduced motion asks for a still frame.
 */
export const heroFallbackGradient =
  'radial-gradient(120% 90% at 50% 0%, rgb(27 53 92) 0%, rgb(11 16 28) 55%, rgb(7 10 18) 100%)';