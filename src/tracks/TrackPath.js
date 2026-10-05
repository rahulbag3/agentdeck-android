import * as THREE from 'three';
import { clamp, clamp01, lerp, TAU, wrapAngle } from '../engine/utils.js';

/**
 * Track centreline sampling and spatial lookup.
 *
 * A track is defined by a closed Catmull-Rom spline through hand-placed control
 * points. From it we derive an evenly-resampled centreline with tangents,
 * lateral normals, curvature and cumulative arc length, plus a uniform-grid
 * spatial hash so "which part of track am I near?" is an O(1) query.
 *
 * Everything downstream (lap logic, off-road detection, AI, minimap, camera)
 * queries TrackPath rather than re-deriving its own geometry.
 */

export const SURFACE = {
  TARMAC: 'tarmac',
  KERB: 'kerb',
  VERGE: 'verge',
  GRASS: 'grass',
  DIRT: 'dirt',
  SAND: 'sand',
  WATER: 'water'
};

/**
 * Physics + effect characteristics per surface.
 *   grip   : multiplier on tyre grip
 *   drag   : multiplier on aerodynamic drag
 *   roll   : multiplier on rolling resistance
 *   rough  : vertical jitter amplitude
 *   rumble : camera / audio shake intensity 0..1
 */
export const SURFACE_PROPS = {
  [SURFACE.TARMAC]: { grip: 1.0, drag: 1, roll: 1, rough: 0, rumble: 0, skidCol: 0x2a2a30, dust: 0xbbbbbb },
  [SURFACE.KERB]: { grip: 0.86, drag: 1.0, roll: 1.25, rough: 0.35, rumble: 1.0, skidCol: 0x33333a, dust: 0xdddddd },
  [SURFACE.VERGE]: { grip: 0.72, drag: 1.7, roll: 1.9, rough: 0.3, rumble: 0.35, skidCol: 0x5a4a32, dust: 0xc9b28a },
  [SURFACE.GRASS]: { grip: 0.58, drag: 3.0, roll: 2.6, rough: 0.5, rumble: 0.3, skidCol: 0x3f6b2c, dust: 0x7fae5a },
  [SURFACE.DIRT]: { grip: 0.52, drag: 3.4, roll: 3.0, rough: 0.65, rumble: 0.45, skidCol: 0x6b4a28, dust: 0xb08a5e },
  [SURFACE.SAND]: { grip: 0.46, drag: 4.2, roll: 3.6, rough: 0.55, rumble: 0.35, skidCol: 0x9c7c4c, dust: 0xd9bd8a },
  [SURFACE.WATER]: { grip: 0.3, drag: 6.0, roll: 4.5, rough: 0.8, rumble: 0.5, skidCol: 0x2f6f9f, dust: 0xbfe3ff }
};

/** Build the physics descriptor consumed by CarPhysics.update(). */
export const surfaceProps = (type) => {
  const p = SURFACE_PROPS[type] || SURFACE_PROPS[SURFACE.TARMAC];
  return {
    type,
    grip: p.grip,
    drag: p.drag,
    rollingResistance: p.roll,
    roughness: p.rough,
    rumble: p.rumble,
    skidColor: p.skidCol,
    dustColor: p.dust
  };
};

/* __CLASS__ */
export class TrackPath {
  /**
   * @param {THREE.Vector3[]} controlPoints closed loop of control points
   * @param {object} opts samples, width, kerbWidth, vergeWidth, surface, ...
   */
  constructor(controlPoints, opts = {}) {
    this.opts = opts;
    this.sampleCount = opts.samples || 1200;
    this.width = opts.width || 9;
    this.halfWidth = this.width / 2;
    this.kerbWidth = opts.kerbWidth ?? 0.9;
    this.vergeWidth = opts.vergeWidth ?? 4.5;
    this.barrierWidth = opts.barrierWidth ?? 1.2;
    this.defaultSurface = opts.surface || SURFACE.GRASS;
    this.vergeSurface = opts.vergeSurface || null;
    this.barrierSurface = opts.barrierSurface || null;

    this.curve = new THREE.CatmullRomCurve3(
      controlPoints.map((p) => p.clone()),
      true,
      'centripetal',
      0.5
    );

    this._buildSamples();
    this._buildGrid();
    this._buildCheckpoints(opts.checkpointCount || 24);
  }

