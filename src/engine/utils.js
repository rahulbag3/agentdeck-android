/**
 * Shared math, easing, formatting and random helpers.
 * Kept dependency-free so every subsystem can import it safely.
 */

export const TAU = Math.PI * 2;
export const DEG = Math.PI / 180;
export const RAD = 180 / Math.PI;

export const clamp = (v, min, max) => (v < min ? min : v > max ? max : v);
export const clamp01 = (v) => (v < 0 ? 0 : v > 1 ? 1 : v);
export const lerp = (a, b, t) => a + (b - a) * t;
export const invLerp = (a, b, v) => (b - a === 0 ? 0 : (v - a) / (b - a));
export const remap = (v, a, b, c, d) => lerp(c, d, clamp01(invLerp(a, b, v)));

/** Frame-rate independent exponential smoothing. `lambda` is the rate constant. */
export const damp = (current, target, lambda, dt) =>
  current + (target - current) * (1 - Math.exp(-lambda * dt));

export const moveTowards = (current, target, maxDelta) => {
  const d = target - current;
  if (Math.abs(d) <= maxDelta) return target;
  return current + Math.sign(d) * maxDelta;
};

export const smoothstep = (t) => {
  t = clamp01(t);
  return t * t * (3 - 2 * t);
};

export const smootherstep = (t) => {
  t = clamp01(t);
  return t * t * t * (t * (t * 6 - 15) + 10);
};

export const easeOutCubic = (t) => 1 - Math.pow(1 - clamp01(t), 3);
export const easeInCubic = (t) => Math.pow(clamp01(t), 3);
export const easeInOutCubic = (t) =>
  (t = clamp01(t)) < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
export const easeOutBack = (t) => {
  const c1 = 1.70158;
  const c3 = c1 + 1;
  t = clamp01(t);
  return 1 + c3 * Math.pow(t - 1, 3) + c1 * Math.pow(t - 1, 2);
};

/** Signed shortest angular difference from `a` to `b`, in radians. */
export const angleDelta = (a, b) => {
  let d = (b - a) % TAU;
  if (d > Math.PI) d -= TAU;
  if (d < -Math.PI) d += TAU;
  return d;
};

export const wrapAngle = (a) => {
  a %= TAU;
  if (a > Math.PI) a -= TAU;
  if (a < -Math.PI) a += TAU;
  return a;
};

/** Deterministic PRNG (mulberry32) so procedural content is stable across reloads. */
export const mulberry32 = (seed) => {
  let a = seed >>> 0;
  return function () {
    a = (a + 0x6d2b79f5) >>> 0;
    let t = a;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
};

export const randRange = (rng, min, max) => min + rng() * (max - min);
export const randInt = (rng, min, max) => Math.floor(min + rng() * (max - min + 1));
export const pick = (rng, arr) => arr[Math.floor(rng() * arr.length) % arr.length];

export const wrapIndex = (i, n) => ((i % n) + n) % n;

/** mm:ss.mmm — race timing display. */
export const formatTime = (ms) => {
  if (ms == null || !isFinite(ms) || ms < 0) return '--:--.---';
  const total = Math.floor(ms);
  const m = Math.floor(total / 60000);
  const s = Math.floor((total % 60000) / 1000);
  const t = total % 1000;
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}.${String(t).padStart(3, '0')}`;
};

/** Compact lap delta, e.g. "+1.204" / "-0.412". */
export const formatDelta = (ms) => {
  if (ms == null || !isFinite(ms)) return '';
  const sign = ms >= 0 ? '+' : '-';
  const a = Math.abs(ms);
  const s = Math.floor(a / 1000);
  const t = Math.floor(a % 1000);
  return `${sign}${s}.${String(t).padStart(3, '0')}`;
};

export const ordinal = (n) => {
  const s = ['th', 'st', 'nd', 'rd'];
  const v = n % 100;
  return n + (s[(v - 20) % 10] || s[v] || s[0]);
};

export const capitalize = (s) => (s ? s[0].toUpperCase() + s.slice(1) : s);

/** Thousands separator, e.g. 12500 -> "12,500". */
export const formatCredits = (n) => Math.round(n).toString().replace(/\B(?=(\d{3})+(?!\d))/g, ',');

export const isTouchDevice = () =>
  typeof window !== 'undefined' &&
  ('ontouchstart' in window || (navigator.maxTouchPoints || 0) > 0);

export const isMobile = () =>
  isTouchDevice() ||
  /Android|iPhone|iPad|iPod|Mobile|Silk|Kindle|BlackBerry|Opera Mini|IEMobile/i.test(
    navigator.userAgent
  );

/** Resolve #rrggbb / #rgb into {r,g,b} floats in 0..1. */
export const hexToRgb = (hex) => {
  const h = hex.replace('#', '');
  const n =
    h.length === 3
      ? h
          .split('')
          .map((c) => c + c)
          .join('')
      : h;
  return {
    r: parseInt(n.slice(0, 2), 16) / 255,
    g: parseInt(n.slice(2, 4), 16) / 255,
    b: parseInt(n.slice(4, 6), 16) / 255
  };
};

/** 2D value noise built on a seeded lattice — used by the texture factory. */
export const makeValueNoise2D = (seed = 1) => {
  const rng = mulberry32(seed);
  const size = 256;
  const table = new Float32Array(size * size);
  for (let i = 0; i < table.length; i++) table[i] = rng();
  const at = (x, y) => table[(((y % size) + size) % size) * size + (((x % size) + size) % size)];
  return (x, y) => {
    const xi = Math.floor(x);
    const yi = Math.floor(y);
    const xf = smootherstep(x - xi);
    const yf = smootherstep(y - yi);
    const a = lerp(at(xi, yi), at(xi + 1, yi), xf);
    const b = lerp(at(xi, yi + 1), at(xi + 1, yi + 1), xf);
    return lerp(a, b, yf);
  };
};

/** Fractal Brownian motion over the value noise above. */
export const fbm2D = (noise, x, y, octaves = 4, lacunarity = 2, gain = 0.5) => {
  let amp = 1;
  let freq = 1;
  let sum = 0;
  let norm = 0;
  for (let i = 0; i < octaves; i++) {
    sum += noise(x * freq, y * freq) * amp;
    norm += amp;
    amp *= gain;
    freq *= lacunarity;
  }
  return sum / norm;
};