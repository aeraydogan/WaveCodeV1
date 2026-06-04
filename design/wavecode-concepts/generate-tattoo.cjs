/* WaveCode TATTOO designs — 5 compositions, same code, on skin.
 * SACRED CORE identical everywhere: 40 capsule bars (3 start + 34 data + 3 end),
 * solid high-contrast ink (tattoo-safe). All ornament stays OUTSIDE the quiet zone;
 * nothing between bars, nothing inside the decode zone.
 */
const fs = require("fs");
const path = require("path");
const OUT = __dirname;

const START = [0, 0, 1];
const END = [1, 0, 0];
const DATA = [5,2,6,3,7,4,1,5,3,6, 2,7,4,2,5,3,6,1,4,7, 3,5,2,6,4,1,7,3,5,2, 6,4,3,5];
const LEVELS = [...START, ...DATA, ...END];

const BW = 9, GAP = 8, N = LEVELS.length;
const MIN_BAR = 26, MAX_BAR = 200;
const CORE_W = N * BW + (N - 1) * GAP;     // 672
const W = 1080, H = 680;
const START_X = (W - CORE_W) / 2;          // 204
const CY = 296;
const HALF_MAX = MAX_BAR / 2;              // 100
const QUIET = 52;
// Quiet-zone box — NOTHING may be drawn inside this rectangle.
const QZ = { x0: START_X - QUIET, x1: START_X + CORE_W + QUIET, y0: CY - HALF_MAX - QUIET, y1: CY + HALF_MAX + QUIET }; // x:152..928 y:144..448

const barH = (l) => MIN_BAR + (l / 7) * (MAX_BAR - MIN_BAR);

// Solid, full-contrast core (tattoo-safe). Markers are solid too — distinguished by height pattern only.
function core(ink) {
  let r = "";
  for (let i = 0; i < N; i++) {
    const h = barH(LEVELS[i]);
    const x = START_X + i * (BW + GAP);
    r += `<rect x="${x.toFixed(1)}" y="${(CY - h / 2).toFixed(1)}" width="${BW}" height="${h.toFixed(1)}" rx="${BW / 2}" fill="${ink}"/>`;
  }
  return r;
}

const SKIN = `<defs>
  <radialGradient id="skin" cx="50%" cy="40%" r="85%">
    <stop offset="0%" stop-color="#F1CDA8"/>
    <stop offset="58%" stop-color="#E2B083"/>
    <stop offset="100%" stop-color="#C68E62"/>
  </radialGradient>
  <radialGradient id="vig" cx="50%" cy="42%" r="78%">
    <stop offset="62%" stop-color="#000" stop-opacity="0"/>
    <stop offset="100%" stop-color="#5A3B22" stop-opacity="0.30"/>
  </radialGradient>
  <filter id="ink" x="-5%" y="-5%" width="110%" height="110%"><feGaussianBlur stdDeviation="0.45"/></filter>
</defs>`;
const INK = "#211710";

const skinBg = `<rect width="${W}" height="${H}" fill="url(#skin)"/><rect width="${W}" height="${H}" fill="url(#vig)"/>`;
const inked = (s) => `<g filter="url(#ink)" opacity="0.94">${s}</g>`;
const wrap = (body) => `<svg xmlns="http://www.w3.org/2000/svg" width="${W}" height="${H}" viewBox="0 0 ${W} ${H}">${SKIN}${skinBg}${body}</svg>`;

// 1) PURE — just the WaveCode. Timeless, safest read.
function pure() {
  return wrap(inked(core(INK)));
}

// 2) NAME + DATE — typography only, well outside the decode zone (above & below quiet zone).
function nameDate() {
  const deco = `
    <text x="${W/2}" y="110" text-anchor="middle" font-family="'Helvetica Neue',Arial,sans-serif" font-size="22" letter-spacing="12" fill="${INK}" fill-opacity="0.85">12 · 04 · 2025</text>
    <text x="${W/2}" y="520" text-anchor="middle" font-family="'Segoe Script','Brush Script MT',cursive" font-size="72" fill="${INK}" fill-opacity="0.92">elif</text>`;
  return wrap(inked(core(INK) + deco));
}

