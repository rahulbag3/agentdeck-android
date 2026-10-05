import * as THREE from 'three';
import { fbm2D, makeValueNoise2D, clamp01, lerp } from './utils.js';

/**
 * Procedural PBR texture factory.
 *
 * Every surface in the game is authored here at runtime on 2D canvases, which
 * produces albedo + roughness + normal maps without shipping any binary art.
 * Texture sets are cached by key and shared between materials.
 */

const cache = new Map();

const makeCanvas = (size) => {
  const c = document.createElement('canvas');
  c.width = size;
  c.height = size;
  return c;
};

const finish = (canvas, { repeat = 1, srgb = false, aniso = 8 } = {}) => {
  const tex = new THREE.CanvasTexture(canvas);
  tex.wrapS = tex.wrapT = THREE.RepeatWrapping;
  tex.repeat.set(repeat, repeat);
  tex.colorSpace = srgb ? THREE.SRGBColorSpace : THREE.NoColorSpace;
  tex.anisotropy = aniso;
  tex.needsUpdate = true;
  return tex;
};

/**
 * Derive a tangent-space normal map from a height field via Sobel filtering.
 * `height` is a Float32Array of size*size in 0..1.
 */
const heightToNormal = (height, size, strength = 2.0) => {
  const canvas = makeCanvas(size);
  const ctx = canvas.getContext('2d');
  const img = ctx.createImageData(size, size);
  const at = (x, y) =>
    height[(((y % size) + size) % size) * size + (((x % size) + size) % size)];

  for (let y = 0; y < size; y++) {
    for (let x = 0; x < size; x++) {
      const tl = at(x - 1, y - 1), t = at(x, y - 1), tr = at(x + 1, y - 1);
      const l = at(x - 1, y), r = at(x + 1, y);
      const bl = at(x - 1, y + 1), b = at(x, y + 1), br = at(x + 1, y + 1);

      const dx = tl + 2 * l + bl - (tr + 2 * r + br);
      const dy = tl + 2 * t + tr - (bl + 2 * b + br);

      let nx = dx * strength;
      let ny = dy * strength;
      const len = Math.hypot(nx, ny, 1) || 1;
      nx /= len;
      ny /= len;

      const i = (y * size + x) * 4;
      img.data[i] = (nx * 0.5 + 0.5) * 255;
      img.data[i + 1] = (ny * 0.5 + 0.5) * 255;
      img.data[i + 2] = (1 / len) * 255;
      img.data[i + 3] = 255;
    }
  }
  ctx.putImageData(img, 0, 0);
  return canvas;
};

/** Paint an ImageData buffer from a per-pixel callback returning [r,g,b] 0..1. */
const paint = (size, fn) => {
  const canvas = makeCanvas(size);
  const ctx = canvas.getContext('2d');
  const img = ctx.createImageData(size, size);
  const out = [0, 0, 0];
  for (let y = 0; y < size; y++) {
    for (let x = 0; x < size; x++) {
      fn(x, y, out);
      const i = (y * size + x) * 4;
      img.data[i] = clamp01(out[0]) * 255;
      img.data[i + 1] = clamp01(out[1]) * 255;
      img.data[i + 2] = clamp01(out[2]) * 255;
      img.data[i + 3] = 255;
    }
  }
  ctx.putImageData(img, 0, 0);
  return canvas;
};

/** Convenience: a greyscale roughness canvas. */
const roughFrom = (size, fn) =>
  paint(size, (x, y, out) => {
    out[0] = out[1] = out[2] = clamp01(fn(x, y));
  });

