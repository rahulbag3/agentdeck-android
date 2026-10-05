import * as THREE from 'three';
import { proceduralMaterial } from './textures.js';
import { hexToRgb, lerp, mulberry32, pick, randRange, TAU } from './utils.js';

/* __CONSTANTS__ */

/** Road surface sits just above the ground plane to avoid z-fighting. */
const ROAD_Y = 0.02;
/** Kerbs only exist where the centreline is tighter than this curvature (1/m). */
const KERB_MIN_CURVATURE = 1 / 45;
/** Metres of kerb per colour block, alternating red / white. */
const KERB_ALTERNATE = 2;
const KERB_HEIGHT = 0.095;
const KERB_FOOT = 0.015;
const BARRIER_HEIGHT = 0.9;
const BARRIER_THICKNESS = 0.34;
const START_LINE_LENGTH = 3;
/** Chequer cell size of the start/finish line, in metres. */
const START_CELL = 0.5;
/** Track-side marker spacing, in centreline samples. */
const MARKER_SPACING = 15;

/* __THEMES__ */

const THEMES = {
  coastal: {
    groundTexture: 'grass',
    groundColor: 0xdfe7c6,
    groundTile: 18,
    objectCount: 320,
    runoffBias: 0.6,
    kerbRed: 0xc8281e,
    kerbWhite: 0xf2f2ee,
    banner: 0xd6453a
  },
  forest: {
    groundTexture: 'grass',
    groundColor: 0x9fc07a,
    groundTile: 18,
    objectCount: 380,
    runoffBias: 0.62,
    kerbRed: 0xb9241c,
    kerbWhite: 0xeceee4,
    banner: 0x2f7d4f
  },
  desert: {
    groundTexture: 'sand',
    groundColor: 0xf3e2c2,
    groundTile: 22,
    objectCount: 300,
    runoffBias: 0.55,
    kerbRed: 0xb03024,
    kerbWhite: 0xf6f1e2,
    banner: 0xe0a028
  },
  industrial: {
    groundTexture: 'concrete',
    groundColor: 0xa9a9a4,
    groundTile: 14,
    objectCount: 340,
    runoffBias: 0.66,
    kerbRed: 0xc02a1e,
    kerbWhite: 0xe9e9e4,
    banner: 0xe0e0e0
  }
};

const DEFAULT_THEME = 'coastal';
const DEFAULT_SEED = 1337;

/* __PALETTES__ */

/** Saturated container liveries, tinted onto a neutral metal albedo. */
const CONTAINER_COLOURS = [0xd43f2a, 0x2f6fd0, 0x2f9e5a, 0xe0a028, 0x8f3fb0, 0xd8d8d2, 0x1f8f96];
/** Beach-house render colours and roof tiles. */
const HOUSE_COLOURS = [0xf3ede2, 0xdfe9f0, 0xf7e3c8, 0xe6f0e4, 0xf0d8d4];
const ROOF_COLOURS = [0xb8483c, 0x2f6f8f, 0x8a8f6a, 0xc07a3a];
/** Foliage tones for canopies and bushes. */
const FOLIAGE_COLOURS = [0x3f7a35, 0x4f8f3e, 0x35682f, 0x5a9a46];
const CACTUS_COLOUR = 0x4c7a3c;
/* __HELPERS__ */

/** 0xRRGGBB to [r, g, b] in 0..1, for the 'plain' texture generator. */
const paintRgb = (hex) => {
  const c = hexToRgb('#' + hex.toString(16).padStart(6, '0'));
  return [c.r, c.g, c.b];
};

/** Raw vertex accumulator, used to weld many ribbon spans into one geometry. */
const accumulator = () => ({ pos: [], uv: [], idx: [], base: 0 });

/** Convert an accumulator into an indexed BufferGeometry. */
const accumulatorGeometry = (acc) => {
  const geo = new THREE.BufferGeometry();
  geo.setAttribute('position', new THREE.Float32BufferAttribute(acc.pos, 3));
  geo.setAttribute('uv', new THREE.Float32BufferAttribute(acc.uv, 2));
  geo.setIndex(
    acc.pos.length / 3 > 65535
      ? new THREE.Uint32BufferAttribute(acc.idx, 1)
      : new THREE.Uint16BufferAttribute(acc.idx, 1)
  );
  geo.computeVertexNormals();
  geo.computeBoundingSphere();
  return geo;
};

/**
 * Append one ribbon span (rings `rStart..rEnd`) to an accumulator.
 *
 * A ribbon is a strip swept along the centreline: every ring carries a
 * cross-section of `profile.length` vertices at [lateral, height]. Winding is
 * chosen per profile edge so that an edge whose lateral *decreases* faces +Y.
 * A closed cross-section ordered with its smallest lateral at the bottom, rising
 * at the largest lateral, then returning across the top therefore has
 * outward-facing normals on every face, on either side of the track.
 *
 * `wrap` additionally stitches the last profile edge back to the first, turning
 * an open strip into a closed tube (used for the barriers).
 */
