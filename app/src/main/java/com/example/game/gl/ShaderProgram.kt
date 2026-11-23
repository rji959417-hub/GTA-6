package com.example.game.gl

import android.opengl.GLES20
import android.util.Log

class ShaderProgram(vertexSrc: String, fragmentSrc: String) {
  val programId: Int

  init {
    val vs = compileShader(GLES20.GL_VERTEX_SHADER, vertexSrc)
    val fs = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSrc)
    var linkedId = 0
    if (vs != 0 && fs != 0) {
      val candidate = GLES20.glCreateProgram()
      if (candidate != 0) {
        GLES20.glAttachShader(candidate, vs)
        GLES20.glAttachShader(candidate, fs)
        // Explicit universal ES 2.0/3.0 attribute locations matching Mesh.kt layout
        GLES20.glBindAttribLocation(candidate, 0, "aPos")
        GLES20.glBindAttribLocation(candidate, 1, "aNormal")
        GLES20.glBindAttribLocation(candidate, 2, "aUv")
        GLES20.glLinkProgram(candidate)

        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(candidate, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
          val log = GLES20.glGetProgramInfoLog(candidate)
          Log.e("ViceShader", "Link error: $log")
          GLES20.glDeleteProgram(candidate)
        } else {
          linkedId = candidate
        }
      }
    }
    programId = linkedId
    if (vs != 0) GLES20.glDeleteShader(vs)
    if (fs != 0) GLES20.glDeleteShader(fs)
  }

  fun use() {
    if (programId != 0) GLES20.glUseProgram(programId)
  }

  fun loc(name: String): Int =
    if (programId != 0) GLES20.glGetUniformLocation(programId, name) else -1

  fun release() {
    if (programId != 0) GLES20.glDeleteProgram(programId)
  }

  private fun compileShader(type: Int, src: String): Int {
    val shader = GLES20.glCreateShader(type)
    if (shader == 0) return 0
    GLES20.glShaderSource(shader, src)
    GLES20.glCompileShader(shader)
    val status = IntArray(1)
    GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
    if (status[0] == 0) {
      Log.e("ViceShader", "Compile error ($type): ${GLES20.glGetShaderInfoLog(shader)}")
      GLES20.glDeleteShader(shader)
      return 0
    }
    return shader
  }
}

object ViceShaders {
  const val SHADOW_VERTEX =
    """attribute vec3 aPos;
uniform mat4 uLightMvp;
uniform float uTime;
uniform float uWind;
void main() {
  vec3 pos = aPos;
  if (uWind > 0.0) {
    float sway = sin(uTime * 2.1 + pos.z * 1.4) * 0.08 * uWind * clamp(pos.z * 0.4, 0.0, 1.0);
    pos.x += sway;
    pos.y += sway * 0.35;
  }
  gl_Position = uLightMvp * vec4(pos, 1.0);
}
"""

  const val SHADOW_FRAGMENT =
    """precision mediump float;
void main() {
  gl_FragColor = vec4(1.0);
}
"""

  const val SKY_VERTEX =
    """attribute vec3 aPos;
uniform mat4 uMvp;
varying vec3 vDir;
void main() {
  vDir = normalize(aPos);
  vec4 p = uMvp * vec4(aPos, 1.0);
  gl_Position = p.xyww;
}
"""

  const val SKY_FRAGMENT =
    """#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
varying vec3 vDir;
uniform vec3 uSunDir;
uniform vec3 uSkyZenith;
uniform vec3 uSkyHorizon;
uniform vec3 uSunColor;
uniform float uTime;
uniform float uNightFactor;

float hash21(vec2 p) {
  p = fract(p * vec2(123.34, 456.21));
  p += dot(p, p + 45.32);
  return fract(p.x * p.y);
}

float noise2(vec2 p) {
  vec2 i = floor(p);
  vec2 f = fract(p);
  f = f * f * (3.0 - 2.0 * f);
  float a = hash21(i);
  float b = hash21(i + vec2(1.0, 0.0));
  float c = hash21(i + vec2(0.0, 1.0));
  float d = hash21(i + vec2(1.0, 1.0));
  return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

void main() {
  vec3 dir = normalize(vDir);
  float h = clamp(dir.y, 0.0001, 1.0);

  // Smooth atmospheric gradient from horizon glow to deep twilight zenith
  vec3 sky = mix(uSkyHorizon, uSkyZenith, pow(h, 0.55));

  // Soft Sunset Band near horizon toward sun
  float sunDot = max(dot(dir, normalize(uSunDir)), 0.0001);
  sky += uSunColor * pow(sunDot, 5.0) * 0.42;
  float sunDisc = smoothstep(0.9965, 0.9988, sunDot);
  sky = mix(sky, uSunColor * 1.6 + vec3(0.45), sunDisc * (1.0 - uNightFactor * 0.7));

  // Procedural Vice Clouds
  if (dir.y > 0.03) {
    vec2 cloudUv = dir.xz / (dir.y + 0.18) * 1.4 + vec2(uTime * 0.012, uTime * 0.005);
    float n = noise2(cloudUv) * 0.6 + noise2(cloudUv * 2.3) * 0.4;
    float cloudMask = smoothstep(0.52, 0.80, n) * smoothstep(0.03, 0.24, dir.y);
    vec3 cloudLit = mix(uSkyZenith * 1.35, uSunColor * 1.25 + vec3(0.18), pow(sunDot, 2.0) * 0.8 + 0.2);
    sky = mix(sky, cloudLit, cloudMask * 0.55);
  }

  // Twinkling stars at Neon Night
  if (uNightFactor > 0.15 && dir.y > 0.08) {
    vec2 starGrid = floor(dir.xz / (dir.y + 0.25) * 95.0);
    float s = hash21(starGrid);
    if (s > 0.992) {
      float twinkle = 0.65 + 0.35 * sin(uTime * 3.0 + s * 60.0);
      sky += vec3(0.9, 0.95, 1.0) * uNightFactor * twinkle * smoothstep(0.08, 0.35, dir.y);
    }
  }

  gl_FragColor = vec4(sky, 1.0);
}
"""

