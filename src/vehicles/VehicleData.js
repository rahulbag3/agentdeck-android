/**
 * Vehicle catalogue, physics tuning constants, upgrade definitions and the
 * colour / livery catalogue. All designs are original.
 *
 * Specs are tuned for a miniature toy scale: short wheelbases, low mass, and
 * high grip relative to power, which produces lively but forgiving handling.
 */

export const VEHICLE_CLASSES = {
  compact: {
    id: 'compact',
    name: 'Compact',
    tagline: 'Nimble starter racer',
    description:
      'A light, playful club racer built for tight circuits. Slow in a straight line, but it rotates instantly and rewards a committed rhythm.',
    unlockCost: 0,
    stats: { speed: 6, acceleration: 9, handling: 9 },
    baseColor: 0xff5a3c,
    accentColor: 0xffffff,
    bodyStyle: 'hatch',
    dims: { length: 2.05, width: 1.05, height: 0.72 },
    spec: {
      mass: 420,
      wheelBase: 1.36,
      cgToFront: 0.62,
      trackWidth: 0.82,
      wheelRadius: 0.17,
      driveForce: 2750,
      brakeForce: 3400,
      topSpeed: 20.5,
      topSpeedTail: 0.22,
      grip: 1.42,
      traction: 1.05,
      dragCoefficient: 0.55,
      rollingResistance: 0.85,
      maxSteer: 0.62,
      maxYawRate: 3.1,
      yawInertia: 210,
      yawResponse: 1.0,
      yawDamping: 2.5,
      weightTransfer: 0.32,
      rollDistribution: 0.22,
      tyreB: 8.4,
      tyreC: 1.45,
      tyreE: 0.96,
      slipRefSpeed: 2.2,
      driftThreshold: 0.14,
      handbrakeGrip: 0.42,
      handbrakeDrag: 2.6,
      frontGripBias: 1.0,
      aeroGripLoss: 0.08,
      counterSteerAssist: 1.20
    }
  },

  gt: {
    id: 'gt',
    name: 'GT',
    tagline: 'Balanced all-rounder',
    description:
      'Long-bonnet grand tourer proportions with dependable pace. The reference car: quick enough to win, predictable enough to learn on.',
    unlockCost: 4500,
    stats: { speed: 8, acceleration: 7, handling: 8 },
    baseColor: 0x2f7bff,
    accentColor: 0xffd23f,
    bodyStyle: 'coupe',
    dims: { length: 2.45, width: 1.12, height: 0.66 },
    spec: {
      mass: 520,
      wheelBase: 1.62,
      cgToFront: 0.74,
      trackWidth: 0.9,
      wheelRadius: 0.185,
      driveForce: 3600,
      brakeForce: 4300,
      topSpeed: 25.5,
      topSpeedTail: 0.2,
      grip: 1.5,
      traction: 1.06,
      dragCoefficient: 0.5,
      rollingResistance: 0.8,
      maxSteer: 0.56,
      maxYawRate: 2.85,
      yawInertia: 300,
      yawResponse: 1.0,
      yawDamping: 2.7,
      weightTransfer: 0.34,
      rollDistribution: 0.26,
      tyreB: 9.0,
      tyreC: 1.45,
      tyreE: 0.96,
      slipRefSpeed: 2.4,
      driftThreshold: 0.15,
      handbrakeGrip: 0.45,
      handbrakeDrag: 2.4,
      frontGripBias: 1.0,
      aeroGripLoss: 0.1,
      counterSteerAssist: 1.10
    }
  },

  muscle: {
    id: 'muscle',
    name: 'Muscle',
    tagline: 'Brutal straight-line punch',
    description:
      'A wide-shouldered bruiser with a huge rear axle. Terrifying down the straights and happy to step its tail out, but the steering loads up hard.',
    unlockCost: 11000,
    stats: { speed: 9, acceleration: 8, handling: 5 },
    baseColor: 0x8b1f3a,
    accentColor: 0xf0f0f0,
    bodyStyle: 'muscle',
    dims: { length: 2.6, width: 1.3, height: 0.74 },
    spec: {
      mass: 720,
      wheelBase: 1.72,
      cgToFront: 0.8,
      trackWidth: 1.04,
      wheelRadius: 0.215,
      driveForce: 5100,
      brakeForce: 4600,
      topSpeed: 29.5,
      topSpeedTail: 0.18,
      grip: 1.36,
      traction: 0.98,
      dragCoefficient: 0.62,
      rollingResistance: 0.95,
      maxSteer: 0.5,
      maxYawRate: 2.4,
      yawInertia: 520,
      yawResponse: 0.96,
      yawDamping: 2.9,
      weightTransfer: 0.44,
      rollDistribution: 0.3,
      tyreB: 8.6,
      tyreC: 1.42,
      tyreE: 0.95,
      slipRefSpeed: 2.6,
      driftThreshold: 0.16,
      handbrakeGrip: 0.5,
      handbrakeDrag: 2.2,
      frontGripBias: 0.98,
      aeroGripLoss: 0.12,
      counterSteerAssist: 0.85
    }
  },

  rally: {
    id: 'rally',
    name: 'Rally',
    tagline: 'Loose-surface specialist',
    description:
      'Short wheelbase, high ride height and knobbly tyres. Loses a little tarmac pace but claws back enormous time on grass, dirt and sand.',
    unlockCost: 7500,
    stats: { speed: 7, acceleration: 8, handling: 9 },
    baseColor: 0x2fa84f,
    accentColor: 0xffffff,
    bodyStyle: 'rally',
    dims: { length: 2.15, width: 1.14, height: 0.84 },
    spec: {
      mass: 470,
      wheelBase: 1.44,
      cgToFront: 0.6,
      trackWidth: 0.94,
      wheelRadius: 0.2,
      driveForce: 3250,
      brakeForce: 3900,
      topSpeed: 23.0,
      topSpeedTail: 0.24,
      grip: 1.34,
      traction: 1.12,
      dragCoefficient: 0.66,
      rollingResistance: 1.05,
      maxSteer: 0.68,
      maxYawRate: 3.35,
      yawInertia: 240,
      yawResponse: 1.04,
      yawDamping: 2.4,
      weightTransfer: 0.3,
      rollDistribution: 0.2,
      tyreB: 7.8,
      tyreC: 1.5,
      tyreE: 0.97,
      slipRefSpeed: 2.2,
      driftThreshold: 0.16,
      handbrakeGrip: 0.46,
      handbrakeDrag: 2.4,
      frontGripBias: 1.02,
      aeroGripLoss: 0.05,
      counterSteerAssist: 1.35,
      looseSurfaceBonus: 0.45
    }
  },

  formula: {
    id: 'formula',
    name: 'Formula',
    tagline: 'Open-wheel ultimate speed',
    description:
      'Open wheels, big rear tyres and a razor-sharp chassis. The quickest car in the collection and the most demanding to drive.',
    unlockCost: 25000,
    stats: { speed: 10, acceleration: 9, handling: 10 },
    baseColor: 0xf5f6fa,
    accentColor: 0xe11d48,
    bodyStyle: 'formula',
    dims: { length: 2.7, width: 1.22, height: 0.6 },
    spec: {
      mass: 480,
      wheelBase: 1.68,
      cgToFront: 0.8,
      trackWidth: 0.98,
      wheelRadius: 0.19,
      driveForce: 4400,
      brakeForce: 5000,
      topSpeed: 33.0,
      topSpeedTail: 0.16,
      grip: 1.62,
      traction: 1.1,
      dragCoefficient: 0.72,
      rollingResistance: 0.75,
      maxSteer: 0.54,
      maxYawRate: 3.0,
      yawInertia: 260,
      yawResponse: 1.02,
      yawDamping: 2.6,
      weightTransfer: 0.36,
      rollDistribution: 0.24,
      tyreB: 9.6,
      tyreC: 1.48,
      tyreE: 0.97,
      slipRefSpeed: 2.6,
      driftThreshold: 0.14,
      handbrakeGrip: 0.44,
      handbrakeDrag: 2.8,
      frontGripBias: 1.0,
      aeroGripLoss: 0.16,
      counterSteerAssist: 0.52
    }
  }
};

