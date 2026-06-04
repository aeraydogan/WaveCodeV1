/* WaveCode concept generator — 5 premium presentation themes.
 * SACRED CORE is identical in every image: 40 vertically-centered capsule bars
 * (3 start markers + 34 data + 3 end markers), 8 levels, high contrast.
 * Themes only decorate OUTSIDE the core's quiet zone. Nothing is drawn between bars.
 */
const fs = require("fs");
const path = require("path");

const OUT = __dirname;

// ---- Sacred core data (fixed) ----
const START = [0, 0, 1];
const END = [1, 0, 0];
const DATA = [5,2,6,3,7,4,1,5,3,6, 2,7,4,2,5,3,6,1,4,7, 3,5,2,6,4,1,7,3,5,2, 6,4,3,5]; // 34
const LEVELS = [...START, ...DATA, ...END]; // 40
const CODE = "A7K29XQ4";

// ---- Core geometry (identical across themes) ----
const BW = 9;            // bar width
const GAP = 8;           // gap
const N = LEVELS.length; // 40
const MIN_BAR = 26;
const MAX_BAR = 200;
const CORE_W = N * BW + (N - 1) * GAP;       // 672
const W = 1080, H = 680;
const START_X = (W - CORE_W) / 2;            // left edge of core
const CY = 296;                              // vertical center of bars
const HALF_MAX = MAX_BAR / 2;                // 100
const QUIET = 52;                            // quiet zone padding around core (kept clear)

const barH = (lvl) => MIN_BAR + (lvl / 7) * (MAX_BAR - MIN_BAR);

function core(fill) {
  let r = "";
  for (let i = 0; i < N; i++) {
    const h = barH(LEVELS[i]);
    const x = START_X + i * (BW + GAP);
    const y = CY - h / 2;
    const rx = BW / 2;
    const isMarker = i < 3 || i >= N - 3;
    const op = isMarker ? 0.55 : 1; // markers slightly lighter — still part of sacred core, just visual finder hint
    r += `<rect x="${x.toFixed(1)}" y="${y.toFixed(1)}" width="${BW}" height="${h.toFixed(1)}" rx="${rx}" fill="${fill}" fill-opacity="${op}"/>`;
  }
  return r;
}

// quiet-zone box (for reference / debugging frames) — themes must stay outside this
const QZ = {
  x: START_X - QUIET, y: CY - HALF_MAX - QUIET,
  w: CORE_W + QUIET * 2, h: MAX_BAR + QUIET * 2,
};

const svgWrap = (defs, body) =>
  `<svg xmlns="http://www.w3.org/2000/svg" width="${W}" height="${H}" viewBox="0 0 ${W} ${H}">${defs}${body}</svg>`;

// ---------- 1. MONO MINIMAL ----------
function mono() {
  const ink = "#0B0B0C";
  const body = `
    <rect width="${W}" height="${H}" fill="#FFFFFF"/>
    <rect x="20" y="20" width="${W - 40}" height="${H - 40}" rx="28" fill="none" stroke="#ECECEC" stroke-width="2"/>
    <text x="${W/2}" y="120" text-anchor="middle" font-family="Inter, Arial, sans-serif" font-size="22" letter-spacing="6" fill="#9A9A9A">S E S   K O D U</text>
    ${core(ink)}
    <text x="${W/2}" y="540" text-anchor="middle" font-family="ui-monospace, 'SF Mono', Menlo, monospace" font-size="46" letter-spacing="14" fill="${ink}">${CODE}</text>
    <text x="${W/2}" y="588" text-anchor="middle" font-family="Inter, Arial, sans-serif" font-size="18" letter-spacing="2" fill="#B5B5B5">Dövmene hazır • WaveCode</text>`;
  return svgWrap("", body);
}