  _buildSamples() {
    const N = (this.count = this.sampleCount);
    // getSpacedPoints resamples at even arc length; a closed curve repeats the
    // first point at the end, so the first N entries are the unique samples.
    const pts = this.curve.getSpacedPoints(N);
    this.points = [];
    for (let i = 0; i < N; i++) this.points.push(pts[i].clone());

    this.curvature = new Float32Array(N);
    this.headings = new Float32Array(N);
    this.distance = new Float32Array(N + 1);
    this.tangents = new Array(N);
    this.normals = new Array(N);
    this.rights = new Array(N);

    let d = 0;
    for (let i = 0; i < N; i++) {
      this.distance[i] = d;
      d += this.points[i].distanceTo(this.points[(i + 1) % N]);
    }
    this.distance[N] = d;
    this.length = d;

    // A turn limit that depends on the sample spacing. An offset point at lateral
    // distance L swings by L * |dHeading| between samples, while the samples
    // themselves advance by the segment length. For the offset line to stay
    // well-formed we need L * |dHeading| < segmentLength, i.e.
    //   |dHeading| < segmentLength / L.
    // Using the nominal spacing and the widest offset we allow gives a single
    // limit that satisfies the condition across the whole track.
    const maxOffset = (this.width / 2) * 0.9;
    const nominalStep = this.length / N;
    const limitRadians = Math.min(
      0.3,
      Math.max((nominalStep / Math.max(maxOffset, 0.001)) * 0.45, 1e-3)
    );

    // Remove cusps from the sample positions themselves.
    //
    // An offset line point is centre[i] + normal[i]*offset, and normal[i] is
    // derived from neighbouring samples, so the offset point can swing by up to
    // offset * |heading change| between samples. Where a curve doubles back on
    // itself the heading can flip by nearly 180 degrees between two adjacent
    // samples; the swing then exceeds the along-track step and cancels it,
    // collapsing the offset line to zero length. No racing line built on such a
    // centreline would be meaningful.
    //
    // Smoothing the positions removes the cusp at its source while keeping the
    // points, tangents and normals mutually consistent and preserving the
    // uniform arc-length spacing that getSpacedPoints guarantees.
    // Iterate until every sample's turn respects the limit. The number of passes
    // needed scales with how severe the cusp is, so we keep going (up to a
    // generous cap) and only stop once the geometry is genuinely usable. A
    // centreline with a sub-2-metre radius is not a circuit at all, and any
    // offset line through it would fold, so it is worth smoothing hard.
    // Iterate smoothing together with arc-length re-spacing until the geometry
    // converges. Doing both in one loop matters: smoothing alone leaves the
    // samples unevenly spaced, while re-spacing alone reintroduces the cusp
    // through the fitted curve. Alternating them lets the centreline reach a
    // genuine fixed point.
    //
    // A centreline with a sub-2-metre radius is not a circuit at all, and every
    // offset line through it would fold, so smoothing is worth doing hard.
    let converged = false;
    for (let pass = 0; pass < 400; pass++) {
      // Worst per-sample turn, measured on the smoothed polyline.
      let maxTurn = 0;
      for (let i = 0; i < N; i++) {
        const a = this.points[(i - 1 + N) % N];
        const b = this.points[i];
        const c = this.points[(i + 1) % N];
        const d1 = Math.atan2(b.x - a.x, b.z - a.z);
        const d2 = Math.atan2(c.x - b.x, c.z - b.z);
        let dh = d2 - d1;
        while (dh > Math.PI) dh -= TAU;
        while (dh < -Math.PI) dh += TAU;
        maxTurn = Math.max(maxTurn, Math.abs(dh));
      }
      if (maxTurn <= limitRadians) {
        converged = true;
        break;
      }

      // One Laplacian step: pull each sample toward the midpoint of its
      // neighbours, which is the discrete curvature-reducing operator.
      const next = new Array(N);
      for (let i = 0; i < N; i++) {
        const a = this.points[(i - 1 + N) % N];
        const c = this.points[(i + 1) % N];
        next[i] = new THREE.Vector3(
          (a.x + 2 * this.points[i].x + c.x) / 4,
          (a.y + 2 * this.points[i].y + c.y) / 4,
          (a.z + 2 * this.points[i].z + c.z) / 4
        );
      }
      this.points = next;

      // Re-space by arc length so the samples stay evenly distributed.
      const fit = new THREE.CatmullRomCurve3(
        this.points.map((p) => p.clone()),
        true,
        'centripetal',
        0.5
      );
      const resampled = fit.getSpacedPoints(N);
      for (let i = 0; i < N; i++) this.points[i].copy(resampled[i]);
    }
    this.converged = converged;

    // Recompute arc length after smoothing, then rebuild tangents, headings and
    // lateral normals directly from the smoothed positions so that points,
    // normals and curvature all describe the *same* curve. Every downstream
    // query (surface lookup, offsetting, AI) depends on that consistency.
    d = 0;
    for (let i = 0; i < N; i++) {
      this.distance[i] = d;
      d += this.points[i].distanceTo(this.points[(i + 1) % N]);
    }
    this.distance[N] = d;
    this.length = d;

    for (let i = 0; i < N; i++) {
      const prev = this.points[(i - 1 + N) % N];
      const next = this.points[(i + 1) % N];
      const t = new THREE.Vector3().subVectors(next, prev).setY(0).normalize();
      this.tangents[i] = t;
      this.headings[i] = Math.atan2(t.x, t.z);
      // Left normal in XZ: rotate the tangent +90 degrees about +Y.
      this.normals[i] = new THREE.Vector3(-t.z, 0, t.x);
      this.rights[i] = new THREE.Vector3(t.z, 0, -t.x);
    }

    // Signed curvature from heading change per unit distance.
    for (let i = 0; i < N; i++) {
      const a = this.headings[(i - 1 + N) % N];
      const b = this.headings[(i + 1) % N];
      let dh = b - a;
      while (dh > Math.PI) dh -= TAU;
      while (dh < -Math.PI) dh += TAU;
      const ds = Math.max(this.distance[i + 1] - this.distance[i], 1e-4);
      this.curvature[i] = dh / ds;
    }
    this._smoothWrapped(this.curvature, 6);
  }

