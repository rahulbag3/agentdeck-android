import * as THREE from 'three';
import { clamp, clamp01, damp, lerp, moveTowards, smoothstep, wrapAngle } from '../engine/utils.js';

/**
 * Arcade vehicle physics built on a real slip-angle tyre model.
 *
 * Rather than faking grip with a lateral-velocity damper, each axle gets a
 * simplified Pacejka magic-formula curve. That yields genuine weight transfer,
 * an understeer/oversteer balance, throttle-on oversteer and controllable
 * drifts, while tunable assists keep the result forgiving at toy scale.
 *
 * Local space convention: +Z forward, +X right, +Y up.
 */

const GRAVITY = 9.81;

/** Simplified magic formula; returns a normalised force for a slip angle. */
function tyreCurve(slip, B, C, D, E) {
  const bs = B * slip;
  return D * Math.sin(C * Math.atan(bs - E * (bs - Math.atan(bs))));
}

/** Slip magnitude -> 0..1 skid intensity, relative to the car's threshold. */
function skidFromSlip(slip, spec) {
  return clamp01((Math.abs(slip) - spec.driftThreshold) / 0.28);
}

export class CarPhysics {
  /** @param {object} spec a VehicleData-derived physics spec */
  constructor(spec) {
    this.spec = spec;
    this.mass = spec.mass;
    this.invMass = 1 / spec.mass;
    this.wheelBase = spec.wheelBase;
    this.cgToFront = spec.cgToFront;
    this.cgToRear = spec.wheelBase - spec.cgToFront;
    this.trackWidth = spec.trackWidth;
    this.wheelRadius = spec.wheelRadius;

    // ---- State ----
    this.position = new THREE.Vector3();
    this.velocity = new THREE.Vector3();
    this.heading = 0;
    this.yawRate = 0;
    this.steerAngle = 0;
    this.throttle = 0;
    this.brake = 0;
    this.handbrake = 0;
    this.speed = 0;
    this.slipFront = 0;
    this.slipRear = 0;
    this.lateralG = 0;
    this.longitudinalG = 0;
    this.wheelSpin = 0;
    this.bumpOffset = 0;
    this.lastImpact = 0;
    this.loadFront = 0.5;
    this.loadRear = 0.5;
    this.skid = 0;
    this.wheelSlipRatio = 0;
    this.surface = null;

    // Scratch vectors, reused to avoid per-frame allocation.
    this._fwd = new THREE.Vector3();
    this._right = new THREE.Vector3();

    this.reset(new THREE.Vector3(), 0);
  }

  /** Place the car at a position/heading with all motion zeroed. */
  reset(position, heading = 0) {
    this.position.copy(position);
    this.velocity.set(0, 0, 0);
    this.heading = heading;
    this.yawRate = 0;
    this.steerAngle = 0;
    this.speed = 0;
    this.skid = 0;
    this.lateralG = 0;
    this.longitudinalG = 0;
    this.bumpOffset = 0;
    this.lastImpact = 0;
    this._syncBasis();
  }

  _syncBasis() {
    const s = Math.sin(this.heading);
    const c = Math.cos(this.heading);
    this._fwd.set(s, 0, c);
    this._right.set(c, 0, -s);
  }

  get maxSpeed() { return this.spec.topSpeed; }
  get forwardVector() { return this._fwd; }
  get rightVector() { return this._right; }
  /** Signed forward speed, so reversing works naturally. */
  get forwardSpeed() { return this.velocity.dot(this._fwd); }
  get lateralSpeed() { return this.velocity.dot(this._right); }
  /** Speed in km/h for HUD display. */
  get speedKmh() { return Math.abs(this.forwardSpeed) * 3.6; }

  /** Normalised 0..1 engine reading with simulated 5-step gear changes. */
  get rpmNorm() {
    const s = Math.abs(this.forwardSpeed);
    const gears = 5;
    const perGear = this.spec.topSpeed / gears;
    const g = clamp(Math.floor(s / perGear), 0, gears - 1);
    const within = clamp01((s - g * perGear) / perGear);
    return clamp01((g + within) / gears) * 0.86 + this.throttle * 0.14;
  }