const appendSpan = (acc, rings, profile, rStart, rEnd, opts = {}) => {
  const { uScale = 8, vScale = 8, wrap = false } = opts;
  const P = profile.length;
  const edges = wrap ? P : P - 1;

  // v runs along the cross-section so textures do not stretch around corners.
  const vAt = new Float32Array(P);
  for (let k = 1; k < P; k++) {
    vAt[k] =
      vAt[k - 1] +
      Math.hypot(profile[k][0] - profile[k - 1][0], profile[k][1] - profile[k - 1][1]);
  }

  for (let r = rStart; r <= rEnd; r++) {
    const ring = rings[r];
    for (let k = 0; k < P; k++) {
      acc.pos.push(
        ring.px + ring.nx * profile[k][0],
        profile[k][1],
        ring.pz + ring.nz * profile[k][0]
      );
      acc.uv.push(ring.dist / uScale, vAt[k] / vScale);
    }
  }

  for (let r = 0; r < rEnd - rStart; r++) {
    for (let k = 0; k < edges; k++) {
      const k1 = (k + 1) % P;
      // Row indices are span-relative: acc.base already carries the span offset.
      const A = acc.base + r * P + k;
      const B = acc.base + r * P + k1;
      const C = acc.base + (r + 1) * P + k;
      const D = acc.base + (r + 1) * P + k1;
      if (profile[k][0] >= profile[k1][0]) {
        acc.idx.push(A, C, B, B, C, D);
      } else {
        acc.idx.push(B, C, A, A, C, D);
      }
    }
  }

  acc.base += (rEnd - rStart + 1) * P;
};

/* __CLASS__ */

/**
 * Static circuit scenery: ground, road surface, kerbs, barriers, the
 * start/finish gantry, deterministic instanced scenery and track-side markers.
 *
 * Everything derives from the TrackPath samples plus a single seed, so a given
 * (track, theme, seed) triple always produces identical content. Repeated
 * scenery is drawn with InstancedMesh, one instanced mesh per part of each
 * object type, which keeps the whole circuit within a few dozen draw calls.
 */
export class SceneBuilder {
  /**
   * @param {import('../tracks/TrackPath.js').TrackPath} trackPath
   * @param {{theme?: string, seed?: number}} opts
   */
  constructor(trackPath, opts = {}) {
    this.trackPath = trackPath;
    this.opts = {
      theme: THEMES[opts.theme] ? opts.theme : DEFAULT_THEME,
      seed: opts.seed == null ? DEFAULT_SEED : opts.seed
    };
    this.theme = this.opts.theme;
    this.themeConfig = THEMES[this.theme];

    /** Shared materials, keyed by role and populated by build(). */
    this.materials = {};

    /** Distance from the centreline to the barrier centreline. */
    this.barrierLateral =
      trackPath.halfWidth + trackPath.kerbWidth + trackPath.vergeWidth * 0.75;

    this._group = null;
    this._geometries = new Set();
    this._materials = new Set();
    this._textures = new Set();
  }

  /* __LIFECYCLE__ */

  /** Build (or rebuild) the whole circuit. Safe to call more than once. */
  build() {
    if (this._group) this.dispose();

    this._geometries = new Set();
    this._materials = new Set();
    this._textures = new Set();
    this.materials = {};

    const group = new THREE.Group();
    group.name = `circuit-${this.theme}`;

    this._buildGround(group);
    this._buildRoad(group);
    this._buildKerbs(group);
    this._buildBarriers(group);
    this._buildStartFinish(group);
    this._buildScenery(group);
    this._buildMarkers(group);

    this._group = group;
    return group;
  }

  /** Dispose every geometry, material and texture this builder created. */
  dispose() {
    if (this._group) {
      this._group.clear();
      this._group = null;
    }
    this._geometries.forEach((g) => g.dispose());
    this._materials.forEach((m) => m.dispose());
    this._textures.forEach((t) => t.dispose());
    this._geometries.clear();
    this._materials.clear();
    this._textures.clear();
    this.materials = {};
  }

  /* __RESOURCES__ */

  /** Track a geometry for disposal and return it unchanged. */
  _geo(geometry) {
    this._geometries.add(geometry);
    return geometry;
  }

  /** Claim a material plus every texture it owns (clones, never the cache). */
  _ownMaterial(material) {
    this._materials.add(material);
    for (const value of Object.values(material)) {
      if (value && value.isTexture) this._textures.add(value);
    }
    return material;
  }

  /** Lazily create and cache a named material. */
  _mat(key, factory) {
    if (!this.materials[key]) {
      const mat = this._ownMaterial(factory());
      mat.name = key;
      this.materials[key] = mat;
    }
    return this.materials[key];
  }

