/**
 * Tuning sweep for drift stabilisation. Explores counterSteerAssist and
 * yawDamping to find a band where held drifts sit around 30-45 degrees of
 * rear slip instead of spinning out.
 *
 * Run: node tools/tune-drift.mjs
 */
import { CarPhysics } from '../src/vehicles/Physics.js';
import { resolveSpec } from '../src/vehicles/VehicleData.js';

const DT = 1 / 120;
const ROAD = { grip: 1, drag: 1, rollingResistance: 1, roughness: 0 };

const drive = (id, spec, seconds, input) => {
  const p = new CarPhysics(spec);
  p.reset({ x: 0, y: 0, z: 0 }, 0);
  let maxSlip = 0;
  let spun = false;
  const steps = Math.round(seconds / DT);
  for (let i = 0; i < steps; i++) {
    p.update(DT, typeof input === 'function' ? input(i * DT, p) : input, ROAD);
    const slip = Math.abs(p.slipRear);
    if (slip > maxSlip) maxSlip = slip;
    // "Spun" = sliding sideways faster than moving forward.
    if (Math.abs(p.lateralSpeed) > p.speed * 0.98 && p.speed > 4) spun = true;
    if (!isFinite(p.position.x)) return { blewUp: true, maxSlip, spun };
  }
  return { blewUp: false, maxSlip, spun, heading: p.heading, speed: p.speedKmh };
};

const drift = (id, spec, seconds = 4) =>
  drive(
    id,
    spec,
    seconds,
    (t) => (t < 1.5 ? { throttle: 1, steer: 0.55 } : { throttle: 0.85, steer: 0.7, handbrake: 1 })
  );

const hold = (id, spec, seconds = 5) =>
  drive(id, spec, seconds, (t) => ({ throttle: 0.7, steer: 0.45, handbrake: t > 1 ? 1 : 0 }));

console.log('assist  damp  | compact          gt               rally');
console.log('                 (maxSlip deg / spun)');
console.log('---------------------------------------------------');

const results = [];
for (const assist of [0.55, 0.9, 1.3, 1.8, 2.4]) {
  for (const dampMul of [1, 2, 3.5]) {
    const cells = ['compact', 'gt', 'rally'].map((id) => {
      const base = resolveSpec(id);
      const spec = { ...base, counterSteerAssist: assist, yawDamping: base.yawDamping * dampMul };
      const d = drift(id, spec);
      return `${(d.maxSlip * 180) / Math.PI < 0 ? '!' : ''}${((d.maxSlip * 180) / Math.PI).toFixed(0).padStart(3)}deg${d.spun ? ' SPUN' : '    '}`;
    });
    console.log(
      `${String(assist).padStart(5)}  ${String(dampMul).padStart(4)}  | ${cells.join('  ')}`
    );
    results.push({ assist, dampMul, cells });
  }
}

console.log('\nTarget band: maxSlip 30-50deg, no SPUN.');