// ---------- 2. SKIN / TATTOO PREVIEW ----------
function skin() {
  const ink = "#241A12";
  const defs = `<defs>
    <radialGradient id="skin" cx="50%" cy="38%" r="80%">
      <stop offset="0%" stop-color="#F0CBA6"/>
      <stop offset="55%" stop-color="#E3B488"/>
      <stop offset="100%" stop-color="#C9966B"/>
    </radialGradient>
    <filter id="ink"><feGaussianBlur stdDeviation="0.5"/></filter>
    <radialGradient id="vig" cx="50%" cy="42%" r="75%">
      <stop offset="60%" stop-color="#000000" stop-opacity="0"/>
      <stop offset="100%" stop-color="#5A3B22" stop-opacity="0.28"/>
    </radialGradient>
  </defs>`;
  const body = `
    <rect width="${W}" height="${H}" fill="url(#skin)"/>
    <rect width="${W}" height="${H}" fill="url(#vig)"/>
    <g filter="url(#ink)" opacity="0.92">${core(ink)}</g>
    <text x="${W/2}" y="560" text-anchor="middle" font-family="Inter, Arial, sans-serif" font-size="20" letter-spacing="8" fill="#3A281B" fill-opacity="0.8">D Ö V M E   Ö N İ Z L E M E</text>`;
  return svgWrap(defs, body);
}

// ---------- 3. DARK PREMIUM (Spotify-like) ----------
function dark() {
  const defs = `<defs>
    <linearGradient id="frame" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0%" stop-color="#1DB954"/>
      <stop offset="100%" stop-color="#37E0A0"/>
    </linearGradient>
  </defs>`;
  const chipY = 506, chipW = 360, chipX = (W - chipW) / 2;
  const body = `
    <rect width="${W}" height="${H}" fill="#0E0E10"/>
    <rect x="22" y="22" width="${W - 44}" height="${H - 44}" rx="30" fill="none" stroke="url(#frame)" stroke-width="2" opacity="0.55"/>
    <text x="${W/2}" y="118" text-anchor="middle" font-family="Inter, Arial, sans-serif" font-size="22" letter-spacing="6" fill="#7A7A82">S E S   K O D U</text>
    ${core("#FFFFFF")}
    <g>
      <rect x="${chipX}" y="${chipY}" width="${chipW}" height="64" rx="32" fill="#17171B" stroke="#262630" stroke-width="1.5"/>
      <circle cx="${chipX + 38}" cy="${chipY + 32}" r="15" fill="#1DB954"/>
      <path d="M${chipX + 31} ${chipY + 32} l5 5 l9 -10" fill="none" stroke="#0E0E10" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"/>
      <text x="${chipX + 70}" y="${chipY + 42}" font-family="ui-monospace, Menlo, monospace" font-size="34" letter-spacing="8" fill="#FFFFFF">${CODE}</text>
    </g>
    <text x="${W/2}" y="616" text-anchor="middle" font-family="Inter, Arial, sans-serif" font-size="17" letter-spacing="2" fill="#5C5C66">Kopyalandı • WaveCode</text>`;
  return svgWrap(defs, body);
}

