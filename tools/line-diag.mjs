/**
 * Diagnostic for racing-line quality. Reports peak curvature (which sets
 * minimum corner speed) alongside total absolute curvature.
 *
 * Run: node tools/line-diag.mjs
 */
import * as THREE from 'three';
import { TrackPath } from '../src/tracks/TrackPath.js';

const mk = (pts, opts) => new TrackPath(pts, { samples: 900, width: 10, ...opts });

// Generated ellipse-ish loops with well-spaced control points and no tight
// kinks, so the centreline itself is sane and we measure the racing line
// rather than a degenerate input.
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
  gentleS: [
    ...ring(10, 70, 55),
    ...ring(8, 45, 40, 0.25).map((p) => p.clone().add(new THREE.Vector3(120, 0, 40)))
  ],
  hairpin: [
    new THREE.Vector3(0, 0, 0),
    new THREE.Vector3(70, 0, 0),
    new THREE.Vector3(110, 0, 45),
    new THREE.Vector3(70, 0, 95),
    new THREE.Vector3(10, 0, 85),
    new THREE.Vector3(20, 0, 35),
    new THREE.Vector3(-30, 0, 50)
  ]
};

const curvatureStats = (pts) => {
  const N = pts.length;
  const ks = [];
  for (let i = 0; i < N; i++) {
    const a = pts[(i - 1 + N) % N];
    const b = pts[i];
    const c = pts[(i + 1) % N];
    const v1 = new THREE.Vector3().subVectors(b, a);
    const v2 = new THREE.Vector3().subVectors(c, b);
    const ds = (v1.length() + v2.length()) * 0.5;
    ks.push(v1.angleTo(v2) / Math.max(ds, 1e-6));
  }
  const sorted = [...ks].sort((x, y) => x - y);
  const q = (p) => sorted[Math.min(sorted.length - 1, Math.floor(sorted.length * p))];
  const median = q(0.5);
  const p90 = q(0.9);
  const peak = sorted[sorted.length - 1];
  return {
    total: ks.reduce((a, b) => a + b, 0),
    median,
    p90,
    peak,
    // Speed the line supports through a typical (median) and a hard (p90) corner.
    vMedian: Math.sqrt(9.81 / Math.max(median, 1e-9)),
    vP90: Math.sqrt(9.81 / Math.max(p90, 1e-9))
  };
};

for (const [name, pts] of Object.entries(shapes)) {
  const path = mk(pts);
  const line = path.buildRacingLine();
  const centre = curvatureStats(path.points);
  const rl = curvatureStats(line.points);
  const maxOff = Math.max(...Array.from(line.offsets).map(Math.abs));
  console.log(`\n--- ${name} ---`);
  console.log(`  centreline : median v=${centre.vMedian.toFixed(1)} m/s  p90 v=${centre.vP90.toFixed(1)} m/s  peak k=${centre.peak.toFixed(4)}`);
  console.log(`  racing line: median v=${rl.vMedian.toFixed(1)} m/s  p90 v=${rl.vP90.toFixed(1)} m/s  peak k=${rl.peak.toFixed(4)}`);
  const gain = rl.vMedian / centre.vMedian;
  console.log(`  typical-corner speed gain: ${gain.toFixed(2)}x  ${gain > 1.005 ? '(line is faster)' : '(no gain)'}`);
}