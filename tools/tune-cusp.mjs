/**
 * Does Laplacian smoothing actually converge to a cusp-free centreline, and how
 * many passes does it need? Sweeps strength x passes.
 *
 * Run: node tools/tune-cusp.mjs
 */
import * as THREE from 'three';
import { TrackPath } from '../src/tracks/TrackPath.js';

const pts = [
  new THREE.Vector3(0, 0, 0), new THREE.Vector3(60, 0, 0),
  new THREE.Vector3(110, 0, 20), new THREE.Vector3(120, 0, 70),
  new THREE.Vector3(90, 0, 100), new THREE.Vector3(40, 0, 95),
  new THREE.Vector3(30, 0, 60), new THREE.Vector3(55, 0, 35),
  new THREE.Vector3(20, 0, 15)
];
const path = new TrackPath(pts, { samples: 900, width: 10 });
const N = path.count;
const avg = path.length / N;

const worstTurnOf = (points) => {
  let worst = 0;
  for (let i = 0; i < points.length; i++) {
    const a = points[(i - 1 + points.length) % points.length];
    const b = points[i];
    const c = points[(i + 1) % points.length];
    let d1 = Math.atan2(b.x - a.x, b.z - a.z);
    let d2 = Math.atan2(c.x - b.x, c.z - b.z);
    let dh = d2 - d1;
    while (dh > Math.PI) dh -= 2 * Math.PI;
    while (dh < -Math.PI) dh += 2 * Math.PI;
    worst = Math.max(worst, Math.abs(dh));
  }
  return worst;
};

const offsetMinRatio = (points, normals, lat) => {
  let min = Infinity;
  for (let i = 0; i < points.length; i++) {
    const j = (i + 1) % points.length;
    const a = new THREE.Vector3(points[i].x + normals[i].x * lat, 0, points[i].z + normals[i].z * lat);
    const b = new THREE.Vector3(points[j].x + normals[j].x * lat, 0, points[j].z + normals[j].z * lat);
    min = Math.min(min, a.distanceTo(b));
  }
  return min / avg;
};

console.log(`avg segment ${avg.toFixed(3)}m, required turn limit ~${((avg / 4.5) * 180 / Math.PI).toFixed(2)} deg`);
console.log('\npasses  strength | worstTurn(deg)  offsetRatio@4.25m');
console.log('------------------------------------------------------');
for (const passes of [40, 200, 800, 3000]) {
  for (const strength of [0.25, 0.5]) {
    const pts2 = path.points.map((p) => p.clone());
    for (let it = 0; it < passes; it++) {
      const next = [];
      for (let i = 0; i < N; i++) {
        const a = pts2[(i - 1 + N) % N];
        const c = pts2[(i + 1) % N];
        next.push(new THREE.Vector3(
          pts2[i].x + ((a.x + c.x) / 2 - pts2[i].x) * strength,
          0,
          pts2[i].z + ((a.z + c.z) / 2 - pts2[i].z) * strength
        ));
      }
      pts2.splice(0, pts2.length, ...next);
    }
    const wt = worstTurnOf(pts2) * 180 / Math.PI;
    // normals from the smoothed points
    const normals = [];
    for (let i = 0; i < N; i++) {
      const a = pts2[(i - 1 + N) % N];
      const c = pts2[(i + 1) % N];
      const t = new THREE.Vector3().subVectors(c, a).setY(0).normalize();
      normals.push(new THREE.Vector3(-t.z, 0, t.x));
    }
    const ratio = offsetMinRatio(pts2, normals, 4.25);
    console.log(
      `${String(passes).padStart(6)}  ${strength.toFixed(2)}    | ${wt.toFixed(2).padStart(10)}  ${ratio.toFixed(3).padStart(12)}` +
        `${ratio > 0.3 ? '  OK' : ''}`
    );
  }
}