  /** Flat painted material built from the 'plain' procedural texture. */
  _paint(key, hex, opts = {}) {
    return this._mat(key, () =>
      proceduralMaterial('plain', {
        args: [64, paintRgb(hex)],
        roughness: opts.roughness ?? 0.82,
        metalness: opts.metalness ?? 0.0
      })
    );
  }

  /** Neutral white base for parts that are tinted per instance. */
  _tintBase(key, opts = {}) {
    return this._mat(key, () =>
      proceduralMaterial('plain', {
        args: [64, [1, 1, 1]],
        roughness: opts.roughness ?? 0.78,
        metalness: opts.metalness ?? 0.0
      })
    );
  }

  /* __FRAMES__ */

  /**
   * Cross-section frames for centreline indices `from..to` inclusive. Indices
   * beyond `count` wrap to the start of the loop while the arc length keeps
   * increasing, so a closed ribbon can simply run from 0 to count.
   */
  _frames(from, to) {
    const tp = this.trackPath;
    const N = tp.count;
    const step = tp.length / N;
    const rings = [];
    for (let i = from; i <= to; i++) {
      const j = ((i % N) + N) % N;
      const p = tp.points[j];
      const n = tp.normals[j];
      rings.push({
        px: p.x,
        pz: p.z,
        nx: n.x,
        nz: n.z,
        dist: i <= N ? tp.distance[i] : tp.length + (i - N) * step
      });
    }
    return rings;
  }

  /** Interpolated frame at a fractional centreline index. */
  _frameAt(f) {
    const tp = this.trackPath;
    const N = tp.count;
    const ff = ((f % N) + N) % N;
    const i0 = Math.floor(ff);
    const i1 = (i0 + 1) % N;
    const t = ff - i0;
    const a = tp.points[i0];
    const b = tp.points[i1];
    const na = tp.normals[i0];
    const nb = tp.normals[i1];
    const nx = lerp(na.x, nb.x, t);
    const nz = lerp(na.z, nb.z, t);
    const len = Math.hypot(nx, nz) || 1;
    return {
      px: lerp(a.x, b.x, t),
      pz: lerp(a.z, b.z, t),
      nx: nx / len,
      nz: nz / len,
      dist: lerp(tp.distance[i0], tp.distance[i1], t)
    };
  }

  /* __TRACK_BUILDERS__ */

  /** Big textured plane under the circuit, sized from the track bounds. */
  _buildGround(group) {
    const tp = this.trackPath;
    let minX = Infinity;
    let maxX = -Infinity;
    let minZ = Infinity;
    let maxZ = -Infinity;
    for (const p of tp.points) {
      if (p.x < minX) minX = p.x;
      if (p.x > maxX) maxX = p.x;
      if (p.z < minZ) minZ = p.z;
      if (p.z > maxZ) maxZ = p.z;
    }

    const margin = 90;
    const cfg = this.themeConfig;
    const size = Math.max(maxX - minX + margin * 2, maxZ - minZ + margin * 2, 256);
    const repeat = [size / cfg.groundTile, size / cfg.groundTile];

    const geo = new THREE.PlaneGeometry(size, size, 1, 1);
    geo.rotateX(-Math.PI / 2);
    const mat = this._mat('ground', () =>
      proceduralMaterial(cfg.groundTexture, {
        color: cfg.groundColor,
        roughness: 0.95,
        repeat
      })
    );

    const mesh = new THREE.Mesh(this._geo(geo), mat);
    mesh.name = 'ground';
    mesh.position.set((minX + maxX) * 0.5, 0, (minZ + maxZ) * 0.5);
    mesh.receiveShadow = true;
    mesh.castShadow = false;
    group.add(mesh);
  }

  /** Asphalt ribbon: two vertices per centreline sample, indexed strip. */
  _buildRoad(group) {
    const tp = this.trackPath;
    const rings = this._frames(0, tp.count);
    const acc = accumulator();
    // Lateral decreases across the profile, which faces the strip +Y.
    appendSpan(acc, rings, [[tp.halfWidth, 0], [-tp.halfWidth, 0]], 0, rings.length - 1, {
      uScale: 8,
      vScale: tp.halfWidth * 2
    });

    const mat = this._mat('road', () =>
      proceduralMaterial('asphalt', { roughness: 0.9, repeat: [1, 1] })
    );
    const mesh = new THREE.Mesh(this._geo(accumulatorGeometry(acc)), mat);
    mesh.name = 'road';
    mesh.position.y = ROAD_Y;
    mesh.receiveShadow = true;
    mesh.castShadow = false;
    group.add(mesh);
  }

