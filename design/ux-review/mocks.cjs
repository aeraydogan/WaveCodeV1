/* WaveCode UX redesign mocks — built on the real "Ink & Signal" tokens.
 * Phone canvas 390x844. Pure visual proposals (not app code).
 */
const fs = require("fs");
const path = require("path");
const OUT = __dirname;
const W = 390, H = 844;

// --- real tokens (from ui/theme/Color.kt) ---
const C = {
  canvas: "#000000", s1: "#121214", s2: "#1C1C20", s3: "#26262B", outline: "#2E2E34",
  tPrim: "#ECECEC", tSec: "#A0A0A6", tMut: "#6E6E76",
  accent: "#C8FF3D", onAccent: "#0A0A0A", rec: "#FF4D4D", successCard: "#15240C", skin: "#E8C4A0",
};
const SANS = "Inter, 'Helvetica Neue', Arial, sans-serif";
const MONO = "ui-monospace, 'SF Mono', Menlo, monospace";

const esc = (s) => String(s).replace(/&/g, "&amp;").replace(/</g, "&lt;");
const rect = (x, y, w, h, r, fill, stroke, sw) =>
  `<rect x="${x}" y="${y}" width="${w}" height="${h}" rx="${r}" fill="${fill}"${stroke ? ` stroke="${stroke}" stroke-width="${sw || 1}"` : ""}/>`;
const text = (x, y, s, o = {}) =>
  `<text x="${x}" y="${y}" font-family="${o.mono ? MONO : SANS}" font-size="${o.size || 14}" font-weight="${o.w || 400}" fill="${o.fill || C.tPrim}"${o.anchor ? ` text-anchor="${o.anchor}"` : ""}${o.ls ? ` letter-spacing="${o.ls}"` : ""}>${esc(s)}</text>`;

// mini waveform bars (decorative), centered in a band
function wave(x, y, w, band, heights, color, bw = 5, gap = 4) {
  const n = heights.length;
  const span = n * bw + (n - 1) * gap;
  const sx = x + (w - span) / 2;
  let r = "";
  for (let i = 0; i < n; i++) {
    const bh = Math.max(4, band * heights[i]);
    const bx = sx + i * (bw + gap);
    r += `<rect x="${bx.toFixed(1)}" y="${(y + (band - bh) / 2).toFixed(1)}" width="${bw}" height="${bh.toFixed(1)}" rx="${bw / 2}" fill="${color}"/>`;
  }
  return r;
}
const HS = [0.25,0.45,0.7,1,0.8,0.55,0.35,0.6,0.9,0.65,0.4,0.75,1,0.5,0.3,0.55,0.85,0.6,0.4,0.7,0.95,0.5,0.3,0.6];

// pill button
function pill(x, y, w, h, label, kind, icon) {
  const fill = kind === "accent" ? C.accent : kind === "tonal" ? C.s3 : "none";
  const txt = kind === "accent" ? C.onAccent : C.tPrim;
  const stroke = kind === "outline" ? C.outline : null;
  let g = rect(x, y, w, h, h / 2, fill, stroke, 1.5);
  const cx = x + w / 2;
  g += text(cx + (icon ? 10 : 0), y + h / 2 + 5, label, { size: 15, w: 600, fill: txt, anchor: "middle" });
  if (icon) g += icon(cx - txt.length - (label.length * 4) - 14, y + h / 2, txt);
  return g;
}
// simple glyphs
const micGlyph = (x, y, c) => `<g stroke="${c}" stroke-width="2" fill="none" stroke-linecap="round"><rect x="${x-4}" y="${y-9}" width="8" height="13" rx="4" fill="${c}" stroke="none"/><path d="M${x-7} ${y} a7 7 0 0 0 14 0"/><path d="M${x} ${y+7} v4"/></g>`;
const scanGlyph = (x, y, c) => `<g stroke="${c}" stroke-width="2" fill="none" stroke-linecap="round"><path d="M${x-8} ${y-5} v-3 h3 M${x+8} ${y-5} v-3 h-3 M${x-8} ${y+5} v3 h3 M${x+8} ${y+5} v3 h-3"/><path d="M${x-7} ${y} h14"/></g>`;
const playGlyph = (x, y, c) => `<path d="M${x-5} ${y-8} l13 8 l-13 8 z" fill="${c}"/>`;
const pauseGlyph = (x, y, c) => `<g fill="${c}"><rect x="${x-7}" y="${y-8}" width="5" height="16" rx="1.5"/><rect x="${x+2}" y="${y-8}" width="5" height="16" rx="1.5"/></g>`;