  /** Apply an instantaneous velocity change (used by collisions). */
  applyImpulse(vec, spinImpulse = 0) {
    this.velocity.add(vec);
    this.yawRate += spinImpulse;
    this.lastImpact = 1;
  }

/**
   * Advance the simulation.
   * @param {number} dt fixed timestep in seconds
   * @param {object} input { throttle, brake, steer, handbrake }
   * @param {object} surface { grip, drag, rollingResistance, roughness }
   */
  update(dt, input, surface) {
    const spec = this.spec;
    const S = surface || { grip: 1, drag: 1, rollingResistance: 1, roughness: 0 };
    this.surface = S;

    const throttle = clamp01(input.throttle || 0);
    const brakeInput = clamp01(input.brake || 0);
    const steerInput = clamp(input.steer || 0, -1, 1);
    const handbrake = clamp01(input.handbrake || 0);

    this.throttle = throttle;
    this.brake = brakeInput;
    this.handbrake = handbrake;

    this._syncBasis();

    const vLong = this.velocity.dot(this._fwd);
    const vLat = this.velocity.dot(this._right);
    const speedAbs = Math.hypot(vLong, vLat);
    this.speed = speedAbs;

    // ---- Steering: speed-sensitive lock ----
    const speedNorm = clamp01(speedAbs / spec.topSpeed);
    const lockScale = lerp(1, 0.34, smoothstep(speedNorm * 1.15));
    const maxSteer = spec.maxSteer * lockScale;
    const steerRate = lerp(6.5, 3.4, speedNorm);
    this.steerAngle = moveTowards(this.steerAngle, steerInput * maxSteer, steerRate * dt);

    // ---- Vertical load + weight transfer ----
    const transferLong = clamp(this.longitudinalG, -1.2, 1.2) * spec.weightTransfer;
    const transferLat = clamp(this.lateralG, -1.4, 1.4) * spec.rollDistribution;
    this.loadFront = clamp(0.5 - transferLong * 0.5 + transferLat * 0.5, 0.08, 0.92);
    this.loadRear = 1 - this.loadFront;

    // ---- Tyre slip angles ----
    // Guarded denominator so we never divide by ~0 at a standstill.
    const vRef = Math.max(Math.abs(vLong), spec.slipRefSpeed);
    const dirSign = vLong >= 0 ? 1 : -1;

    this.slipFront =
      Math.atan2(vLat + this.yawRate * this.cgToFront, vRef) - this.steerAngle * dirSign;
    this.slipRear = Math.atan2(vLat - this.yawRate * this.cgToRear, vRef);

    // ---- Lateral forces ----
    // Some cars (rally) are specialists: a loose-surface bonus restores part of
    // the grip that the surface takes away, scaled by how loose it actually is.
    const looseness = clamp01(1 - (S.grip ?? 1));
    const surfaceBonus = 1 + (spec.looseSurfaceBonus || 0) * looseness;
    const gripBase = spec.grip * S.grip * surfaceBonus;

    // Handbrake unloads the rear: this both initiates and sustains a drift.
    const rearGrip = gripBase * lerp(1, spec.handbrakeGrip, handbrake);
    const frontGrip = gripBase * lerp(1, spec.frontGripBias, spec.aeroGripLoss * speedNorm);

    const fyFront =
      -tyreCurve(this.slipFront, spec.tyreB, spec.tyreC, frontGrip * this.loadFront, spec.tyreE) *
      spec.mass * GRAVITY;
    const fyRear =
      -tyreCurve(this.slipRear, spec.tyreB, spec.tyreC, rearGrip * this.loadRear, spec.tyreE) *
      spec.mass * GRAVITY;

    // ---- Longitudinal forces ----
    // Loose surfaces bleed far more speed than tarmac, so scale the aero and
    // rolling penalties hard and cut the tyre's ability to put power down.
    const surfaceDrag = (S.drag ?? 1) * (S.aeroScale ?? 1);
    const rolling = spec.rollingResistance * (S.rollingResistance ?? 1);

    // A loose surface cannot put full power down even at full throttle, though
    // cars with a loose-surface bonus claw some of that back.
    const tractionLoss =
      S.grip < 1 ? lerp(1, S.grip, 0.8 - Math.min(0.55, (spec.looseSurfaceBonus || 0) * 1.6)) : 1;

    // Drive force tapers with speed so top speed is asymptotic, not abrupt.
    const speedFrac = clamp01(Math.abs(vLong) / spec.topSpeed);
    const powerCurve = 1 - speedFrac * speedFrac * (1 - spec.topSpeedTail);
    const driveForce = throttle * spec.driveForce * powerCurve * tractionLoss;

    let brakeForce = 0;
    if (brakeInput > 0) {
      if (vLong > 0.4) {
        brakeForce = -brakeInput * spec.brakeForce;
      } else {
        // Reverse gear, limited to a fraction of top speed.
        const over = clamp01(-vLong / (spec.topSpeed * 0.28));
        brakeForce = -brakeInput * spec.driveForce * 0.55 * (1 - over);
      }
    }

    const aeroDrag = -vLong * Math.abs(vLong) * spec.dragCoefficient * surfaceDrag;
    const rollDrag = -vLong * rolling;
    const hbDrag = -vLong * handbrake * spec.handbrakeDrag;

    const fx = driveForce + brakeForce + aeroDrag + rollDrag + hbDrag;

    // ---- Friction circle ----
    // Longitudinal and lateral demand share one tyre budget, which is what
    // makes trail-braking and power-oversteer behave believably.
    const maxLong = spec.mass * GRAVITY * gripBase * spec.traction;
    const fxClamped = clamp(fx, -maxLong, maxLong);
    const usedLong = Math.abs(fxClamped) / Math.max(maxLong, 1e-4);
    const latBudget =
      spec.mass * GRAVITY * gripBase * Math.sqrt(Math.max(0, 1 - usedLong * usedLong * 0.85));
    const scale = latBudget / Math.max(Math.abs(fyFront) + Math.abs(fyRear), 1e-4);
    const fyFrontC = fyFront * Math.min(1, scale);
    const fyRearC = fyRear * Math.min(1, scale);

    // ---- Combine into world-space acceleration ----
    const accLong = fxClamped * this.invMass;
    const accLat = (fyFrontC + fyRearC) * this.invMass;
    this.longitudinalG = accLong / GRAVITY;
    this.lateralG = accLat / GRAVITY;

    this.velocity.addScaledVector(this._fwd, accLong * dt);
    this.velocity.addScaledVector(this._right, accLat * dt);

    // ---- Yaw dynamics ----
    const moment =
      fyFrontC * Math.cos(this.steerAngle) * this.cgToFront - fyRearC * this.cgToRear;
    let yawAccel = (moment / spec.yawInertia) * spec.yawResponse;
    yawAccel -= this.yawRate * spec.yawDamping;
    this.yawRate += yawAccel * dt;

    // Counter-steer stabilisation. When the rear steps out beyond the car's slip
    // threshold we bleed yaw back toward the velocity vector. This runs on
    // every frame (including full steering lock, where a slide is being held
    // deliberately) but is weakened while the driver is actively
    // countersteering or pulling on the handbrake, so drifts stay drivable.
    const slipExcess = Math.abs(this.slipRear) - spec.driftThreshold;
    if (slipExcess > 0) {
      // Countersteering = steering opposite to the current slide.
      const slideDir = Math.sign(this.slipRear) || 1;
      const counterSteering = Math.sign(steerInput) !== 0 && Math.sign(steerInput) === -slideDir;
      const intent = Math.max(handbrake, counterSteering ? 0.75 : 0);
      const assist =
        spec.counterSteerAssist *
        smoothstep(clamp01(slipExcess / 0.4)) *
        (1 - intent * 0.7);
      if (assist > 0.001) {
        // Damp the yaw rate that is generating the slide. Never adds rotation,
        // so it cannot fight a deliberate drift.
        this.yawRate = damp(this.yawRate, 0, assist * 5, dt);
      }
    }

    this.yawRate = clamp(this.yawRate, -spec.maxYawRate, spec.maxYawRate);
    this.heading = wrapAngle(this.heading + this.yawRate * dt);

    // ---- Integrate position ----
    this.position.addScaledVector(this.velocity, dt);

    // ---- Slip metrics driving smoke, marks and audio ----
    // Skid is driven by genuine rear-axle slip angle and lateral slide, not by
    // friction-circle saturation (which is high under any hard acceleration and
    // would light up the tyres in a straight line).
    const rearSlip = skidFromSlip(this.slipRear, spec);
    const lateralSlide = clamp01((Math.abs(vLat) - spec.driftThreshold * 2.2) / 3.2);
    const handbrakeSlide = handbrake * clamp01(speedAbs / 5);
    const targetSkid = clamp01(
      Math.max(rearSlip, lateralSlide * 0.85, handbrakeSlide * 0.95)
    );
    this.skid = damp(this.skid, targetSkid * clamp01(speedAbs / 2.5), 10, dt);

    // Wheel spin for visual rolling and skid streak length.
    const wheelSurfaceSpeed = Math.abs(vLong) + Math.abs(fxClamped) * 0.00022;
    this.wheelSpin += (wheelSurfaceSpeed / this.wheelRadius) * dt * (vLong >= 0 ? 1 : -1);
    if (Math.abs(this.wheelSpin) > 1e6) this.wheelSpin = 0;
    this.wheelSlipRatio = clamp01(handbrake * 0.8 + rearSlip * 0.6);

    // Rough surfaces add vertical jitter so off-road driving feels bumpy.
    this.bumpOffset = 0;
    if (S.roughness > 0 && speedAbs > 1) {
      this.bumpOffset =
        (Math.sin(this.position.x * 4.7 + this.wheelSpin * 0.7) +
          Math.sin(this.position.z * 5.3 - this.wheelSpin * 0.5)) *
        0.5 *
        S.roughness *
        clamp01(speedAbs / 12) *
        0.06;
    }

    // Safety net against integration blow-ups.
    const maxV = spec.topSpeed * 2.2;
    if (this.velocity.lengthSq() > maxV * maxV) this.velocity.setLength(maxV);

    this._syncBasis();
    return this;
  }
}

export { GRAVITY, tyreCurve };