export const VEHICLE_IDS = Object.keys(VEHICLE_CLASSES);

/**
 * Performance upgrades. Each has 4 levels; level 0 is stock.
 * `apply` mutates a resolved spec, so upgrades compose predictably.
 */
export const UPGRADES = {
  engine: {
    id: 'engine',
    name: 'Engine',
    icon: 'engine',
    blurb: 'Raises acceleration and top speed.',
    levels: [
      { label: 'Stock', cost: 0, driveForce: 1.0, topSpeed: 1.0 },
      { label: 'Stage 1', cost: 1200, driveForce: 1.14, topSpeed: 1.06 },
      { label: 'Stage 2', cost: 3400, driveForce: 1.3, topSpeed: 1.13 },
      { label: 'Stage 3', cost: 8200, driveForce: 1.5, topSpeed: 1.22 }
    ],
    apply: (s, lvl) => {
      const l = UPGRADES.engine.levels[lvl];
      s.driveForce *= l.driveForce;
      s.topSpeed *= l.topSpeed;
      // More power means more mass at the nose.
      s.mass *= 1 + lvl * 0.012;
      s.yawInertia *= 1 + lvl * 0.015;
    }
  },
  transmission: {
    id: 'transmission',
    name: 'Transmission',
    icon: 'gear',
    blurb: 'Sharper throttle response and quicker gearshift feel.',
    levels: [
      { label: 'Stock', cost: 0 },
      { label: 'Close-Ratio', cost: 900 },
      { label: 'Quick Shift', cost: 2600 },
      { label: 'Sequential', cost: 6000 }
    ],
    apply: (s, lvl) => {
      // Response manifests as faster steer and yaw build-up, plus more drive
      // at low speed (better launch).
      s.yawResponse *= 1 + lvl * 0.08;
      s.maxYawRate *= 1 + lvl * 0.035;
      s.driveForce *= 1 + lvl * 0.05;
      s.rollingResistance *= 1 - lvl * 0.05;
    }
  },
  tires: {
    id: 'tires',
    name: 'Tires',
    icon: 'tire',
    blurb: 'Compound with more mechanical grip.',
    levels: [
      { label: 'Touring', cost: 0 },
      { label: 'Sport', cost: 1100 },
      { label: 'Semi-Slick', cost: 3200 },
      { label: 'Competition', cost: 7800 }
    ],
    apply: (s, lvl) => {
      s.grip *= 1 + lvl * 0.075;
      s.traction *= 1 + lvl * 0.03;
      s.driftThreshold *= 1 - lvl * 0.045;
    }
  },
  suspension: {
    id: 'suspension',
    name: 'Suspension',
    icon: 'suspension',
    blurb: 'Keeps the car flat and stable, and adds cornering confidence.',
    levels: [
      { label: 'Stock', cost: 0 },
      { label: 'Sport', cost: 1000 },
      { label: 'Adjustable', cost: 3000 },
      { label: 'Race', cost: 7000 }
    ],
    apply: (s, lvl) => {
      s.weightTransfer *= 1 - lvl * 0.11;
      s.rollDistribution *= 1 - lvl * 0.12;
      s.yawDamping *= 1 + lvl * 0.13;
      s.maxSteer *= 1 + lvl * 0.04;
    }
  },
  brakes: {
    id: 'brakes',
    name: 'Brakes',
    icon: 'brake',
    blurb: 'Shorter stopping distances and better trail-braking.',
    levels: [
      { label: 'Stock', cost: 0 },
      { label: 'Street', cost: 800 },
      { label: 'Sport', cost: 2400 },
      { label: 'Carbon', cost: 5600 }
    ],
    apply: (s, lvl) => {
      s.brakeForce *= 1 + lvl * 0.16;
      s.mass *= 1 - lvl * 0.006;
    }
  }
};