  const val WORLD_VERTEX =
    """attribute vec3 aPos;
attribute vec3 aNormal;
attribute vec2 aUv;

uniform mat4 uModel;
uniform mat4 uViewProj;
uniform mat4 uLightViewProj;
uniform float uTime;
uniform float uWind;

varying vec3 vWorldPos;
varying vec3 vWorldNormal;
varying vec2 vUv;
varying vec4 vShadowCoord;

void main() {
  vec3 pos = aPos;
  if (uWind > 0.0) {
    float sway = sin(uTime * 2.1 + pos.z * 1.4) * 0.08 * uWind * clamp(pos.z * 0.4, 0.0, 1.0);
    pos.x += sway;
    pos.y += sway * 0.35;
  }
  vec4 wp = uModel * vec4(pos, 1.0);
  vWorldPos = wp.xyz;
  vec3 worldN = uModel[0].xyz * aNormal.x + uModel[1].xyz * aNormal.y + uModel[2].xyz * aNormal.z;
  vWorldNormal = normalize(worldN);
  vUv = aUv;
  vShadowCoord = uLightViewProj * wp;
  gl_Position = uViewProj * wp;
}
"""

  const val WORLD_FRAGMENT =
    """#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif

varying vec3 vWorldPos;
varying vec3 vWorldNormal;
varying vec2 vUv;
varying vec4 vShadowCoord;

uniform vec3 uBaseColor;
uniform int uMaterialType;
uniform float uEmissive;
uniform float uMetallic;
uniform float uRoughness;
uniform float uAlpha;
uniform float uTime;

uniform vec3 uCamPos;
uniform vec3 uSunDir;
uniform vec3 uSunColor;
uniform vec3 uSkyZenith;
uniform vec3 uSkyHorizon;
uniform float uNightFactor;

uniform vec3 uHeadlightPos;
uniform vec3 uHeadlightDir;
uniform float uHeadlightOn;

uniform sampler2D uShadowMap;
uniform float uHasShadowMap;

float hash21(vec2 p) {
  p = fract(p * vec2(127.1, 311.7));
  p += dot(p, p + 34.23);
  return fract(p.x * p.y);
}

float noise2(vec2 p) {
  vec2 i = floor(p);
  vec2 f = fract(p);
  f = f * f * (3.0 - 2.0 * f);
  return mix(
    mix(hash21(i), hash21(i + vec2(1.0, 0.0)), f.x),
    mix(hash21(i + vec2(0.0, 1.0)), hash21(i + vec2(1.0, 1.0)), f.x),
    f.y
  );
}

float computeShadow(vec4 shadowCoord, vec3 n, vec3 l) {
  if (uHasShadowMap < 0.5) return 1.0;
  vec3 proj = shadowCoord.xyz / shadowCoord.w;
  proj = proj * 0.5 + 0.5;
  if (proj.z > 1.0 || proj.x < 0.0 || proj.x > 1.0 || proj.y < 0.0 || proj.y > 1.0) {
    return 1.0;
  }
  float bias = max(0.0028 * (1.0 - dot(n, l)), 0.0009);
  vec2 texel = vec2(1.0 / 1024.0);
  float lit = 0.0;
  float d0 = texture2D(uShadowMap, proj.xy + vec2(-1.0, -1.0) * texel).r;
  float d1 = texture2D(uShadowMap, proj.xy + vec2(1.0, -1.0) * texel).r;
  float d2 = texture2D(uShadowMap, proj.xy + vec2(-1.0, 1.0) * texel).r;
  float d3 = texture2D(uShadowMap, proj.xy + vec2(1.0, 1.0) * texel).r;
  lit += (proj.z - bias <= d0) ? 1.0 : 0.34;
  lit += (proj.z - bias <= d1) ? 1.0 : 0.34;
  lit += (proj.z - bias <= d2) ? 1.0 : 0.34;
  lit += (proj.z - bias <= d3) ? 1.0 : 0.34;
  return lit * 0.25;
}

vec3 proceduralAlbedo(vec3 base, int matType, vec3 wp, vec3 wn) {
  vec2 worldXZ = wp.xz;
  if (matType == 0) {
    float n1 = noise2(worldXZ * 0.45);
    float n2 = noise2(worldXZ * 2.2);
    return base * (0.82 + n1 * 0.26 + n2 * 0.12);
  } else if (matType == 1) {
    float grain = hash21(floor(worldXZ * 18.0));
    float patchN = noise2(worldXZ * 0.35);
    return base * (0.86 + grain * 0.16 + patchN * 0.08);
  } else if (matType == 2) {
    float ripple = sin(worldXZ.y * 4.5 + noise2(worldXZ * 0.8) * 3.0) * 0.04;
    float grain = hash21(floor(worldXZ * 14.0)) * 0.06;
    return base + vec3(ripple + grain);
  } else if (matType == 3) {
    vec2 wallUv = abs(wn.y) > 0.6 ? wp.xz : (abs(wn.x) > 0.6 ? wp.zy : wp.xy);
    float micro = noise2(wallUv * 6.0);
    return base * (0.93 + micro * 0.10);
  } else if (matType == 4) {
    vec2 pUv = abs(wn.y) > 0.6 ? wp.xz : wp.xy;
    float plank = sin(pUv.x * 11.0 + noise2(pUv * 1.8) * 1.4);
    float seam = smoothstep(0.92, 1.0, abs(fract(pUv.x * 2.5) - 0.5) * 2.0);
    return base * (0.88 + plank * 0.08) * (1.0 - seam * 0.28);
  } else if (matType == 5) {
    vec2 g = abs(fract(worldXZ * 0.85) - 0.5);
    float grout = smoothstep(0.46, 0.50, max(g.x, g.y));
    return mix(base, base * 0.72, grout * 0.65);
  } else if (matType == 7) {
    float leaf = noise2(wp.xz * 2.5 + wp.y);
    return base * (0.85 + leaf * 0.30);
  } else if (matType == 13) {
    float weave = sin(vUv.x * 90.0) * sin(vUv.y * 90.0) * 0.018 + (noise2(vUv * 18.0) - 0.5) * 0.03;
    return base * (1.0 + weave);
  }
  return base;
}

void main() {
  vec3 N = normalize(vWorldNormal);
  vec3 V = normalize(uCamPos - vWorldPos);
  vec3 L = normalize(uSunDir);

  vec3 albedo = proceduralAlbedo(uBaseColor, uMaterialType, vWorldPos, N);

  if (uEmissive > 0.01) {
    vec3 glow = albedo * (1.0 + uEmissive * 1.4);
    gl_FragColor = vec4(glow, uAlpha);
    return;
  }

  float shadow = computeShadow(vShadowCoord, N, L);

  float hemi = N.y * 0.5 + 0.5;
  vec3 ambientColor = mix(uSkyZenith * 0.34 + vec3(0.08, 0.06, 0.10), uSkyHorizon * 0.52 + vec3(0.18), hemi);
  vec3 ambient = albedo * ambientColor * (1.0 - uNightFactor * 0.42);

  float rawDot = dot(N, L);
  float nDotL = max(rawDot, 0.0);
  vec3 diffuse;
  if (uMaterialType == 12) {
    float wrap = clamp((rawDot + 0.42) / 1.42, 0.0, 1.0);
    float sssBand = smoothstep(-0.35, 0.20, rawDot) * (1.0 - smoothstep(0.08, 0.55, rawDot));
    vec3 skinBounce = albedo * vec3(1.05, 0.56, 0.44) * sssBand * 0.30;
    diffuse = (albedo * uSunColor * wrap * 0.94 + skinBounce) * mix(0.65, 1.0, shadow);
    ambient *= 1.15;
  } else {
    diffuse = albedo * uSunColor * nDotL * shadow;
  }

  vec3 H = normalize(L + V);
  float shininess = mix(128.0, 14.0, clamp(uRoughness, 0.05, 1.0));
  float specPow = pow(max(dot(N, H), 0.0001), shininess);
  float specStrength = (uMaterialType == 12) ? 0.34 : mix(0.25, 1.15, uMetallic);
  vec3 specular = uSunColor * specPow * specStrength * shadow * (1.0 - uRoughness * 0.65);

  float fresnel = pow(max(1.0 - max(dot(N, V), 0.0), 0.0001), 3.5);
  float envStrength = (uMaterialType == 12) ? 0.20 : ((uMaterialType == 13) ? 0.24 : (uMetallic * 0.65 + (uMaterialType == 10 ? 0.85 : 0.10)));
  vec3 envReflect = mix(uSkyHorizon, uSkyZenith, 0.35) * fresnel * envStrength;

  vec3 headlightAdd = vec3(0.0);
  if (uHeadlightOn > 0.5) {
    vec3 toFrag = vWorldPos - uHeadlightPos;
    float dist = length(toFrag);
    if (dist < 28.0 && dist > 0.3) {
      vec3 dirToFrag = toFrag / dist;
      float cone = smoothstep(0.62, 0.94, dot(dirToFrag, normalize(uHeadlightDir)));
      float atten = 1.0 / (1.0 + 0.04 * dist + 0.014 * dist * dist);
      float beam = cone * atten * max(dot(N, -dirToFrag), 0.15);
      headlightAdd = albedo * vec3(0.75, 0.92, 1.0) * beam * (1.2 + uNightFactor * 1.4);
    }
  }

  vec3 color = ambient + diffuse + specular + envReflect + headlightAdd;

  float distCam = length(uCamPos - vWorldPos);
  float fogD = distCam * 0.0046;
  float fog = 1.0 - exp(-(fogD * fogD));
  vec3 fogColor = mix(uSkyHorizon * 0.85, uSkyZenith * 0.75, 0.25);
  color = mix(color, fogColor, clamp(fog, 0.0, 0.82));

  gl_FragColor = vec4(color, uAlpha);
}
"""