  /** In-place box blur that wraps around the loop. */
  _smoothWrapped(arr, radius) {
    const N = arr.length;
    const tmp = new Float32Array(N);
    const w = radius * 2 + 1;
    for (let i = 0; i < N; i++) {
      let sum = 0;
      for (let k = -radius; k <= radius; k++) sum += arr[(i + k + N) % N];
      tmp[i] = sum / w;
    }
    arr.set(tmp);
  }

/**
   * Uniform grid over the track bounds. Each cell lists the centreline indices
   * registered against it (and its neighbours), giving fast local searches.
   */
  _buildGrid() {
    let minX = Infinity, maxX = -Infinity, minZ = Infinity, maxZ = -Infinity;
    for (const p of this.points) {
      if (p.x < minX) minX = p.x;
      if (p.x > maxX) maxX = p.x;
      if (p.z < minZ) minZ = p.z;
      if (p.z > maxZ) maxZ = p.z;
    }
    const pad = this.width + this.vergeWidth + this.barrierWidth + 4;
    this.bounds = { minX: minX - pad, maxX: maxX + pad, minZ: minZ - pad, maxZ: maxZ + pad };

    const cell = this.opts.gridCell || 8;
    this.cellSize = cell;
    this.gridCols = Math.max(1, Math.ceil((this.bounds.maxX - this.bounds.minX) / cell));
    this.gridRows = Math.max(1, Math.ceil((this.bounds.maxZ - this.bounds.minZ) / cell));

    this.grid = new Array(this.gridCols * this.gridRows);
    for (let i = 0; i < this.grid.length; i++) this.grid[i] = [];

    for (let i = 0; i < this.count; i++) {
      const p = this.points[i];
      const c = this._cellCoord(p.x, p.z);
      for (let dz = -1; dz <= 1; dz++) {
        for (let dx = -1; dx <= 1; dx++) {
          const cx = c.col + dx;
          const cz = c.row + dz;
          if (cx < 0 || cz < 0 || cx >= this.gridCols || cz >= this.gridRows) continue;
          this.grid[cz * this.gridCols + cx].push(i);
        }
      }
    }
  }

  _cellCoord(x, z) {
    return {
      col: clamp(Math.floor((x - this.bounds.minX) / this.cellSize), 0, this.gridCols - 1),
      row: clamp(Math.floor((z - this.bounds.minZ) / this.cellSize), 0, this.gridRows - 1)
    };
  }

  _buildCheckpoints(count) {
    this.checkpoints = [];
    const stride = Math.max(1, Math.floor(this.count / count));
    for (let c = 0; c < count; c++) {
      const index = (c * stride) % this.count;
      this.checkpoints.push({
        index,
        t: index / this.count,
        position: this.points[index].clone(),
        radius: this.width * 1.6
      });
    }
    this.checkpointStride = stride;
    this.startIndex = 0;
    this.startPosition = this.points[0].clone();
    this.startHeading = this.headings[0];
  }