export const UPGRADE_IDS = Object.keys(UPGRADES);

/** Paint colours available in the garage. */
export const PAINT_COLORS = [
  { name: 'Ember Red', value: 0xe23b3b },
  { name: 'Sunburst', value: 0xf5a623 },
  { name: 'Buttercup', value: 0xf2d64b },
  { name: 'Lime Zest', value: 0x76c043 },
  { name: 'Mint Chip', value: 0x37c98b },
  { name: 'Pool Blue', value: 0x2f9fe0 },
  { name: 'Deep Sea', value: 0x2f5bff },
  { name: 'Violet', value: 0x8b4fe0 },
  { name: 'Orchid', value: 0xe04fb0 },
  { name: 'Pearl', value: 0xf2f3f7 },
  { name: 'Graphite', value: 0x3a3f4b },
  { name: 'Carbon', value: 0x1b1e26 }
];

/** Livery stripe patterns, drawn onto the body as a canvas texture. */
export const LIVERY_PATTERNS = [
  { id: 'solid', name: 'Solid' },
  { id: 'stripe', name: 'Twin Stripe' },
  { id: 'racing', name: 'Racing Line' },
  { id: 'checker', name: 'Chequer' },
  { id: 'swoosh', name: 'Swoosh' }
];

export const WHEEL_STYLES = [
  { id: 'standard', name: 'Standard', rim: 0xd8dde6, spokes: 5 },
  { id: 'sport', name: 'Sport', rim: 0x2f3540, spokes: 7 },
  { id: 'classic', name: 'Classic', rim: 0xe6c14a, spokes: 4 },
  { id: 'carbon', name: 'Carbon', rim: 0x14171d, spokes: 10 }
];

