/**
 * Isolation test for offset-line collapse: is it caused by the offset profile,
 * or inherent to the centreline geometry?
 *
 * Run: node tools/collapse-probe.mjs
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

const minSegForOffset = (lat) => {
  let min = Infinity;
  for (let i = 0; i < N; i++) {
    const a = path.pointAt(i, lat);
    const b = path.pointAt(i + 1, lat);
    min = Math.min(min, a.distanceTo(b));
  }
  return min;
};

console.log(`centreline avg segment ${avg.toFixed(3)}m`);
console.log('\nconstant-offset lines (no racing profile):');
for (const lat of [0, 1, 2, 3, 4, 4.25, -4.25]) {
  const m = minSegForOffset(lat);
  console.log(
    `  offset ${String(lat).padStart(6)}m -> min segment ${m.toFixed(4)}m  ratio ${(m / avg).toFixed(3)}` +
      `${m / avg < 0.15 ? '   <-- COLLAPSES' : ''}`
  );
}

const LAT = 4.25;
// Show, at the worst spot, which side is actually the inside of the corner.
let worst = { i: -1, d: Infinity };
for (let i = 0; i < N; i++) {
  const a = path.pointAt(i, LAT);
  const b = path.pointAt(i + 1, LAT);
  const d = a.distanceTo(b);
  if (d < worst.d) worst = { i, d };
}
const wi = worst.i;
console.log(`\nworst at index ${wi}: offset segment ${worst.d.toFixed(4)}m vs centreline ${avg.toFixed(4)}m`);
console.log(`curvature there = ${path.curvature[wi].toFixed(5)}  radius = ${(1 / Math.abs(path.curvature[wi])).toFixed(2)}m`);
console.log('local geometry (offsetPt shown for BOTH sides):');
for (let k = -3; k <= 3; k++) {
  const j = (wi + k + N) % N;
  const c = path.points[j];
  const t = path.tangents[j];
  const n = path.normals[j];
  const oPos = new THREE.Vector3(c.x + n.x * LAT, 0, c.z + n.z * LAT);
  const oNeg = new THREE.Vector3(c.x - n.x * LAT, 0, c.z - n.z * LAT);
  console.log(
    `  i=${j} centre=(${c.x.toFixed(2)}, ${c.z.toFixed(2)}) tangent=(${t.x.toFixed(2)}, ${t.z.toFixed(2)})` +
      `  +L=(${oPos.x.toFixed(2)}, ${oPos.z.toFixed(2)})  -L=(${oNeg.x.toFixed(2)}, ${oNeg.z.toFixed(2)})`
  );
}
const pa = path.pointAt(wi, LAT);
const pb = path.pointAt(wi + 1, LAT);
const dx = pb.x - pa.x;
const dz = pb.z - pa.z;
console.log(`\noffset segment vector (${dx.toFixed(4)}, ${dz.toFixed(4)}) len ${pa.distanceTo(pb).toFixed(4)}`);
console.log(`  along tangent: ${(dx * path.tangents[wi].x + dz * path.tangents[wi].z).toFixed(4)}m`);
console.log(`  along normal:  ${(dx * path.normals[wi].x + dz * path.normals[wi].z).toFixed(4)}m`);