  /**
   * Kerbs, only through corners. Each corner gets a beveled kerb on the inside
   * of the bend, alternating red and white every `KERB_ALTERNATE` metres. The
   * colour blocks of every corner weld into one geometry per colour, so the
   * whole circuit costs exactly two kerb draw calls.
   */
  _buildKerbs(group) {
    const tp = this.trackPath;
    const half = tp.halfWidth;
    const kw = tp.kerbWidth;
    const kw2 = tp.kerbWidth * 0.5;

    // Contiguous runs of samples tight enough to warrant a kerb.
    const runs = [];
    let start = -1;
    for (let i = 0; i <= tp.count; i++) {
      const j = i % tp.count;
      const on = Math.abs(tp.curvature[j]) > KERB_MIN_CURVATURE;
      if (on && start < 0) start = i;
      if (!on && start >= 0) {
        runs.push([start, i - 1]);
        start = -1;
      }
    }
    if (start >= 0) runs.push([start, tp.count - 1]);

    const reds = accumulator();
    const whites = accumulator();

    for (const [i0, i1] of runs) {
      const dist0 = i0 <= tp.count ? tp.distance[i0] : tp.length;
      const dist1 = i1 <= tp.count ? tp.distance[i1] : tp.length;
      if (dist1 - dist0 < 1.2) continue; // too short to read as a kerb

      // Inside of the corner: opposite the sign of the curvature.
      const s = Math.sign(tp.curvature[i0]) || 1;
      const centre = -s * (half + kw2);
      const inner = centre + s * kw2;
      const outer = centre - s * kw2;
      const lo = Math.min(inner, outer);
      const hi = Math.max(inner, outer);
      // The road-side edge sits lower than the peak, which forms the bevel.
      const yLo = lo === inner ? 0.075 : 0.058;
      const yHi = hi === inner ? 0.075 : 0.058;
      const profile = [
        [lo, KERB_FOOT],
        [hi, KERB_FOOT],
        [hi, yHi],
        [centre, KERB_HEIGHT],
        [lo, yLo]
      ];

      const rings = this._frames(i0, i1 + 1);
      // Each colour block carries the trailing ring of the next block so that
      // no gap opens at a boundary.
      let block = -1;
      let spanStart = 0;
      const flush = (spanEnd) => {
        if (block < 0) return;
        appendSpan(block === 0 ? reds : whites, rings, profile, spanStart, spanEnd, {
          uScale: KERB_ALTERNATE,
          vScale: 1.2,
          wrap: true
        });
      };
      for (let r = 0; r < rings.length - 1; r++) {
        const mid = (rings[r].dist + rings[r + 1].dist) * 0.5;
        const c = Math.floor(mid / KERB_ALTERNATE) % 2;
        if (block === -1) {
          block = c;
          spanStart = r;
        } else if (c !== block) {
          flush(r);
          block = c;
          spanStart = r;
        }
      }
      flush(rings.length - 2);
    }

    if (!reds.idx.length && !whites.idx.length) return;

    const redMat = this._paint('kerbRed', this.themeConfig.kerbRed, { roughness: 0.6 });
    const whiteMat = this._paint('kerbWhite', this.themeConfig.kerbWhite, { roughness: 0.6 });
    for (const [acc, mat, name] of [
      [reds, redMat, 'kerbs-red'],
      [whites, whiteMat, 'kerbs-white']
    ]) {
      if (!acc.idx.length) continue;
      const mesh = new THREE.Mesh(this._geo(accumulatorGeometry(acc)), mat);
      mesh.name = name;
      mesh.receiveShadow = true;
      mesh.castShadow = true;
      group.add(mesh);
    }
  }