function topbar(title) {
  return `<g>${text(20, 64, "‹", { size: 30, fill: C.tPrim })}${text(52, 60, title, { size: 20, w: 600 })}</g>`;
}
const frame = (body) =>
  `<svg xmlns="http://www.w3.org/2000/svg" width="${W}" height="${H}" viewBox="0 0 ${W} ${H}">
   <rect width="${W}" height="${H}" rx="0" fill="${C.canvas}"/>${body}</svg>`;

/* ===== MOCK A — HOME + Recent library (new) ===== */
function home() {
  const P = 24;
  let b = "";
  b += wave(P, 70, W - 2 * P, 40, HS, C.accent + "D9");
  b += text(P, 168, "WaveCode", { size: 36, w: 800, fill: C.tPrim, ls: -1 });
  b += text(P, 200, "Sesini koda dönüştür,", { size: 15, fill: C.tSec });
  b += text(P, 220, "paylaş, dinlet.", { size: 15, fill: C.tSec });

  // NEW: recent WaveCodes
  b += text(P, 272, "SON WAVECODE'LARIN", { size: 11, w: 600, fill: C.tMut, ls: 1.5 });
  b += text(W - P, 272, "Tümü ›", { size: 12, fill: C.accent, anchor: "end" });
  const cw = (W - 2 * P - 14) / 2;
  const cards = [["A7K29XQ4", "Doğum günü"], ["M3P8KZ2Q", "Anneme"]];
  cards.forEach(([code, title], i) => {
    const x = P + i * (cw + 14), y = 288;
    b += rect(x, y, cw, 112, 18, C.s1);
    b += wave(x + 12, y + 16, cw - 24, 30, HS.slice(0, 14), C.accent + "CC", 3, 3);
    b += text(x + 14, y + 74, code, { size: 14, mono: true, w: 600 });
    b += text(x + 14, y + 96, title, { size: 12, fill: C.tSec });
  });

  // action cards
  const ay = 560, ah = 84;
  b += rect(P, ay, W - 2 * P, ah, 26, C.accent);
  b += rect(P + 18, ay + (ah - 52) / 2, 52, 52, 16, C.onAccent + "17");
  b += micGlyph(P + 44, ay + ah / 2, C.onAccent);
  b += text(P + 86, ay + 38, "Dövme Oluştur", { size: 18, w: 700, fill: C.onAccent });
  b += text(P + 86, ay + 60, "Sesini kaydet", { size: 13, fill: C.onAccent + "C0" });
  b += text(W - P - 18, ay + ah / 2 + 6, "→", { size: 22, fill: C.onAccent + "C0", anchor: "end" });

  const by = ay + ah + 14;
  b += rect(P, by, W - 2 * P, ah, 26, C.s2);
  b += rect(P + 18, by + (ah - 52) / 2, 52, 52, 16, "#FFFFFF10");
  b += scanGlyph(P + 44, by + ah / 2, C.accent);
  b += text(P + 86, by + 38, "Tara & Dinle", { size: 18, w: 700, fill: C.tPrim });
  b += text(P + 86, by + 60, "Kodu okut, dinle", { size: 13, fill: C.tSec });
  b += text(W - P - 18, by + ah / 2 + 6, "→", { size: 22, fill: C.tSec, anchor: "end" });
  return frame(b);
}

/* ===== MOCK B — CREATE, record-first with live timer + amplitude ===== */
function record() {
  const P = 24;
  let b = topbar("Dövme Oluştur");
  // step hint
  b += text(W / 2, 150, "DİNLİYORUM…", { size: 12, w: 600, fill: C.rec, ls: 2, anchor: "middle" });
  // live timer
  b += text(W / 2, 215, "0:07", { size: 56, w: 700, mono: true, fill: C.tPrim, anchor: "middle" });
  // live amplitude waveform (red while recording)
  const live = [0.3,0.6,0.9,0.5,0.8,1,0.7,0.4,0.6,0.85,0.5,0.7,0.95,0.6,0.35,0.55,0.8,0.5];
  b += wave(P, 250, W - 2 * P, 70, live, C.rec + "E6", 6, 6);
  // record button with pulse ring
  const cy = 470;
  b += `<circle cx="${W/2}" cy="${cy}" r="78" fill="${C.rec}" fill-opacity="0.16"/>`;
  b += `<circle cx="${W/2}" cy="${cy}" r="56" fill="${C.rec}"/>`;
  b += rect(W/2 - 15, cy - 15, 30, 30, 8, "#FFFFFF");
  b += text(W / 2, cy + 110, "Kaydı Durdur", { size: 17, w: 600, anchor: "middle" });
  b += text(W / 2, cy + 138, "En fazla 30 sn · istediğinde durdur", { size: 13, fill: C.tMut, anchor: "middle" });
  // cancel
  b += rect(W/2 - 60, cy + 168, 120, 44, 22, "none", C.outline, 1.5);
  b += text(W / 2, cy + 196, "İptal", { size: 14, w: 600, fill: C.tSec, anchor: "middle" });
  return frame(b);
}