  /**
   * Nearest centreline sample index. A hint makes sequential per-frame lookups
   * cheap; if the hint search lands far away the car has respawned and we fall
   * back to the global grid search.
   */
  nearestIndex(x, z, hintIndex = -1) {
    if (hintIndex >= 0) {
      let best = hintIndex;
      let bestD = Infinity;
      const span = 30;
      for (let k = -span; k <= span; k++) {
        const i = (hintIndex + k + this.count) % this.count;
        const p = this.points[i];
        const dx = p.x - x;
        const dz = p.z - z;
        const d = dx * dx + dz * dz;
        if (d < bestD) {
          bestD = d;
          best = i;
        }
      }
      if (bestD < (this.width * 3) ** 2) return best;
    }
    return this.nearestIndexGlobal(x, z);
  }

  /** Global nearest-sample search by expanding rings around the grid cell. */
  nearestIndexGlobal(x, z) {
    const c = this._cellCoord(x, z);
    let best = 0;
    let bestD = Infinity;
    const consider = (i) => {
      const p = this.points[i];
      const dx = p.x - x;
      const dz = p.z - z;
      const d = dx * dx + dz * dz;
      if (d < bestD) {
        bestD = d;
        best = i;
      }
    };
    const maxRing = Math.max(this.gridCols, this.gridRows);
    for (let ring = 0; ring <= maxRing; ring++) {
      let found = false;
      for (let dz = -ring; dz <= ring; dz++) {
        for (let dx = -ring; dx <= ring; dx++) {
          if (ring > 0 && Math.abs(dx) !== ring && Math.abs(dz) !== ring) continue;
          const cx = c.col + dx;
          const cz = c.row + dz;
          if (cx < 0 || cz < 0 || cx >= this.gridCols || cz >= this.gridRows) continue;
          const cell = this.grid[cz * this.gridCols + cx];
          if (cell.length) found = true;
          for (const i of cell) consider(i);
        }
      }
      // Stop once the searched ring reaches further than the best candidate.
      if (found && ring * this.cellSize > Math.sqrt(bestD)) break;
    }
    return best;
  }

/** Full surface query for a world position. */
  query(x, z, hintIndex = -1) {
    const i = this.nearestIndex(x, z, hintIndex);
    const p = this.points[i];
    const t = this.tangents[i];
    const n = this.normals[i];

    const dx = x - p.x;
    const dz = z - p.z;
    // Signed lateral offset: positive to the left of the direction of travel.
    const lateral = dx * n.x + dz * n.z;
    const along = dx * t.x + dz * t.z;
    const absLateral = Math.abs(lateral);

    let surface;
    if (absLateral <= this.halfWidth) surface = SURFACE.TARMAC;
    else if (absLateral <= this.halfWidth + this.kerbWidth) surface = SURFACE.KERB;
    else if (absLateral <= this.halfWidth + this.kerbWidth + this.vergeWidth)
      surface = this.vergeSurface || this.defaultSurface;
    else surface = this.barrierSurface || SURFACE.GRASS;

    return {
      index: i,
      lateral,
      absLateral,
      side: lateral >= 0 ? 1 : -1,
      along,
      distance: this.distance[i] + along,
      surface,
      props: surfaceProps(surface),
      onTrack: absLateral <= this.halfWidth,
      onKerb: absLateral > this.halfWidth && absLateral <= this.halfWidth + this.kerbWidth,
      beyondTrack: absLateral > this.halfWidth + this.kerbWidth,
      atBarrier: absLateral > this.halfWidth + this.kerbWidth + this.vergeWidth,
      lateralNorm: clamp(lateral / this.halfWidth, -1, 1)
    };
  }

  /** World position at a centreline index, offset laterally. */
  pointAt(index, lateral = 0) {
    const i = this.wrapIndex(index);
    const p = this.points[i];
    const n = this.normals[i];
    return new THREE.Vector3(
      p.x + n.x * this.safeOffset(i, lateral),
      p.y,
      p.z + n.z * this.safeOffset(i, lateral)
    );
  }