// 3) FRAME — thin rectangle + corner ticks, drawn strictly outside the quiet zone.
function frame() {
  const fx0 = QZ.x0 - 14, fx1 = QZ.x1 + 14, fy0 = QZ.y0 - 12, fy1 = QZ.y1 + 12; // outside QZ
  const t = 14; // corner tick length
  const deco = `
    <rect x="${fx0}" y="${fy0}" width="${fx1 - fx0}" height="${fy1 - fy0}" rx="10" fill="none" stroke="${INK}" stroke-opacity="0.5" stroke-width="2"/>
    <g stroke="${INK}" stroke-width="4" stroke-linecap="round">
      <path d="M${fx0} ${fy0 + t} V${fy0} H${fx0 + t}"/>
      <path d="M${fx1 - t} ${fy0} H${fx1} V${fy0 + t}"/>
      <path d="M${fx0} ${fy1 - t} V${fy1} H${fx0 + t}"/>
      <path d="M${fx1 - t} ${fy1} H${fx1} V${fy1 - t}"/>
    </g>`;
  return wrap(inked(deco + core(INK)));
}

// 4) ENSO — hand-drawn broken circle enclosing the code, clearing the quiet zone on all sides.
function enso() {
  const cx = W / 2, cy = CY, rx = 432, ry = 206; // top 90 / bottom 502 / left 108 / right 972 — all clear of QZ
  // open-ended ellipse stroke (enso gap at top-right)
  const a0 = -1.15, a1 = Math.PI * 2 - 0.35; // radians, leaves a small opening
  const steps = 120;
  let d = "";
  for (let i = 0; i <= steps; i++) {
    const a = a0 + (a1 - a0) * (i / steps);
    const x = cx + rx * Math.cos(a);
    const y = cy + ry * Math.sin(a);
    d += (i === 0 ? "M" : "L") + x.toFixed(1) + " " + y.toFixed(1) + " ";
  }
  const deco = `<path d="${d}" fill="none" stroke="${INK}" stroke-opacity="0.55" stroke-width="6" stroke-linecap="round"/>`;
  return wrap(inked(deco + core(INK)));
}

// 5) HEARTBEAT TAIL — a thin ECG line with a small heart, entirely BELOW the quiet zone.
function heartbeat() {
  const y = QZ.y1 + 34; // 482, below quiet zone
  const x0 = 250, x1 = 760;
  const line = `M${x0} ${y} H${x0 + 150} l16 -34 l20 70 l18 -100 l22 130 l16 -66 H${x1}`;
  const hx = x1 + 26, hy = y; // little heart at the end
  const heart = `M${hx} ${hy + 6} c-10 -16 -34 -6 -22 12 l22 24 l22 -24 c12 -18 -12 -28 -22 -12 z`;
  const deco = `
    <path d="${line}" fill="none" stroke="${INK}" stroke-opacity="0.7" stroke-width="4" stroke-linecap="round" stroke-linejoin="round"/>
    <path d="${heart}" fill="${INK}" fill-opacity="0.85"/>`;
  return wrap(inked(core(INK) + deco));
}

const variants = [
  ["t1-pure", pure()],
  ["t2-name-date", nameDate()],
  ["t3-frame", frame()],
  ["t4-enso", enso()],
  ["t5-heartbeat", heartbeat()],
];
for (const [name, svg] of variants) fs.writeFileSync(path.join(OUT, `${name}.svg`), svg);

const cards = variants.map(([n]) => `<figure><img src="${n}.svg" alt="${n}"/><figcaption>${n}</figcaption></figure>`).join("\n");
fs.writeFileSync(path.join(OUT, "index-tattoo.html"),
`<!doctype html><meta charset="utf-8"><title>WaveCode tattoo designs</title>
<style>body{margin:0;background:#171513;font-family:Inter,Arial,sans-serif;color:#e7ddd2;padding:32px}
h1{font-weight:600;font-size:20px}figure{margin:0 0 32px}img{width:100%;max-width:760px;border-radius:14px;box-shadow:0 10px 44px #0009}
figcaption{margin:8px 2px;font-size:13px;color:#a99a8a;letter-spacing:1px}</style>
<h1>WaveCode — 5 dövme tasarımı (çekirdek sabit, aynı kod)</h1>${cards}`);

// quiet-zone safety assertion: ensure no decoration constant accidentally lands inside QZ (informational)
console.log("QZ x:", QZ.x0 + ".." + QZ.x1, "y:", QZ.y0 + ".." + QZ.y1);
console.log("Wrote:", variants.map(v => v[0] + ".svg").join(", "), "+ index-tattoo.html");