  /** Low concrete walls on both sides of the circuit, closing the diorama. */
  _buildBarriers(group) {
    const tp = this.trackPath;
    const rings = this._frames(0, tp.count);
    const t = BARRIER_THICKNESS * 0.5;
    const top = BARRIER_HEIGHT;
    const bevel = 0.11;

    for (const side of [-1, 1]) {
      const b = side * this.barrierLateral;
      // Smallest lateral at the bottom, up the far face, back across the top:
      // outward-facing normals on every edge, on either side of the track.
      const profile = [
        [b - t, 0],
        [b + t, 0],
        [b + t, top - 0.1],
        [b + t - bevel, top],
        [b - t + bevel, top],
        [b - t, top - 0.1]
      ];
      const acc = accumulator();
      appendSpan(acc, rings, profile, 0, rings.length - 1, {
        uScale: 4,
        vScale: 3,
        wrap: true
      });

      const mat = this._mat('barrier', () =>
        proceduralMaterial('concrete', { color: 0xd6d2c8, roughness: 0.92, repeat: [1, 1] })
      );
      const mesh = new THREE.Mesh(this._geo(accumulatorGeometry(acc)), mat);
      mesh.name = `barrier-${side > 0 ? 'left' : 'right'}`;
      mesh.castShadow = true;
      mesh.receiveShadow = true;
      group.add(mesh);
    }
  }
  /** Chequered start/finish line plus a gantry of two posts and a banner. */
  _buildStartFinish(group) {
    const tp = this.trackPath;
    const half = tp.halfWidth;
    const step = tp.length / tp.count;
    const rings = [
      this._frameAt((-START_LINE_LENGTH * 0.5) / step),
      this._frameAt((START_LINE_LENGTH * 0.5) / step)
    ];

    const acc = accumulator();
    const y = ROAD_Y + 0.025;
    appendSpan(acc, rings, [[half, y], [-half, y]], 0, 1, { uScale: 1, vScale: 1 });

    const mat = this._mat('startLine', () =>
      proceduralMaterial('checker', {
        roughness: 0.6,
        repeat: [(half * 2) / (START_CELL * 8), START_LINE_LENGTH / (START_CELL * 8)]
      })
    );
    const line = new THREE.Mesh(this._geo(accumulatorGeometry(acc)), mat);
    line.name = 'start-line';
    line.receiveShadow = true;
    group.add(line);

    // Gantry: metal posts either side of the road, banner spanning between them.
    const metal = this._mat('metal', () =>
      proceduralMaterial('metal', { roughness: 0.5, metalness: 0.55, repeat: [1, 1] })
    );
    const banner = this._paint('banner', this.themeConfig.banner, { roughness: 0.55 });

    const gantry = new THREE.Group();
    gantry.name = 'start-gantry';
    const heading = tp.headings[0];
    const origin = this._frameAt(0);
    const offset = half + 1.1;
    const postGeo = this._geo(new THREE.BoxGeometry(0.22, 3.6, 0.22));
    for (const side of [-1, 1]) {
      const post = new THREE.Mesh(postGeo, metal);
      post.name = `gantry-post-${side > 0 ? 'left' : 'right'}`;
      post.position.set(
        origin.px + origin.nx * offset * side,
        1.8,
        origin.pz + origin.nz * offset * side
      );
      post.rotation.y = heading;
      post.castShadow = true;
      post.receiveShadow = true;
      gantry.add(post);
    }

    const beamGeo = this._geo(new THREE.BoxGeometry(half * 2 + 2.6, 0.9, 0.14));
    const beam = new THREE.Mesh(beamGeo, banner);
    beam.name = 'gantry-banner';
    beam.position.set(origin.px, 3.25, origin.pz);
    // The banner's local X has to run along the track's lateral axis.
    beam.rotation.y = heading + Math.PI;
    beam.castShadow = true;
    beam.receiveShadow = true;
    gantry.add(beam);

    group.add(gantry);
  }

  /** Alternating red/white cones hugging the barrier line. */
  _buildMarkers(group) {
    const tp = this.trackPath;
    const lateral = this.barrierLateral + 0.34;
    const slots = { red: [], white: [] };

    for (let i = 0; i < tp.count; i += MARKER_SPACING) {
      const p = tp.points[i];
      const n = tp.normals[i];
      const parity = Math.floor(i / MARKER_SPACING) % 2;
      for (const side of [-1, 1]) {
        const key = (parity + (side > 0 ? 1 : 0)) % 2 === 0 ? 'red' : 'white';
        slots[key].push({
          x: p.x + n.x * lateral * side,
          z: p.z + n.z * lateral * side,
          yaw: tp.headings[i] + (side > 0 ? 0 : Math.PI)
        });
      }
    }

    const geo = this._geo(new THREE.ConeGeometry(0.19, 0.52, 7));
    const red = this._paint('markerRed', 0xd02a1c, { roughness: 0.55 });
    const white = this._paint('markerWhite', 0xf0f0ea, { roughness: 0.55 });
    const matrix = new THREE.Matrix4();
    const quaternion = new THREE.Quaternion();
    const up = new THREE.Vector3(0, 1, 0);
    const position = new THREE.Vector3();
    const one = new THREE.Vector3(1, 1, 1);

    for (const [key, mat, name] of [
      ['red', red, 'markers-red'],
      ['white', white, 'markers-white']
    ]) {
      const list = slots[key];
      if (!list.length) continue;
      const mesh = new THREE.InstancedMesh(this._geo(geo.clone()), mat, list.length);
      mesh.name = name;
      mesh.castShadow = true;
      mesh.receiveShadow = true;
      // Instances span the whole circuit, so geometry-only bounds cull them wrongly.
      mesh.frustumCulled = false;
      list.forEach((s, k) => {
        quaternion.setFromAxisAngle(up, s.yaw);
        position.set(s.x, 0.26, s.z);
        matrix.compose(position, quaternion, one);
        mesh.setMatrixAt(k, matrix);
      });
      mesh.instanceMatrix.needsUpdate = true;
      group.add(mesh);
    }
  }

  /* __SCENERY__ */

  /**
   * Object types for the active theme. Each entry declares a weight (share of
   * the object budget), whether it rotates freely or aligns to the track, an
   * optional per-instance tint palette, and a `build` returning the prototype
   * object that its instances are generated from.
   */
  _sceneryTypes() {
    switch (this.theme) {
      case 'forest':
        return this._forestTypes();
      case 'desert':
        return this._desertTypes();
      case 'industrial':
        return this._industrialTypes();
      default:
        return this._coastalTypes();
    }
  }