/* ===== MOCK C — NOW PLAYING with scrubber + manual entry ===== */
function nowPlaying() {
  const P = 24;
  let b = topbar("Tara & Dinle");
  // Ses Kodu card
  let y = 100;
  b += rect(P, y, W - 2 * P, 96, 18, C.s1);
  b += text(P + 16, y + 28, "SES KODU", { size: 11, w: 600, fill: C.tSec, ls: 1.5 });
  b += text(W / 2, y + 70, "A7K29XQ4", { size: 30, mono: true, w: 600, anchor: "middle" });

  // Now playing card
  y = 220;
  const ch = 250;
  b += rect(P, y, W - 2 * P, ch, 24, C.successCard);
  b += text(P + 18, y + 32, "ŞİMDİ ÇALIYOR", { size: 11, w: 600, fill: C.accent, ls: 1.5 });
  b += text(P + 18, y + 62, "Doğum günü mesajı", { size: 18, w: 600 });
  // equalizer
  const ex = W - P - 60;
  [0.5,0.9,0.4,0.75,0.6].forEach((h, i) => {
    b += rect(ex + i * 11, y + 40 - 0, 5, 0, 0, "none"); // placeholder
    b += `<rect x="${ex + i * 11}" y="${y + 56 - 30 * h}" width="5" height="${30 * h}" rx="2.5" fill="${C.accent}"/>`;
  });
  // scrubber
  const sx = P + 18, sw = W - 2 * P - 36, sy = y + 110;
  const prog = 0.36;
  b += rect(sx, sy, sw, 6, 3, C.s3);
  b += rect(sx, sy, sw * prog, 6, 3, C.accent);
  b += `<circle cx="${sx + sw * prog}" cy="${sy + 3}" r="8" fill="${C.accent}"/>`;
  b += text(sx, sy + 28, "0:12", { size: 12, mono: true, fill: C.tSec });
  b += text(sx + sw, sy + 28, "0:34", { size: 12, mono: true, fill: C.tSec, anchor: "end" });
  // controls: replay + play/pause
  const py = y + 162, ph = 56;
  b += `<g><circle cx="${P + 18 + 24}" cy="${py + ph/2}" r="24" fill="none" stroke="${C.outline}" stroke-width="1.5"/><path d="M${P+30} ${py+ph/2-6} a8 8 0 1 1 -2 9" fill="none" stroke="${C.tPrim}" stroke-width="2" stroke-linecap="round"/><path d="M${P+28} ${py+ph/2-9} l2 5 l5 -2" fill="none" stroke="${C.tPrim}" stroke-width="2" stroke-linecap="round"/></g>`;
  const bx = P + 18 + 60;
  b += rect(bx, py, W - P - 18 - bx, ph, ph / 2, C.accent);
  b += pauseGlyph((bx + W - P - 18) / 2 - 36, py + ph / 2, C.onAccent);
  b += text((bx + W - P - 18) / 2 + 6, py + ph / 2 + 5, "Duraklat", { size: 15, w: 600, fill: C.onAccent, anchor: "middle" });

  // manual entry fallback
  b += text(W / 2, y + ch + 44, "Kod okunmadı mı?  Kodu elle gir", { size: 13, fill: C.tSec, anchor: "middle" });
  b += `<line x1="${W/2 + 18}" y1="${y + ch + 48}" x2="${W/2 + 112}" y2="${y + ch + 48}" stroke="${C.accent}" stroke-width="1"/>`;
  return frame(b);
}

const out = [["A-home", home()], ["B-record", record()], ["C-now-playing", nowPlaying()]];
for (const [n, svg] of out) fs.writeFileSync(path.join(OUT, `mock-${n}.svg`), svg);
fs.writeFileSync(path.join(OUT, "index.html"),
  `<!doctype html><meta charset=utf-8><title>WaveCode UX mocks</title><style>body{margin:0;background:#0c0c0e;display:flex;gap:28px;flex-wrap:wrap;padding:28px;font-family:Inter,Arial}img{width:300px;border-radius:22px;box-shadow:0 12px 50px #000a}figure{margin:0;color:#9a9aa2;text-align:center;font-size:13px}</style>` +
  out.map(([n]) => `<figure><img src="mock-${n}.svg"><figcaption>${n}</figcaption></figure>`).join(""));
console.log("Wrote", out.map((o) => "mock-" + o[0] + ".svg").join(", "));