  const val WATER_VERTEX =
    """attribute vec3 aPos;
attribute vec3 aNormal;
attribute vec2 aUv;

uniform mat4 uModel;
uniform mat4 uViewProj;
uniform float uTime;
uniform float uWaveAmp;

varying vec3 vWorldPos;
varying vec2 vUv;

void main() {
  vec4 wp = uModel * vec4(aPos, 1.0);
  float w1 = sin(wp.x * 0.22 + uTime * 1.8) * cos(wp.z * 0.18 + uTime * 1.4);
  float w2 = sin((wp.x + wp.z) * 0.45 - uTime * 2.4);
  wp.y += (w1 * 0.14 + w2 * 0.06) * uWaveAmp;
  vWorldPos = wp.xyz;
  vUv = aUv;
  gl_Position = uViewProj * wp;
}
"""

  const val WATER_FRAGMENT =
    """#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif

varying vec3 vWorldPos;
varying vec2 vUv;

uniform float uTime;
uniform vec3 uCamPos;
uniform vec3 uSunDir;
uniform vec3 uSunColor;
uniform vec3 uSkyZenith;
uniform vec3 uSkyHorizon;
uniform vec3 uWaterTint;
uniform float uIsOcean;

void main() {
  float nx = cos(vWorldPos.x * 0.55 + uTime * 2.1) * 0.14 + sin((vWorldPos.x - vWorldPos.z) * 1.2 + uTime * 2.8) * 0.08;
  float nz = sin(vWorldPos.z * 0.48 + uTime * 1.7) * 0.14 + cos((vWorldPos.x + vWorldPos.z) * 1.1 - uTime * 2.3) * 0.08;
  vec3 N = normalize(vec3(nx, 1.0, nz));
  vec3 V = normalize(uCamPos - vWorldPos);
  vec3 L = normalize(uSunDir);

  float fresnel = pow(max(1.0 - max(dot(N, V), 0.0), 0.0001), 4.0);
  vec3 skyReflect = mix(uSkyHorizon, uSkyZenith, 0.35);

  vec3 baseWater = uWaterTint;
  if (uIsOcean > 0.5) {
    float distIsland = max(abs(vWorldPos.x) - 64.0, abs(vWorldPos.z) - 65.0);
    float shallow = 1.0 - smoothstep(0.0, 28.0, max(distIsland, 0.0));
    baseWater = mix(vec3(0.04, 0.18, 0.34), vec3(0.08, 0.62, 0.72), shallow);
  }

  vec3 H = normalize(L + V);
  float sparkle = pow(max(dot(N, H), 0.0001), 160.0) * 1.8;
  vec3 color = mix(baseWater, skyReflect, clamp(fresnel * 0.85 + 0.12, 0.0, 0.92));
  color += uSunColor * sparkle;

  float distCam = length(uCamPos - vWorldPos);
  float fogD = distCam * 0.0046;
  float fog = 1.0 - exp(-(fogD * fogD));
  color = mix(color, mix(uSkyHorizon * 0.85, uSkyZenith * 0.75, 0.25), clamp(fog, 0.0, 0.88));

  gl_FragColor = vec4(color, uIsOcean > 0.5 ? 0.94 : 0.84);
}
"""
}