/** Largest useful upgrade level for a category. */
export const maxUpgradeLevel = (id) => UPGRADES[id].levels.length - 1;

/**
 * Build a concrete physics spec for a vehicle with upgrade levels applied.
 * Returns a fresh object; the base catalogue is never mutated.
 */
export const resolveSpec = (vehicleId, upgrades = {}) => {
  const v = VEHICLE_CLASSES[vehicleId];
  if (!v) throw new Error(`Unknown vehicle: ${vehicleId}`);
  const spec = { ...v.spec };
  for (const id of UPGRADE_IDS) {
    const lvl = clampInt(upgrades[id] || 0, 0, maxUpgradeLevel(id));
    UPGRADES[id].apply(spec, lvl);
  }
  return spec;
};

/**
 * Advertised stats (0..10) combining base class stats with upgrades, so the
 * garage bars visibly move as the player invests.
 */
export const resolveStats = (vehicleId, upgrades = {}) => {
  const base = VEHICLE_CLASSES[vehicleId].stats;
  const u = upgrades || {};
  const total =
    (u.engine || 0) * 0.9 +
    (u.tires || 0) * 0.7 +
    (u.transmission || 0) * 0.55 +
    (u.suspension || 0) * 0.6 +
    (u.brakes || 0) * 0.45;
  return {
    speed: clampNum(base.speed + total * 0.55, 1, 10),
    acceleration: clampNum(base.acceleration + total * 0.62, 1, 10),
    handling: clampNum(base.handling + total * 0.5, 1, 10)
  };
};

function clampInt(v, a, b) {
  return Math.max(a, Math.min(b, Math.round(v)));
}
function clampNum(v, a, b) {
  return Math.max(a, Math.min(b, v));
}

/** Aggregate power score used for standings / AI balance. */
export const performanceScore = (vehicleId, upgrades = {}) => {
  const s = resolveStats(vehicleId, upgrades);
  return s.speed * 1.1 + s.acceleration + s.handling * 0.9;
};
      