  /** Shared materials used by the scenery prototypes. */
  _rockMaterial() {
    return this._mat('rock', () => proceduralMaterial('rock', { roughness: 0.95 }));
  }

  _woodMaterial() {
    return this._mat('wood', () => proceduralMaterial('wood', { roughness: 0.88 }));
  }

  _metalMaterial(key = 'metal') {
    return this._mat(key, () =>
      proceduralMaterial('metal', { roughness: 0.5, metalness: 0.55, repeat: [1, 1] })
    );
  }

  /** Coastal: shoreline boulders, painted beach houses, driftwood fencing. */
  _coastalTypes() {
    const rock = this._rockMaterial();
    const wood = this._woodMaterial();
    const walls = this._tintBase('houseWall', { roughness: 0.8 });
    const roofs = this._tintBase('houseRoof', { roughness: 0.75 });

    return [
      {
        name: 'boulder',
        weight: 4,
        natural: true,
        scale: [0.75, 1.4],
        build: () => {
          const g = new THREE.Group();
          const a = new THREE.Mesh(new THREE.IcosahedronGeometry(0.62, 0), rock);
          a.position.y = 0.28;
          a.scale.set(1, 0.72, 1);
          const b = new THREE.Mesh(new THREE.IcosahedronGeometry(0.3, 0), rock);
          b.position.set(0.7, 0.14, 0.36);
          b.scale.set(1, 0.8, 1);
          g.add(a, b);
          return g;
        }
      },
      {
        name: 'beach-house',
        weight: 1,
        natural: false,
        align: 0.3,
        scale: [0.85, 1.15],
        tints: [
          { part: 0, palette: HOUSE_COLOURS },
          { part: 1, palette: ROOF_COLOURS }
        ],
        build: () => {
          const g = new THREE.Group();
          const body = new THREE.Mesh(new THREE.BoxGeometry(1.7, 1.15, 1.5), walls);
          body.position.y = 0.575;
          const roof = new THREE.Mesh(new THREE.ConeGeometry(1.42, 0.8, 4), roofs);
          roof.rotation.y = Math.PI / 4;
          roof.position.y = 1.55;
          g.add(body, roof);
          return g;
        }
      },
      {
        name: 'fence',
        weight: 2,
        natural: false,
        align: 0.12,
        scale: [0.9, 1.1],
        build: () => {
          const g = new THREE.Group();
          const post = new THREE.Mesh(new THREE.CylinderGeometry(0.07, 0.08, 1.1, 6), wood);
          post.position.y = 0.55;
          const post2 = new THREE.Mesh(new THREE.CylinderGeometry(0.06, 0.07, 0.95, 6), wood);
          post2.position.set(1.7, 0.475, 0);
          const rail = new THREE.Mesh(new THREE.BoxGeometry(1.8, 0.09, 0.05), wood);
          rail.position.y = 0.82;
          g.add(post, post2, rail);
          return g;
        }
      }
    ];
  }

