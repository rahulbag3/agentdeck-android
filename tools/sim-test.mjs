/**
 * Headless physics harness. Runs the real CarPhysics step loop with no
 * rendering so handling behaviour can be verified numerically.
 *
 * Run: node tools/sim-test.mjs
 */
import { CarPhysics } from '../src/vehicles/Physics.js';
import { resolveSpec, VEHICLE_IDS } from '../src/vehicles/VehicleData.js';

const DT = 1 / 120;
const ROAD = { grip: 1, drag: 1, rollingResistance: 1, roughness: 0 };
const GRASS = { grip: 0.58, drag: 3.0, rollingResistance: 2.6, roughness: 0.5, aeroScale: 1.6 };

const run = (id, input, seconds, surface = ROAD, upgrades = {}) => {
  const p = new CarPhysics(resolveSpec(id, upgrades));
  p.reset({ x: 0, y: 0, z: 0 }, 0);
  let maxSpeed = 0;
  let maxSlip = 0;
  const steps = Math.round(seconds / DT);
  for (let i = 0; i < steps; i++) {
    p.update(DT, typeof input === 'function' ? input(i * DT, p) : input, surface);
    maxSpeed = Math.max(maxSpeed, p.speedKmh);
    maxSlip = Math.max(maxSlip, Math.abs(p.slipRear));
    if (!isFinite(p.position.x) || !isFinite(p.velocity.x)) {
      return { blewUp: true, t: i * DT };
    }
  }
  return {
    blewUp: false,
    speed: p.speedKmh,
    maxSpeed,
    dist: Math.hypot(p.position.x, p.position.z),
    maxSlipDeg: (maxSlip * 180) / Math.PI,
    skid: p.skid,
    headingDeg: (p.heading * 180) / Math.PI
  };
};

console.log('=== TOP SPEED (12s full throttle) ===');
const top = {};
for (const id of VEHICLE_IDS) {
  const r = run(id, { throttle: 1 }, 12);
  top[id] = r.maxSpeed;
  console.log(
    `  ${id.padEnd(8)} ${(r.blewUp ? 'BLEW UP' : r.maxSpeed.toFixed(1)).padStart(8)} km/h  skid=${r.skid.toFixed(2)}`
  );
}

console.log('\n=== 0-100 km/h ===');
for (const id of VEHICLE_IDS) {
  const p = new CarPhysics(resolveSpec(id));
  p.reset({ x: 0, y: 0, z: 0 }, 0);
  let t = null;
  for (let i = 0; i < 120 * 30; i++) {
    p.update(DT, { throttle: 1 }, ROAD);
    if (t === null && p.speedKmh >= 100) t = i * DT;
  }
  console.log(`  ${id.padEnd(8)} ${t ? t.toFixed(2) + ' s' : 'n/a (top ' + top[id].toFixed(0) + ')'}`);
}

console.log('\n=== BRAKING from 100 km/h ===');
for (const id of VEHICLE_IDS) {
  const p = new CarPhysics(resolveSpec(id));
  p.reset({ x: 0, y: 0, z: 0 }, 0);
  for (let i = 0; i < 120 * 40 && p.speedKmh < 100; i++) p.update(DT, { throttle: 1 }, ROAD);
  const z0 = p.position.z;
  for (let i = 0; i < 120 * 15; i++) {
    p.update(DT, { brake: 1 }, ROAD);
    if (p.speedKmh < 1) break;
  }
  console.log(`  ${id.padEnd(8)} ${(z0 - p.position.z).toFixed(1).padStart(6)} m`);
}

console.log('\n=== STEADY-STATE CORNERING (full lock) ===');
for (const id of VEHICLE_IDS) {
  const r = run(id, { throttle: 0.45, steer: 1 }, 8);
  console.log(
    `  ${id.padEnd(8)} speed=${r.speed.toFixed(1).padStart(6)} km/h  rearSlip=${r.maxSlipDeg.toFixed(1).padStart(6)}deg  yawAccum=${r.headingDeg.toFixed(0).padStart(6)}deg`
  );
}

console.log('\n=== DRIFT (handbrake + steer) ===');
for (const id of VEHICLE_IDS) {
  const r = run(
    id,
    (t) => (t < 2 ? { throttle: 1, steer: 0.6 } : { throttle: 0.9, steer: 0.85, handbrake: 1 }),
    4
  );
  console.log(
    `  ${id.padEnd(8)} maxSlip=${r.maxSlipDeg.toFixed(1).padStart(6)}deg  skid=${r.skid.toFixed(2)}  heading=${r.headingDeg.toFixed(0).padStart(5)}deg`
  );
}

console.log('\n=== OFF-ROAD RETENTION (10s throttle) ===');
for (const id of VEHICLE_IDS) {
  const tar = run(id, { throttle: 1 }, 10, ROAD);
  const gra = run(id, { throttle: 1 }, 10, GRASS);
  console.log(
    `  ${id.padEnd(8)} tarmac=${tar.maxSpeed.toFixed(1).padStart(6)}  grass=${gra.maxSpeed.toFixed(1).padStart(6)} km/h  ${((gra.maxSpeed / tar.maxSpeed) * 100).toFixed(0)}%`
  );
}

console.log('\n=== REVERSE ===');
{
  const p = new CarPhysics(resolveSpec('gt'));
  p.reset({ x: 0, y: 0, z: 0 }, 0);
  for (let i = 0; i < 120 * 6; i++) p.update(DT, { brake: 1 }, ROAD);
  console.log(`  ${p.speedKmh.toFixed(1)} km/h, moved ${Math.abs(p.position.z).toFixed(2)} m backwards`);
}

console.log('\n=== COAST STABILITY (no input, 5s from 60km/h) ===');
{
  const p = new CarPhysics(resolveSpec('gt'));
  p.reset({ x: 0, y: 0, z: 0 }, 0);
  for (let i = 0; i < 120 * 40 && p.speedKmh < 60; i++) p.update(DT, { throttle: 1 }, ROAD);
  const h0 = p.heading;
  for (let i = 0; i < 120 * 5; i++) p.update(DT, {}, ROAD);
  console.log(
    `  heading drift ${(((p.heading - h0) * 180) / Math.PI).toFixed(4)} deg, speed ${p.speedKmh.toFixed(1)} km/h`
  );
}

console.log('\n=== LONG-RUN STABILITY (90s mixed input) ===');
for (const id of VEHICLE_IDS) {
  const r = run(
    id,
    (t) => ({
      throttle: 0.5 + 0.5 * Math.sin(t * 0.7),
      steer: Math.sin(t * 1.1),
      brake: Math.sin(t * 0.23) > 0.85 ? 1 : 0,
      handbrake: Math.sin(t * 0.37) > 0.93 ? 1 : 0
    }),
    90
  );
  console.log(
    `  ${id.padEnd(8)} ${r.blewUp ? 'BLEW UP @' + r.t.toFixed(1) + 's' : 'stable dist=' + r.dist.toFixed(0) + 'm maxSlip=' + r.maxSlipDeg.toFixed(0) + 'deg'}`
  );
}