/* __GENERATORS__ */
const generators = {
  /** Asphalt with aggregate speckle, tar patches and subtle wear. */
  asphalt(size = 512) {
    const noise = makeValueNoise2D(1337);
    const grain = makeValueNoise2D(4211);
    const albedo = paint(size, (x, y, out) => {
      const u = x / size, v = y / size;
      const base = fbm2D(noise, u * 5, v * 5, 4);
      const spec = grain(x * 0.9, y * 0.9);
      const patch = fbm2D(noise, u * 1.6 + 11, v * 1.6, 3);
      let shade = 0.16 + base * 0.1 + (spec > 0.82 ? 0.16 : 0) + (spec < 0.14 ? -0.045 : 0);
      shade -= patch > 0.66 ? 0.035 : 0;
      out[0] = shade;
      out[1] = shade * 1.02;
      out[2] = shade * 1.09;
    });
    const height = new Float32Array(size * size);
    for (let y = 0; y < size; y++)
      for (let x = 0; x < size; x++)
        height[y * size + x] =
          grain(x * 0.9, y * 0.9) * 0.75 + fbm2D(noise, (x / size) * 6, (y / size) * 6, 3) * 0.25;
    return {
      map: finish(albedo, { srgb: true }),
      roughnessMap: finish(roughFrom(size, (x, y) =>
        0.94 - grain(x * 0.9, y * 0.9) * 0.22 + fbm2D(noise, (x / size) * 5, (y / size) * 5, 4) * 0.06)),
      normalMap: finish(heightToNormal(height, size, 1.5))
    };
  },

  /** Mown grass with clumping, colour variation and mowing stripes. */
  grass(size = 512) {
    const noise = makeValueNoise2D(777);
    const blade = makeValueNoise2D(9091);
    const albedo = paint(size, (x, y, out) => {
      const u = x / size, v = y / size;
      const clump = fbm2D(noise, u * 7, v * 7, 4);
      const streak = blade(x * 1.6, y * 0.5);
      const m = lerp(0.92, 1.08, Math.sin(u * Math.PI * 2 * 4) * 0.5 + 0.5);
      out[0] = (lerp(0.16, 0.36, clump) + streak * 0.07) * m;
      out[1] = (lerp(0.36, 0.62, clump) + streak * 0.09) * m;
      out[2] = (lerp(0.11, 0.24, clump) + streak * 0.04) * m;
    });
    const height = new Float32Array(size * size);
    for (let y = 0; y < size; y++)
      for (let x = 0; x < size; x++)
        height[y * size + x] =
          blade(x * 1.6, y * 0.5) * 0.7 + fbm2D(noise, (x / size) * 8, (y / size) * 8, 3) * 0.3;
    return {
      map: finish(albedo, { srgb: true }),
      roughnessMap: finish(roughFrom(size, (x, y) =>
        0.82 + fbm2D(noise, (x / size) * 7, (y / size) * 7, 3) * 0.14)),
      normalMap: finish(heightToNormal(height, size, 2.4))
    };
  },

  /** Desert sand: soft dunes plus wind ripples. */
  sand(size = 512) {
    const noise = makeValueNoise2D(2024);
    const ripple = makeValueNoise2D(5150);
    const albedo = paint(size, (x, y, out) => {
      const u = x / size, v = y / size;
      const dune = fbm2D(noise, u * 3, v * 3, 4);
      const rip = Math.sin(u * 26 + fbm2D(noise, u * 2, v * 2, 2) * 7) * 0.5 + 0.5;
      const grit = ripple(x * 1.3, y * 1.3);
      const base = lerp(0.68, 0.86, dune) + rip * 0.05 + grit * 0.04;
      out[0] = base;
      out[1] = base * 0.84;
      out[2] = base * 0.58;
    });
    const height = new Float32Array(size * size);
    for (let y = 0; y < size; y++)
      for (let x = 0; x < size; x++) {
        const u = x / size;
        height[y * size + x] =
          Math.sin(u * 26 + fbm2D(noise, u * 2, (y / size) * 2, 2) * 7) * 0.18 +
          fbm2D(noise, u * 3, (y / size) * 3, 4) * 0.5 +
          ripple(x * 1.3, y * 1.3) * 0.32;
      }
    return {
      map: finish(albedo, { srgb: true }),
      roughnessMap: finish(roughFrom(size, (x, y) =>
        0.86 + fbm2D(noise, (x / size) * 4, (y / size) * 4, 3) * 0.1)),
      normalMap: finish(heightToNormal(height, size, 1.1))
    };
  },

  /** Rough natural stone for cliffs, rocks and canyon walls. */
  rock(size = 512) {
    const noise = makeValueNoise2D(6161);
    const crack = makeValueNoise2D(313);
    const albedo = paint(size, (x, y, out) => {
      const u = x / size, v = y / size;
      const strata = fbm2D(noise, u * 4, v * 9, 5);
      const c = Math.abs(fbm2D(crack, u * 6, v * 6, 3) - 0.5);
      const base = lerp(0.3, 0.62, strata) * (c < 0.05 ? 0.55 : 1);
      out[0] = base;
      out[1] = base * 0.92;
      out[2] = base * 0.82;
    });
    const height = new Float32Array(size * size);
    for (let y = 0; y < size; y++)
      for (let x = 0; x < size; x++)
        height[y * size + x] =
          fbm2D(noise, (x / size) * 4, (y / size) * 9, 5) * 0.7 +
          fbm2D(crack, (x / size) * 6, (y / size) * 6, 3) * 0.3;
    return {
      map: finish(albedo, { srgb: true }),
      roughnessMap: finish(roughFrom(size, (x, y) =>
        0.78 + fbm2D(noise, (x / size) * 5, (y / size) * 5, 4) * 0.18)),
      normalMap: finish(heightToNormal(height, size, 2.8))
    };
  },

  /** Poured concrete with faint panel seams and staining. */
  concrete(size = 512) {
    const noise = makeValueNoise2D(4747);
    const fine = makeValueNoise2D(121);
    const seam = (u, v) => (Math.abs(u - 0.5) < 0.006 || Math.abs(v - 0.5) < 0.006 ? 0.78 : 1);
    const albedo = paint(size, (x, y, out) => {
      const u = x / size, v = y / size;
      const s = lerp(0.55, 0.72, fbm2D(noise, u * 4, v * 4, 4)) *
        lerp(0.95, 1.05, fine(x * 1.1, y * 1.1)) * seam(u, v);
      out[0] = s;
      out[1] = s * 0.99;
      out[2] = s * 0.96;
    });
    const height = new Float32Array(size * size);
    for (let y = 0; y < size; y++)
      for (let x = 0; x < size; x++) {
        const u = x / size, v = y / size;
        height[y * size + x] =
          fbm2D(noise, u * 4, v * 4, 4) * 0.8 + fine(x * 1.1, y * 1.1) * 0.2 * seam(u, v);
      }
    return {
      map: finish(albedo, { srgb: true }),
      roughnessMap: finish(roughFrom(size, (x, y) =>
        0.8 + fbm2D(noise, (x / size) * 4, (y / size) * 4, 3) * 0.16)),
      normalMap: finish(heightToNormal(height, size, 1.3))
    };
  },

  /** Weathered timber planking for docks, bridges and barns. */
  wood(size = 512) {
    const noise = makeValueNoise2D(818);
    const albedo = paint(size, (x, y, out) => {
      const u = x / size, v = y / size;
      const plank = Math.floor(v * 6);
      const inPlank = v * 6 - plank;
      const grain = Math.sin((u * 34 + fbm2D(noise, u * 3 + plank * 13.7, v * 22, 3) * 12)) * 0.5 + 0.5;
      const gap = inPlank < 0.045 ? 0.4 : 1;
      const tone = lerp(0.32, 0.5, grain) * gap * lerp(0.9, 1.08, (plank * 0.37) % 1);
      out[0] = tone;
      out[1] = tone * 0.74;
      out[2] = tone * 0.5;
    });
    const height = new Float32Array(size * size);
    for (let y = 0; y < size; y++)
      for (let x = 0; x < size; x++) {
        const u = x / size, v = y / size;
        const inPlank = v * 6 - Math.floor(v * 6);
        const g = Math.sin((u * 34 + fbm2D(noise, u * 3 + Math.floor(v * 6) * 13.7, v * 22, 3) * 12)) * 0.5 + 0.5;
        height[y * size + x] = g * 0.6 + (inPlank < 0.045 ? 0.1 : 1) * 0.4;
      }
    return {
      map: finish(albedo, { srgb: true }),
      roughnessMap: finish(roughFrom(size, (x, y) =>
        0.85 + fbm2D(noise, (x / size) * 5, (y / size) * 20, 3) * 0.12)),
      normalMap: finish(heightToNormal(height, size, 2.0))
    };
  },

  /** Painted corrugated metal for warehouses, sheds and containers. */
  metal(size = 256) {
    const noise = makeValueNoise2D(3131);
    const corr = (u) => Math.sin(u * Math.PI * 2 * 14) * 0.5 + 0.5;
    const albedo = paint(size, (x, y, out) => {
      const u = x / size, v = y / size;
      const rust = fbm2D(noise, u * 5, v * 5, 4);
      const rustMask = rust > 0.62 ? (rust - 0.62) * 2.2 : 0;
      const shade = lerp(0.44, 0.62, corr(u));
      out[0] = lerp(shade, 0.42, rustMask);
      out[1] = lerp(shade, 0.24, rustMask);
      out[2] = lerp(shade * 1.04, 0.15, rustMask);
    });
    const height = new Float32Array(size * size);
    for (let y = 0; y < size; y++)
      for (let x = 0; x < size; x++) height[y * size + x] = corr(x / size);
    return {
      map: finish(albedo, { srgb: true }),
      roughnessMap: finish(roughFrom(size, (x, y) => {
        const r = fbm2D(noise, (x / size) * 5, (y / size) * 5, 4);
        return lerp(0.34, 0.86, r > 0.62 ? 1 : 0.15);
      })),
      normalMap: finish(heightToNormal(height, size, 1.6))
    };
  },

  /** Start/finish chequer with a printed-paper wear pass. */
  checker(size = 256, cells = 8) {
    const noise = makeValueNoise2D(99);
    const albedo = paint(size, (x, y, out) => {
      const cx = Math.floor((x / size) * cells);
      const cy = Math.floor((y / size) * cells);
      const white = (cx + cy) % 2 === 0;
      const wear = fbm2D(noise, (x / size) * 6, (y / size) * 6, 3);
      const s = white ? lerp(0.88, 0.98, wear) : lerp(0.06, 0.12, wear);
      out[0] = out[1] = out[2] = s;
    });
    return { map: finish(albedo, { srgb: true }) };
  },
  blob(size = 128, power = 2.2) {
    const canvas = makeCanvas(size);
    const ctx = canvas.getContext('2d');
    const img = ctx.createImageData(size, size);
    const c = size / 2;
    for (let y = 0; y < size; y++)
      for (let x = 0; x < size; x++) {
        const d = Math.hypot(x - c, y - c) / c;
        const i = (y * size + x) * 4;
        img.data[i] = img.data[i + 1] = img.data[i + 2] = 255;
        img.data[i + 3] = Math.pow(clamp01(1 - d), power) * 255;
      }
    ctx.putImageData(img, 0, 0);
    const t = new THREE.CanvasTexture(canvas);
    t.colorSpace = THREE.SRGBColorSpace;
    return t;
  },

  /** Flat colour with subtle noise — generic material albedo. */
  plain(size = 64, color = [1, 1, 1], noiseAmount = 0.03) {
    const noise = makeValueNoise2D(4242);
    const albedo = paint(size, (x, y, out) => {
      const n = 1 + (noise(x * 0.8, y * 0.8) - 0.5) * noiseAmount * 2;
      out[0] = color[0] * n;
      out[1] = color[1] * n;
      out[2] = color[2] * n;
    });
    return { map: finish(albedo, { srgb: true }) };
  }
};


