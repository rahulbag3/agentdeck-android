/**
 * Parameter sweep for racing-line generation: offset strength threshold and
 * smoothing radius. We want a line that (a) uses most of the available width
 * at real corners and (b) stays free of collapsed samples.
 *
 * Run: node tools/tune-line.mjs
 */
import * as THREE from 'three';
import { TrackPath } from '../src/tracks/TrackPath.js';

const ring = (n, rx, rz, wobble = 0) => {
  const out = [];
  for (let i = 0; i < n; i++) {
    const a = (i / n) * Math.PI * 2;
    const r = 1 + Math.sin(a * 3) * wobble;
    out.push(new THREE.Vector3(Math.cos(a) * rx * r, 0, Math.sin(a) * rz * r));
  }
  return out;
};

const shapes = {
  circle: ring(16, 90, 90),
  longOval: ring(16, 130, 70),
  sBend: [
    ...ring(10, 70, 55),
    ...ring(8, 45, 40, 0.25).map((p) => p.clone().add(new THREE.Vector3(120, 0, 40)))
  ],
  hairpin: [
    new THREE.Vector3(0, 0, 0), new THREE.Vector3(70, 0, 0),
    new THREE.Vector3(110, 0, 45), new THREE.Vector3(70, 0, 95),
    new THREE.Vector3(10, 0, 85), new THREE.Vector3(20, 0, 35),
    new THREE.Vector3(-30, 0, 50)
  ]
};

const measure = (path) => {
  const line = path.buildRacingLine();
  const segs = [];
  for (let i = 0; i < path.count; i++) {
    segs.push(line.points[i].distanceTo(line.points[(i + 1) % path.count]));
  }
  const avg = segs.reduce((a, b) => a + b, 0) / segs.length;
  const minRatio = Math.min(...segs) / avg;
  const maxOff = Math.max(...Array.from(line.offsets).map(Math.abs));
  const widthUse = maxOff / (path.halfWidth * 0.85);

  // p90 corner speed of the line vs the centreline.
  const stat = (pts) => {
    const ks = [];
    for (let i = 0; i < pts.length; i++) {
      const a = pts[(i - 1 + pts.length) % pts.length];
      const b = pts[i];
      const c = pts[(i + 1) % pts.length];
      const v1 = new THREE.Vector3().subVectors(b, a);
      const v2 = new THREE.Vector3().subVectors(c, b);
      ks.push(v1.angleTo(v2) / Math.max((v1.length() + v2.length()) * 0.5, 1e-6));
    }
    ks.sort((x, y) => x - y);
    return Math.sqrt(9.81 / Math.max(ks[Math.floor(ks.length * 0.9)], 1e-9));
  };
  const gain = stat(line.points) / stat(path.points);
  return { minRatio, widthUse, gain };
};

console.log('thr   smooth | shape      minRatio  widthUse  cornerGain');
console.log('-------------------------------------------------------');
for (const thr of [0.02, 0.06, 0.15]) {
  for (const smooth of [[30, 30, 20], [14, 14, 8], [8, 8, 4]]) {
    const cells = Object.entries(shapes).map(([name, pts]) => {
      const path = new TrackPath(pts, { samples: 900, width: 10 });
      // Patch the tunables for the sweep.
      path.__thr = thr;
      path.__smooth = smooth;
      const m = measureWith(path, thr, smooth);
      return `${name.slice(0, 5).padEnd(10)} ${m.minRatio.toFixed(3)}    ${(m.widthUse * 100).toFixed(0).padStart(3)}%`;
    });
    console.log(`${String(thr).padEnd(6)} ${String(smooth[0]).padStart(6)} | ${cells.join('\n                     ')}`);
  }
}

/** Re-implement the profile generation with explicit tunables. */
function measureWith(path, thrFrac, smoothRadii) {
  const N = path.count;
  const limit = path.halfWidth * 0.85;
  let peak = 0;
  for (let i = 0; i < N; i++) peak = Math.max(peak, Math.abs(path.curvature[i]));
  peak = peak || 1;
  const offsets = new Float32Array(N);
  for (let i = 0; i < N; i++) {
    const k = path.curvature[i];
    const s = Math.min(1, Math.max(0, (Math.abs(k) - peak * thrFrac) / (peak * 0.5)));
    offsets[i] = -Math.sign(k) * limit * s;
  }
  for (const r of smoothRadii) path._smoothWrapped(offsets, r);
  for (let i = 0; i < N; i++) offsets[i] = Math.max(-limit, Math.min(limit, offsets[i]));

  const points = [];
  for (let i = 0; i < N; i++) {
    const p = path.points[i];
    const n = path.normals[i];
    points.push(new THREE.Vector3(p.x + n.x * offsets[i], p.y, p.z + n.z * offsets[i]));
  }
  const segs = [];
  for (let i = 0; i < N; i++) segs.push(points[i].distanceTo(points[(i + 1) % N]));
  const avg = segs.reduce((a, b) => a + b, 0) / segs.length;
  const maxOff = Math.max(...Array.from(offsets).map(Math.abs));
  return { minRatio: Math.min(...segs) / avg, widthUse: maxOff / limit };
}