// ---------- 4. BENTO PRODUCT CARD ----------
function bento() {
  const ink = "#101114";
  const cardX = 70, cardY = 70, cardW = W - 140, cardH = H - 140;
  const pillW = 330, pillX = (W - pillW) / 2, pillY = 500;
  const body = `
    <rect width="${W}" height="${H}" fill="#F4F5F7"/>
    <rect x="${cardX}" y="${cardY}" width="${cardW}" height="${cardH}" rx="34" fill="#FFFFFF" stroke="#E7E9EE" stroke-width="2"/>
    <g transform="translate(${cardX + 40}, ${cardY + 60})">
      <rect x="0" y="-22" width="6" height="14" rx="3" fill="${ink}"/>
      <rect x="11" y="-30" width="6" height="30" rx="3" fill="${ink}"/>
      <rect x="22" y="-26" width="6" height="22" rx="3" fill="${ink}"/>
      <text x="44" y="-8" font-family="Inter, Arial, sans-serif" font-size="26" font-weight="700" fill="${ink}">WaveCode</text>
    </g>
    <text x="${cardX + cardW - 40}" y="${cardY + 52}" text-anchor="end" font-family="Inter, Arial, sans-serif" font-size="18" letter-spacing="3" fill="#AEB3BD">SES İZİ</text>
    ${core(ink)}
    <g>
      <rect x="${pillX}" y="${pillY}" width="${pillW}" height="60" rx="30" fill="#F1F3F6"/>
      <text x="${pillX + 28}" y="${pillY + 39}" font-family="ui-monospace, Menlo, monospace" font-size="30" letter-spacing="7" fill="${ink}">${CODE}</text>
      <circle cx="${pillX + pillW - 34}" cy="${pillY + 30}" r="14" fill="#16A34A"/>
      <path d="M${pillX + pillW - 41} ${pillY + 30} l5 5 l9 -10" fill="none" stroke="#FFFFFF" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"/>
    </g>`;
  return svgWrap("", body);
}

// ---------- 5. EDITORIAL LUXE (gold) ----------
function luxe() {
  const ink = "#15110A";
  const gold = "#C8A24B";
  const body = `
    <rect width="${W}" height="${H}" fill="#F7F2E8"/>
    <rect x="30" y="30" width="${W - 60}" height="${H - 60}" rx="6" fill="none" stroke="${gold}" stroke-width="2"/>
    <rect x="40" y="40" width="${W - 80}" height="${H - 80}" rx="4" fill="none" stroke="${gold}" stroke-width="1" opacity="0.6"/>
    <text x="${W/2}" y="116" text-anchor="middle" font-family="Georgia, 'Times New Roman', serif" font-size="26" letter-spacing="10" fill="${gold}">SES KODU</text>
    <line x1="${W/2 - 60}" y1="138" x2="${W/2 + 60}" y2="138" stroke="${gold}" stroke-width="1"/>
    ${core(ink)}
    <text x="${W/2}" y="548" text-anchor="middle" font-family="Georgia, 'Times New Roman', serif" font-size="42" letter-spacing="16" fill="${ink}">${CODE}</text>
    <text x="${W/2}" y="592" text-anchor="middle" font-family="Georgia, serif" font-style="italic" font-size="19" fill="#9A8A66">sesini sonsuza taşı</text>`;
  return svgWrap("", body);
}

const variants = [
  ["01-mono-minimal", mono()],
  ["02-skin-tattoo", skin()],
  ["03-dark-premium", dark()],
  ["04-bento-card", bento()],
  ["05-editorial-luxe", luxe()],
];

for (const [name, svg] of variants) {
  fs.writeFileSync(path.join(OUT, `${name}.svg`), svg);
}

// contact sheet
const cards = variants
  .map(([name]) => `<figure><img src="${name}.svg" alt="${name}"/><figcaption>${name}</figcaption></figure>`)
  .join("\n");
const html = `<!doctype html><meta charset="utf-8"><title>WaveCode concepts</title>
<style>body{margin:0;background:#1b1b1f;font-family:Inter,Arial,sans-serif;color:#ddd;padding:32px}
h1{font-weight:600;font-size:20px}figure{margin:0 0 32px}img{width:100%;max-width:760px;border-radius:14px;box-shadow:0 8px 40px #0008}
figcaption{margin:8px 2px;font-size:13px;color:#9a9aa2;letter-spacing:1px}</style>
<h1>WaveCode — 5 görsel konsept (çekirdek sabit)</h1>${cards}`;
fs.writeFileSync(path.join(OUT, "index.html"), html);

console.log("Bars:", N, "| core width:", CORE_W, "| levels:", LEVELS.join(""));
console.log("Wrote:", variants.map((v) => v[0] + ".svg").join(", "), "+ index.html");