/** Get (and memoise) a named texture set. */
export const getTexture = (name, ...args) => {
  const key = `${name}(${args.join(',')})`;
  if (cache.has(key)) return cache.get(key);
  const gen = generators[name];
  if (!gen) throw new Error(`Unknown texture: ${name}`);
  const tex = gen(...args);
  cache.set(key, tex);
  return tex;
};

/** Clone a texture set's maps (textures cannot be shared across repeats). */
const cloneTex = (t, repeat) => {
  if (!t) return null;
  const c = t.clone();
  c.needsUpdate = true;
  if (repeat) c.repeat.set(repeat[0], repeat[1]);
  return c;
};

/** Apply a texture set to an existing material. */
export const applyTextures = (material, tex, repeat) => {
  if (tex.map) material.map = cloneTex(tex.map, repeat);
  if (tex.roughnessMap) material.roughnessMap = cloneTex(tex.roughnessMap, repeat);
  if (tex.normalMap) {
    material.normalMap = cloneTex(tex.normalMap, repeat);
    material.normalScale = new THREE.Vector2(1, 1);
  }
  return material;
};

/** Build a MeshStandardMaterial with procedural maps baked in. */
export const proceduralMaterial = (name, opts = {}) => {
  const tex = getTexture(name, ...(opts.args || []));
  const mat = new THREE.MeshStandardMaterial({
    color: opts.color ?? 0xffffff,
    roughness: opts.roughness ?? 0.85,
    metalness: opts.metalness ?? 0.0,
    ...(opts.extra || {})
  });
  return applyTextures(mat, tex, opts.repeat);
};

export const disposeTextureCache = () => {
  cache.forEach((set) => {
    Object.values(set).forEach((t) => t && t.dispose && t.dispose());
  });
  cache.clear();
};

export { heightToNormal, makeCanvas, paint, finish, generators };