  /** Forest: conifers, low bushes and split-rail fencing. */
  _forestTypes() {
    const wood = this._woodMaterial();
    const trunk = this._mat('treeTrunk', () => proceduralMaterial('wood', { color: 0x9a7a55 }));
    const canopy = this._tintBase('foliage', { roughness: 0.9 });

    return [
      {
        name: 'tree',
        weight: 4,
        natural: true,
        scale: [0.75, 1.35],
        tints: [
          { part: 1, palette: FOLIAGE_COLOURS },
          { part: 2, palette: FOLIAGE_COLOURS }
        ],
        build: () => {
          const g = new THREE.Group();
          const stem = new THREE.Mesh(new THREE.CylinderGeometry(0.15, 0.2, 1.5, 6), trunk);
          stem.position.y = 0.75;
          const crown = new THREE.Mesh(new THREE.ConeGeometry(0.95, 2.0, 7), canopy);
          crown.position.y = 2.3;
          const tip = new THREE.Mesh(new THREE.SphereGeometry(0.5, 6, 4), canopy);
          tip.position.y = 3.25;
          tip.scale.set(1, 0.85, 1);
          g.add(stem, crown, tip);
          return g;
        }
      },
      {
        name: 'bush',
        weight: 3,
        natural: true,
        scale: [0.7, 1.4],
        tints: [{ part: 0, palette: FOLIAGE_COLOURS }],
        build: () => {
          const g = new THREE.Group();
          const body = new THREE.Mesh(new THREE.IcosahedronGeometry(0.45, 0), canopy);
          body.position.y = 0.34;
          body.scale.set(1.2, 0.8, 1.1);
          g.add(body);
          return g;
        }
      },
      {
        name: 'fence',
        weight: 2,
        natural: false,
        align: 0.12,
        scale: [0.9, 1.1],
        build: () => {
          const g = new THREE.Group();
          const post = new THREE.Mesh(new THREE.CylinderGeometry(0.08, 0.09, 1.2, 6), wood);
          post.position.y = 0.6;
          const rail = new THREE.Mesh(new THREE.BoxGeometry(1.9, 0.1, 0.06), wood);
          rail.position.y = 0.88;
          g.add(post, rail);
          return g;
        }
      }
    ];
  }
  /** Desert: weathered rock outcrops, saguaro cacti and leaning fence posts. */
  _desertTypes() {
    const rock = this._rockMaterial();
    const wood = this._woodMaterial();
    const cactus = this._paint('cactus', CACTUS_COLOUR, { roughness: 0.85 });

    return [
      {
        name: 'outcrop',
        weight: 3,
        natural: true,
        scale: [0.7, 1.5],
        build: () => {
          const g = new THREE.Group();
          const a = new THREE.Mesh(new THREE.DodecahedronGeometry(0.9, 0), rock);
          a.position.y = 0.55;
          a.scale.set(1, 1.2, 1);
          const b = new THREE.Mesh(new THREE.IcosahedronGeometry(0.45, 0), rock);
          b.position.set(0.78, 0.24, 0.3);
          b.scale.set(1.1, 0.9, 1);
          g.add(a, b);
          return g;
        }
      },
      {
        name: 'cactus',
        weight: 2,
        natural: true,
        scale: [0.75, 1.25],
        build: () => {
          const g = new THREE.Group();
          const stem = new THREE.Mesh(new THREE.CylinderGeometry(0.22, 0.26, 2.3, 7), cactus);
          stem.position.y = 1.15;
          const armA = new THREE.Mesh(new THREE.CylinderGeometry(0.12, 0.12, 0.85, 6), cactus);
          armA.rotation.z = Math.PI / 2;
          armA.position.set(0.3, 1.3, 0);
          const armB = new THREE.Mesh(new THREE.CylinderGeometry(0.11, 0.11, 0.7, 6), cactus);
          armB.rotation.z = Math.PI / 2;
          armB.position.set(-0.26, 0.85, 0);
          g.add(stem, armA, armB);
          return g;
        }
      },
      {
        name: 'post',
        weight: 2,
        natural: false,
        align: 0.15,
        scale: [0.85, 1.2],
        build: () => {
          const g = new THREE.Group();
          const post = new THREE.Mesh(new THREE.CylinderGeometry(0.09, 0.11, 1.3, 6), wood);
          post.position.y = 0.65;
          const rail = new THREE.Mesh(new THREE.BoxGeometry(1.4, 0.08, 0.05), wood);
          rail.position.y = 0.72;
          g.add(post, rail);
          return g;
        }
      }
    ];
  }

  /** Industrial: warehouses, shipping containers and pipe runs. */
  _industrialTypes() {
    const metal = this._metalMaterial();
    const roofPaint = this._paint('warehouseRoof', 0x8a8f96, { roughness: 0.6, metalness: 0.4 });

    return [
      {
        name: 'warehouse',
        weight: 2,
        natural: false,
        align: 0.2,
        scale: [0.8, 1.2],
        build: () => {
          const g = new THREE.Group();
          const body = new THREE.Mesh(new THREE.BoxGeometry(6.4, 3.0, 4.2), metal);
          body.position.y = 1.5;
          const roof = new THREE.Mesh(new THREE.BoxGeometry(6.7, 0.3, 4.5), roofPaint);
          roof.position.y = 3.15;
          const door = new THREE.Mesh(new THREE.BoxGeometry(2.2, 2.2, 0.12), roofPaint);
          door.position.set(0, 1.1, 2.12);
          g.add(body, roof, door);
          return g;
        }
      },
      {
        name: 'container',
        weight: 4,
        natural: false,
        align: 0.5,
        scale: [0.85, 1.2],
        tints: [{ part: 0, palette: CONTAINER_COLOURS }],
        build: () => {
          const g = new THREE.Group();
          const box = new THREE.Mesh(new THREE.BoxGeometry(2.5, 1.25, 1.3), metal);
          box.position.y = 0.63;
          g.add(box);
          return g;
        }
      },
      {
        name: 'pipes',
        weight: 3,
        natural: false,
        align: 0.6,
        scale: [0.85, 1.25],
        build: () => {
          const g = new THREE.Group();
          const low = new THREE.Mesh(new THREE.CylinderGeometry(0.34, 0.34, 4.2, 8), metal);
          low.rotation.z = Math.PI / 2;
          low.position.y = 0.4;
          const high = new THREE.Mesh(new THREE.CylinderGeometry(0.26, 0.26, 4.2, 8), metal);
          high.rotation.z = Math.PI / 2;
          high.position.y = 0.82;
          g.add(low, high);
          return g;
        }
      }
    ];
  }
  /**
   * Flatten a prototype object into one geometry per mesh part, baking each
   * part's local offset and rotation into the geometry so a single instance
   * matrix can drive the whole object.
   */
  _harvest(proto) {
    proto.updateMatrixWorld(true);
    const parts = [];
    proto.traverse((o) => {
      if (!o.isMesh) return;
      o.updateMatrix();
      const geometry = o.geometry.clone();
      geometry.applyMatrix4(o.matrix);
      o.geometry.dispose();
      parts.push({ geometry, material: o.material });
    });
    return parts;
  }