  /**
   * Largest lateral offset at sample `i` that cannot fold back on itself.
   *
   * Offsetting a curve inward by more than its radius of curvature produces the
   * classic offset cusp, where the offset curve reverses direction. Because the
   * fold can span several adjacent samples, we take the tightest radius over a
   * small window rather than the radius at this sample alone.
   */
  safeOffset(i, lateral) {
    const N = this.count;
    // Inspect a window scaled to how far an offset can "see" around the curve.
    const window = Math.max(4, Math.round(N * 0.01));
    let minRadius = Infinity;
    for (let k = -window; k <= window; k++) {
      const j = this.wrapIndex(i + k);
      const kc = Math.abs(this.curvature[j]);
      if (kc > 1e-6) minRadius = Math.min(minRadius, 1 / kc);
    }
    const maxSafe = minRadius * 0.75;
    if (!isFinite(maxSafe)) return lateral;

    // Positive curvature turns left, so a positive offset moves toward the
    // inside of that corner; negative offsets are always on the safe outside.
    const inward = Math.sign(this.curvature[i]) === Math.sign(lateral);
    if (inward && Math.abs(lateral) > maxSafe) return Math.sign(lateral) * maxSafe;
    return lateral;
  }

  /** Interpolated centreline point at a fractional index. */
  pointAtFractional(fIndex) {
    const N = this.count;
    const f = ((fIndex % N) + N) % N;
    const i0 = Math.floor(f);
    const i1 = (i0 + 1) % N;
    const t = f - i0;
    return new THREE.Vector3().lerpVectors(this.points[i0], this.points[i1], t);
  }

  wrapIndex(index) {
    return ((Math.round(index) % this.count) + this.count) % this.count;
  }

  headingAt(index) { return this.headings[this.wrapIndex(index)]; }
  curvatureAt(index) { return this.curvature[this.wrapIndex(index)]; }

  /** Max cornering speed at an index: v = sqrt(a_lat * r), r = 1/|curvature|. */
  cornerSpeedLimit(index, gripScale = 1, latAccel = 9) {
    const k = Math.abs(this.curvatureAt(index));
    if (k < 1e-5) return Infinity;
    return Math.sqrt(latAccel * gripScale / k);
  }

  /** Shortest signed index delta from a to b around the loop. */
  indexDelta(a, b) {
    let d = b - a;
    const half = this.count / 2;
    while (d > half) d -= this.count;
    while (d < -half) d += this.count;
    return d;
  }