  /**
   * Deterministic scatter of `count` placement slots across the circuit: most
   * objects land in the run-off between the road and the barriers, the rest
   * spread out beyond the barriers. A coarse spatial hash keeps objects from
   * stacking on top of one another.
   */
  _scatter(rng, count) {
    const tp = this.trackPath;
    const N = tp.count;
    const roadEdge = tp.halfWidth + tp.kerbWidth;
    const barrier = this.barrierLateral;
    const inner = [roadEdge + 0.7, Math.max(barrier - 0.4, roadEdge + 1.4)];
    const outer = [barrier + 1.8, barrier + 58];
    const cells = new Set();
    const cellSize = 2.2;
    const out = [];

    for (let guard = 0; out.length < count && guard < count * 16; guard++) {
      const side = rng() < 0.5 ? -1 : 1;
      const band = rng() < this.themeConfig.runoffBias ? inner : outer;
      const i = Math.floor(rng() * N) % N;
      const lateral = side * randRange(rng, band[0], band[1]);
      const p = tp.points[i];
      const n = tp.normals[i];
      const x = p.x + n.x * lateral;
      const z = p.z + n.z * lateral;
      const cell = `${Math.round(x / cellSize)}:${Math.round(z / cellSize)}`;
      if (cells.has(cell)) continue;
      cells.add(cell);
      out.push({
        x,
        z,
        heading: tp.headings[i],
        spin: randRange(rng, -1, 1),
        scale: randRange(rng, 0.8, 1.2)
      });
    }

    return out;
  }

  /** Instance every scenery type and add it to the group. */
  _buildScenery(group) {
    const rng = mulberry32(this.opts.seed);
    const types = this._sceneryTypes();
    const placements = this._scatter(rng, this.themeConfig.objectCount);

    // Shuffle so the types interleave instead of forming a band along the loop.
    for (let k = placements.length - 1; k > 0; k--) {
      const j = Math.floor(rng() * (k + 1));
      const tmp = placements[k];
      placements[k] = placements[j];
      placements[j] = tmp;
    }

    const sumWeight = types.reduce((sum, t) => sum + t.weight, 0);
    const matrix = new THREE.Matrix4();
    const quaternion = new THREE.Quaternion();
    const position = new THREE.Vector3();
    const scale = new THREE.Vector3();
    const up = new THREE.Vector3(0, 1, 0);
    const range = (type) => (type.scale ? type.scale : [0.85, 1.25]);
    let cursor = 0;

    for (let t = 0; t < types.length; t++) {
      const type = types[t];
      const count =
        t === types.length - 1
          ? placements.length - cursor
          : Math.round((placements.length * type.weight) / sumWeight);
      if (count <= 0) continue;
      const slice = placements.slice(cursor, cursor + count);
      cursor += count;

      const parts = this._harvest(type.build());
      parts.forEach((part, partIndex) => {
        const tint = (type.tints || []).find((entry) => entry.part === partIndex);
        const mesh = new THREE.InstancedMesh(this._geo(part.geometry), part.material, slice.length);
        mesh.name = `scenery-${type.name}`;
        mesh.castShadow = true;
        mesh.receiveShadow = true;
        // Instances span the whole circuit, so geometry-only bounds cull them wrongly.
        mesh.frustumCulled = false;
        const colours = tint ? new Float32Array(slice.length * 3) : null;

        slice.forEach((p, k) => {
          const yaw = type.natural
            ? randRange(rng, 0, TAU)
            : p.heading + p.spin * (type.align ?? 0.4);
          const s = p.scale * randRange(rng, range(type)[0], range(type)[1]);
          quaternion.setFromAxisAngle(up, yaw);
          position.set(p.x, 0, p.z);
          scale.set(s, s * randRange(rng, 0.9, 1.15), s);
          matrix.compose(position, quaternion, scale);
          mesh.setMatrixAt(k, matrix);
          if (colours) {
            const c = paintRgb(pick(rng, tint.palette));
            colours[k * 3] = c[0];
            colours[k * 3 + 1] = c[1];
            colours[k * 3 + 2] = c[2];
          }
        });

        mesh.instanceMatrix.needsUpdate = true;
        if (colours) {
          mesh.instanceColor = new THREE.InstancedBufferAttribute(colours, 3);
          mesh.instanceColor.needsUpdate = true;
        }
        group.add(mesh);
      });
    }
  }
}