  /** Progress along the lap in 0..1. */
  progressAt(index) {
    return this.distance[this.wrapIndex(index)] / this.length;
  }

/**
   * Build an optimised racing line.
   *
   * Iteratively relaxes lateral offsets toward the inside of each corner
   * while clamping to the drivable width, then smooths the result and derives a
   * per-index target speed with a backward braking pass, so the AI slows *before*
   * a corner rather than in it.
   */
buildRacingLine(opts = {}) {
    const N = this.count;
    const limit = this.halfWidth * (opts.margin ?? 0.85);

    // Derive a target lateral offset from the centreline curvature, then smooth
    // that target. Because the offset profile is a smoothed function of
    // curvature, the resulting line is smooth by construction - no iterative
    // relaxation, and therefore no kinks where a relaxation would pin points
    // against the corridor wall.
    //
    // Sign: positive curvature turns left, so the inside of the corner is to
    // the right, i.e. a negative lateral offset.
    const offsets = new Float32Array(N);

    // Strength is referenced to a physically meaningful corner radius rather
    // than to the track's own peak curvature. Using the peak would mean a
    // single very tight corner raises the bar everywhere else and flattens the
    // rest of the line onto the centreline.
    const refK = 1 / (opts.cornerRadius ?? 45);

    for (let i = 0; i < N; i++) {
      const k = Math.abs(this.curvature[i]);
      // Saturate once the corner is tighter than the reference radius.
      const strength = clamp01((k - refK * 0.35) / (refK * 1.15));

      // Never offset further in than the centreline's own radius of curvature.
      // Inside a corner tighter than the offset distance, the offset curve folds
      // back on itself (the classic offset cusp) and produces points that double
      // over, which destroys any line built from it. Clamping to a fraction of
      // the local radius guarantees the offset always stays on the outside of
      // that fold.
      const radius = k > 1e-6 ? 1 / k : Infinity;
      const maxSafe = Math.min(limit, radius * 0.7);

      offsets[i] = -Math.sign(this.curvature[i]) * Math.min(limit * strength, maxSafe);
    }

    // Heavy wrapped smoothing. Two passes of a wide box filter approximate a
    // Gaussian and keep the profile continuous through corner entry/exit.
    this._smoothWrapped(offsets, 20);
    this._smoothWrapped(offsets, 20);
    this._smoothWrapped(offsets, 14);

    // Clamp after smoothing (smoothing preserves the range, so this is a guard).
    for (let i = 0; i < N; i++) offsets[i] = clamp(offsets[i], -limit, limit);

    // A gentle second smoothing after clamping removes any residual step at
    // the limit boundary, keeping the profile continuous. No slope limiter is
    // needed here: _buildSamples already caps the per-sample heading change,
    // which is what guarantees well-spaced offset points for any geometry.
    this._smoothWrapped(offsets, 6);
    for (let i = 0; i < N; i++) offsets[i] = clamp(offsets[i], -limit, limit);

    // World-space line points, exactly on the road surface.
    const points = new Array(N);
    const speeds = new Float32Array(N);
    for (let i = 0; i < N; i++) {
      const p = this.points[i];
      const n = this.normals[i];
      points[i] = new THREE.Vector3(p.x + n.x * offsets[i], p.y, p.z + n.z * offsets[i]);
    }

    // Target speed from the *line's own* curvature, so the AI slows for what
    // the line actually asks of the car.
    const latAccel = opts.latAccel ?? 9.5;
    const minDs = (this.length / N) * 0.2;
    const v1 = new THREE.Vector3();
    const v2 = new THREE.Vector3();
    for (let i = 0; i < N; i++) {
      v1.subVectors(points[i], points[(i - 1 + N) % N]);
      v2.subVectors(points[(i + 1) % N], points[i]);
      const ds = Math.max((v1.length() + v2.length()) * 0.5, minDs);
      let d = Math.atan2(v2.x, v2.z) - Math.atan2(v1.x, v1.z);
      while (d > Math.PI) d -= TAU;
      while (d < -Math.PI) d += TAU;
      const k = Math.abs(d) / ds;
      // Curvature beyond this is a modelling artefact, not a real corner.
      const maxK = 1 / 8; // ~8 m minimum radius
      speeds[i] = k < 1e-5 ? 65 : Math.min(Math.sqrt(latAccel / Math.min(k, maxK)), 65);
    }

    // Backward pass: entry speed is bounded by what we can decelerate from, so
    // the line carries a proper braking zone ahead of each corner.
    const decel = opts.decel ?? 15;
    for (let pass = 0; pass < 3; pass++) {
      for (let i = N - 1; i >= 0; i--) {
        const nxt = (i + 1) % N;
        const ds = Math.max(this.distance[i + 1] - this.distance[i], 0.01);
        const allowed = Math.sqrt(speeds[nxt] * speeds[nxt] + 2 * decel * ds);
        if (speeds[i] > allowed) speeds[i] = allowed;
      }
    }

    this.racingLine = { offsets, points, speeds };
    return this.racingLine;
  }
  /**
   * Redistribute any run of points that is bunched closer together than
   * `minSpacing`, interpolating along the local direction and re-clamping into
   * the track corridor. Prevents microscopic segments that would otherwise
   * produce meaningless peak-curvature readings.
   */
  _enforceSpacing(pts, minSpacing, limit) {
    const N = pts.length;
    const tmp = new THREE.Vector3();
    for (let pass = 0; pass < 4; pass++) {
      let fixed = 0;
      for (let i = 0; i < N; i++) {
        const j = (i + 1) % N;
        const d = pts[i].distanceTo(pts[j]);
        if (d >= minSpacing || d < 1e-6) continue;
        // Push j away from i along the i-1 -> i direction.
        const dir = tmp.subVectors(pts[i], pts[(i - 1 + N) % N]);
        if (dir.lengthSq() < 1e-8) dir.set(1, 0, 0);
        dir.normalize();
        const target = pts[i].clone().addScaledVector(dir, minSpacing);
        // Clamp into the corridor at sample j.
        const c = this.points[j];
        const n = this.normals[j];
        const lat = clamp(
          (target.x - c.x) * n.x + (target.z - c.z) * n.z,
          -limit,
          limit
        );
        pts[j].set(c.x + n.x * lat, c.y, c.z + n.z * lat);
        fixed++;
      }
      if (fixed === 0) break;
    }
  }

  /** Target speed from the line's own curvature, with a braking pass. */
  racingSpeedAt(index) {
    if (!this.racingLine) return 30;
    return this.racingLine.speeds[this.wrapIndex(index)];
  }

  racingPointAt(index) {
    if (!this.racingLine) return this.points[this.wrapIndex(index)];
    return this.racingLine.points[this.wrapIndex(index)];
  }

  racingOffsetAt(index) {
    if (!this.racingLine) return 0;
    return this.racingLine.offsets[this.wrapIndex(index)];
  }
}
