import { mkdirSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const outSvg = join(__dirname, 'gouxiong-sleep-reference-redesign.svg');
const outHtml = join(__dirname, 'gouxiong-sleep-reference-redesign.html');

const C = {
  bg: '#FFF8EA',
  cream: '#FFF1D8',
  blue: '#2F6FEA',
  blueDeep: '#16325C',
  green: '#4CAF67',
  orange: '#FF9D22',
  red: '#F24A3D',
  text: '#1C2D49',
  muted: '#667085',
  white: '#FFFFFF',
  line: '#E8DECB',
  softGreen: '#ECF8EF',
  softOrange: '#FFF0DA',
  softBlue: '#EAF2FF',
  softRed: '#FFE6E2',
  night1: '#071A2F',
  night2: '#123866',
  bear: '#C98B4E',
  bearLight: '#F1C485',
  bearDark: '#664125',
};

const W = 1852;
const H = 1024;
const els = [];
let clip = 0;

const esc = (s) => String(s).replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;');
const push = (s) => els.push(s);
const rect = (x, y, w, h, fill, rx = 0, stroke = 'none', sw = 1, extra = '') =>
  push(`<rect x="${x}" y="${y}" width="${w}" height="${h}" rx="${rx}" fill="${fill}" stroke="${stroke}" stroke-width="${sw}" ${extra}/>`);
const circle = (cx, cy, r, fill, stroke = 'none', sw = 1) =>
  push(`<circle cx="${cx}" cy="${cy}" r="${r}" fill="${fill}" stroke="${stroke}" stroke-width="${sw}"/>`);
const ellipse = (cx, cy, rx, ry, fill, stroke = 'none', sw = 1) =>
  push(`<ellipse cx="${cx}" cy="${cy}" rx="${rx}" ry="${ry}" fill="${fill}" stroke="${stroke}" stroke-width="${sw}"/>`);
const text = (x, y, value, size, fill = C.text, weight = 600, anchor = 'start') => {
  const family = '"Noto Sans SC","Microsoft YaHei",Arial,sans-serif';
  push(`<text x="${x}" y="${y}" fill="${fill}" font-size="${size}" font-family='${family}' font-weight="${weight}" text-anchor="${anchor}" letter-spacing="0">${esc(value)}</text>`);
};
const lineText = (x, y, lines, size, fill = C.text, weight = 600, anchor = 'start', gap = 1.3) => {
  const family = '"Noto Sans SC","Microsoft YaHei",Arial,sans-serif';
  push(`<text x="${x}" y="${y}" fill="${fill}" font-size="${size}" font-family='${family}' font-weight="${weight}" text-anchor="${anchor}" letter-spacing="0">`);
  lines.forEach((v, i) => push(`<tspan x="${x}" dy="${i === 0 ? 0 : size * gap}">${esc(v)}</tspan>`));
  push('</text>');
};

function defs() {
  push(`<defs>
    <filter id="softShadow" x="-25%" y="-25%" width="150%" height="160%">
      <feDropShadow dx="0" dy="12" stdDeviation="12" flood-color="#16325C" flood-opacity="0.16"/>
    </filter>
    <filter id="buttonShadow" x="-20%" y="-20%" width="140%" height="150%">
      <feDropShadow dx="0" dy="8" stdDeviation="8" flood-color="#16325C" flood-opacity="0.20"/>
    </filter>
    <linearGradient id="blueBtn" x1="0" y1="0" x2="0" y2="1">
      <stop offset="0" stop-color="#5D8CFF"/><stop offset="0.62" stop-color="#2F6FEA"/><stop offset="1" stop-color="#1F55C7"/>
    </linearGradient>
    <linearGradient id="redBtn" x1="0" y1="0" x2="0" y2="1">
      <stop offset="0" stop-color="#FF7469"/><stop offset="0.62" stop-color="#F24A3D"/><stop offset="1" stop-color="#C93228"/>
    </linearGradient>
    <linearGradient id="orangeBtn" x1="0" y1="0" x2="0" y2="1">
      <stop offset="0" stop-color="#FFB743"/><stop offset="1" stop-color="#FF8C13"/>
    </linearGradient>
    <linearGradient id="nightBg" x1="0" y1="0" x2="0" y2="1">
      <stop offset="0" stop-color="#071A2F"/><stop offset="0.55" stop-color="#123866"/><stop offset="1" stop-color="#1B4A80"/>
    </linearGradient>
    <linearGradient id="morningBg" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0" stop-color="#FFF9E8"/><stop offset="1" stop-color="#FFE4B6"/>
    </linearGradient>
    <linearGradient id="alarmBg" x1="0" y1="0" x2="0" y2="1">
      <stop offset="0" stop-color="#FFF7E8"/><stop offset="1" stop-color="#FFD78F"/>
    </linearGradient>
  </defs>`);
}

function bear(cx, cy, s = 1, awake = false) {
  circle(cx - 44 * s, cy - 55 * s, 28 * s, C.bear);
  circle(cx + 44 * s, cy - 55 * s, 28 * s, C.bear);
  circle(cx - 44 * s, cy - 55 * s, 15 * s, C.bearLight);
  circle(cx + 44 * s, cy - 55 * s, 15 * s, C.bearLight);
  ellipse(cx, cy, 75 * s, 70 * s, C.bear);
  ellipse(cx, cy + 20 * s, 38 * s, 28 * s, C.bearLight);
  circle(cx - 28 * s, cy - 12 * s, awake ? 7 * s : 0.1, C.text);
  circle(cx + 28 * s, cy - 12 * s, awake ? 7 * s : 0.1, C.text);
  if (!awake) {
    rect(cx - 38 * s, cy - 15 * s, 22 * s, 5 * s, C.bearDark, 5);
    rect(cx + 16 * s, cy - 15 * s, 22 * s, 5 * s, C.bearDark, 5);
  }
  ellipse(cx, cy + 8 * s, 14 * s, 10 * s, C.bearDark);
  push(`<path d="M ${cx - 16 * s} ${cy + 28 * s} Q ${cx} ${cy + 42 * s} ${cx + 16 * s} ${cy + 28 * s}" fill="none" stroke="${C.bearDark}" stroke-width="${4 * s}" stroke-linecap="round"/>`);
}

function moon(x, y, r) {
  circle(x, y, r, '#FFE08A');
  circle(x + r * 0.34, y - r * 0.15, r * 0.95, C.blueDeep);
}

function shield(x, y, s = 1) {
  push(`<path d="M ${x} ${y} L ${x + 38 * s} ${y + 14 * s} L ${x + 33 * s} ${y + 58 * s} Q ${x + 19 * s} ${y + 76 * s} ${x} ${y + 84 * s} Q ${x - 19 * s} ${y + 76 * s} ${x - 33 * s} ${y + 58 * s} L ${x - 38 * s} ${y + 14 * s} Z" fill="${C.green}" stroke="#2D8D45" stroke-width="${2 * s}"/>`);
  push(`<path d="M ${x - 16 * s} ${y + 38 * s} L ${x - 3 * s} ${y + 52 * s} L ${x + 22 * s} ${y + 24 * s}" fill="none" stroke="${C.white}" stroke-width="${8 * s}" stroke-linecap="round" stroke-linejoin="round"/>`);
}

function iconTile(x, y, label, kind, color, bg = C.white) {
  rect(x, y, 156, 126, bg, 18, '#EFE3D1', 1, 'filter="url(#softShadow)"');
  if (kind === 'shield') shield(x + 78, y + 26, 0.55);
  if (kind === 'sun') {
    circle(x + 78, y + 48, 25, C.orange);
    for (let i = 0; i < 8; i++) {
      const a = i * Math.PI / 4;
      rect(x + 75 + Math.cos(a) * 38, y + 45 + Math.sin(a) * 38, 6, 18, C.orange, 3, 'none', 1, `transform="rotate(${i * 45} ${x + 78} ${y + 48})"`);
    }
  }
  if (kind === 'heart') text(x + 78, y + 64, '❤', 44, color, 700, 'middle');
  if (kind === 'battery') {
    rect(x + 48, y + 34, 55, 28, '#90D89B', 6, '#4B9C5C', 3);
    rect(x + 104, y + 42, 8, 12, '#4B9C5C', 3);
    rect(x + 53, y + 39, 38, 18, C.green, 4);
  }
  text(x + 78, y + 102, label, 21, color, 700, 'middle');
}

function pillButton(x, y, w, h, label, fill, fg = C.white, icon = '') {
  rect(x, y, w, h, fill, h / 2, 'none', 1, 'filter="url(#buttonShadow)"');
  if (icon) {
    circle(x + 38, y + h / 2, h * 0.31, C.white);
    text(x + 38, y + h / 2 + 9, icon, 25, fill === 'url(#redBtn)' ? C.red : C.blue, 700, 'middle');
  }
  text(x + w / 2 + (icon ? 22 : 0), y + h / 2 + 11, label, 30, fg, 800, 'middle');
}

function phone(x, y, h, draw) {
  const id = `clip-${clip++}`;
  rect(x - 5, y - 5, 360, h + 10, '#111111', 48, '#111111', 1);
  rect(x, y, 350, h, C.white, 42, '#202020', 5);
  push(`<defs><clipPath id="${id}"><rect x="${x + 7}" y="${y + 7}" width="336" height="${h - 14}" rx="34"/></clipPath></defs>`);
  push(`<g clip-path="url(#${id})">`);
  draw(x + 7, y + 7, 336, h - 14);
  push('</g>');
}

function statusBar(x, y, dark = false) {
  const c = dark ? C.white : '#222';
  text(x + 16, y + 28, '12:30', 13, c, 600);
  push(`<path d="M ${x + 276} ${y + 18} l 16 0 l -8 12 z" fill="${c}"/>`);
  push(`<path d="M ${x + 305} ${y + 17} l 16 21 l -25 0 z" fill="${c}"/>`);
  rect(x + 326, y + 18, 10, 20, c, 3);
}

function homeScreen(x, y, w, h) {
  rect(x, y, w, h, 'url(#nightBg)');
  statusBar(x, y, true);
  text(x + w / 2, y + 94, '狗熊睡眠', 32, C.white, 800, 'middle');
  moon(x + 276, y + 142, 28);
  for (const [sx, sy] of [[100, 132], [155, 180], [220, 170], [246, 230], [128, 260]]) text(x + sx, y + sy, '✦', 20, '#FFE08A', 700, 'middle');
  rect(x + 22, y + 135, 70, 220, '#1A4380', 20);
  rect(x + 250, y + 125, 72, 232, '#1A4380', 20);
  rect(x + 44, y + 344, 250, 70, '#F9F0E2', 32);
  bear(x + 168, y + 312, 0.92, false);
  rect(x + 42, y + 348, 260, 98, '#315D9C', 28);
  for (const [sx, sy] of [[82, 378], [135, 405], [186, 384], [235, 412], [272, 378]]) text(x + sx, y + sy, '★', 14, '#D5A75E', 700, 'middle');
  rect(x + 16, y + 444, 304, 92, C.white, 28);
  shield(x + 92, y + 462, 0.5);
  text(x + 172, y + 500, '守护已就绪', 23, C.green, 800, 'middle');
  pillButton(x + 40, y + 552, 256, 75, '开始守护', 'url(#blueBtn)', C.white, '▶');
  bottomNav(x, y + h - 88, '守护');
}

function morningScreen(x, y, w, h) {
  rect(x, y, w, h, 'url(#morningBg)');
  statusBar(x, y, false);
  text(x + 96, y + 95, '☀', 36, C.orange, 800, 'middle');
  text(x + 190, y + 96, '早安护理', 34, C.orange, 800, 'middle');
  rect(x + 38, y + 140, 260, 170, 'rgba(255,255,255,0.25)', 32);
  bear(x + 170, y + 260, 0.8, true);
  rect(x + 68, y + 242, 52, 76, C.white, 18, '#E9DEC9');
  rect(x + 34, y + 350, 270, 110, C.softGreen, 22, '#CDEFD5', 2);
  text(x + 82, y + 420, '🥛', 34, C.blue, 700, 'middle');
  text(x + 170, y + 405, '喝水', 25, '#286B36', 800, 'middle');
  text(x + 170, y + 438, '我喝水了', 20, '#286B36', 700, 'middle');
  circle(x + 270, y + 405, 31, C.green);
  text(x + 270, y + 416, '✓', 36, C.white, 800, 'middle');
  rect(x + 34, y + 478, 270, 110, C.softOrange, 22, '#FFD39E', 2);
  text(x + 82, y + 548, '💊', 36, C.orange, 700, 'middle');
  text(x + 178, y + 532, '已吃药', 25, '#A45B08', 800, 'middle');
  text(x + 178, y + 564, '稍后可再提醒', 18, C.orange, 700, 'middle');
  circle(x + 270, y + 532, 31, C.orange);
  text(x + 270, y + 543, '✓', 36, C.white, 800, 'middle');
  bottomNav(x, y + h - 88, '早安');
}

function alarmScreen(x, y, w, h) {
  rect(x, y, w, h, 'url(#alarmBg)');
  statusBar(x, y, false);
  text(x + w / 2, y + 94, '起床啦！', 40, C.red, 900, 'middle');
  circle(x + w / 2, y + 280, 110, '#FFE5BF');
  alarmClock(x + 92, y + 174, 1.05);
  pillButton(x + 36, y + 470, 264, 86, '我醒了', 'url(#redBtn)', C.white, '❤');
  rect(x + 42, y + 590, 252, 58, '#FFF6EA', 28, '#E9CDB0', 2, 'filter="url(#buttonShadow)"');
  text(x + 168, y + 628, '🛡  我没事', 26, C.red, 800, 'middle');
}

function alarmClock(x, y, s = 1) {
  circle(x + 72 * s, y + 86 * s, 70 * s, C.red);
  circle(x + 72 * s, y + 86 * s, 50 * s, '#FFF9F0');
  rect(x + 69 * s, y + 51 * s, 6 * s, 44 * s, C.text, 3);
  rect(x + 72 * s, y + 86 * s, 36 * s, 6 * s, C.text, 3);
  rect(x + 33 * s, y + 151 * s, 28 * s, 12 * s, C.red, 6);
  rect(x + 83 * s, y + 151 * s, 28 * s, 12 * s, C.red, 6);
  ellipse(x + 26 * s, y + 20 * s, 35 * s, 20 * s, C.red);
  ellipse(x + 118 * s, y + 20 * s, 35 * s, 20 * s, C.red);
  rect(x + 45 * s, y + 2 * s, 54 * s, 14 * s, C.red, 7);
}

function bottomNav(x, y, active) {
  rect(x, y, 336, 88, 'rgba(255,255,255,0.92)', 28, '#EFE0C9', 1);
  const items = [['守护', '🛡'], ['早安', '☀'], ['我的', '♙']];
  items.forEach(([label, icon], i) => {
    const cx = x + 56 + i * 112;
    const on = label === active;
    const color = on ? (label === '早安' ? C.orange : C.blue) : '#6B7280';
    text(cx, y + 36, icon, 22, color, 800, 'middle');
    text(cx, y + 66, label, 15, color, 700, 'middle');
  });
}

function roleCard(x, y, name, color, gender = 'girl') {
  rect(x, y, 285, 150, color, 24);
  rect(x + 16, y + 20, 42, 110, 'rgba(255,255,255,0.45)', 18);
  lineText(x + 38, y + 54, name.split(''), 22, C.white, 800, 'middle', 1.08);
  const cx = x + 178;
  ellipse(cx, y + 80, 58, 50, gender === 'girl' ? '#5F3D2E' : '#2B2A2A');
  ellipse(cx, y + 82, 43, 45, '#F3C6A7');
  circle(cx - 16, y + 74, 4, C.text);
  circle(cx + 16, y + 74, 4, C.text);
  push(`<path d="M ${cx - 16} ${y + 95} Q ${cx} ${y + 107} ${cx + 16} ${y + 95}" fill="none" stroke="${C.text}" stroke-width="3" stroke-linecap="round"/>`);
  rect(cx - 43, y + 119, 86, 40, gender === 'girl' ? '#F8C9D2' : '#74B78A', 18);
  text(x + 246, y + 84, gender === 'girl' ? '👋' : '👍', 36, C.text, 700, 'middle');
}

function componentStrip() {
  rect(48, 916, 1492, 72, '#FFF2DD', 26);
  pillButton(82, 932, 240, 48, '开始守护', 'url(#blueBtn)', C.white, '▶');
  rect(344, 932, 240, 48, C.softGreen, 24, '#C8EAD0', 2, 'filter="url(#buttonShadow)"');
  text(464, 964, '✓ 我没事', 24, '#2B7B3E', 800, 'middle');
  pillButton(616, 932, 220, 48, '早安护理', 'url(#orangeBtn)', C.white, '☀');
  pillButton(862, 932, 210, 48, '我醒了', 'url(#redBtn)', C.white, '❤');
  ['🥛', '💊', '🛡', '❤', '☀'].forEach((v, i) => {
    rect(1118 + i * 66, 928, 52, 52, C.white, 15, '#EEE0CA', 1, 'filter="url(#softShadow)"');
    text(1144 + i * 66, 963, v, 25, C.title, 700, 'middle');
  });
}

function brandPanel() {
  circle(178, 130, 106, '#FFF1D8', '#F3DDB6', 3, 'filter="url(#softShadow)"');
  circle(178, 130, 86, C.blueDeep);
  moon(210, 130, 54);
  bear(152, 132, 0.63, false);
  text(178, 306, '狗熊睡眠', 52, C.blueDeep, 900, 'middle');
  text(178, 355, '离线守护 · 晨间关怀', 28, '#4F4A45', 500, 'middle');
  iconTile(48, 420, '安心守护', 'shield', '#224D8E');
  iconTile(224, 420, '晨间关怀', 'sun', '#9F5F08');
  iconTile(48, 565, '家人陪伴', 'heart', '#9F2E27');
  iconTile(224, 565, '离线可用', 'battery', '#286B36');
  rect(48, 726, 332, 86, C.softOrange, 24);
  text(116, 772, '👐❤', 34, C.red, 700, 'middle');
  lineText(178, 760, ['我在，', '你安心入睡'], 27, '#9B6530', 800, 'start', 1.0);
}

defs();
rect(0, 0, W, H, C.bg);
brandPanel();
phone(430, 82, 780, homeScreen);
phone(804, 82, 780, morningScreen);
phone(1178, 82, 780, alarmScreen);
roleCard(1562, 56, '贴心小妹', '#F9DAD7', 'girl');
roleCard(1562, 238, '懂事小弟', '#DFF0D8', 'boy');
roleCard(1562, 420, '阳光小哥', '#D7EAFB', 'boy');
roleCard(1562, 602, '温柔姐姐', '#E7DCF8', 'girl');
componentStrip();

const svg = `<?xml version="1.0" encoding="UTF-8"?>\n<svg xmlns="http://www.w3.org/2000/svg" width="${W}" height="${H}" viewBox="0 0 ${W} ${H}">\n${els.join('\n')}\n</svg>\n`;
mkdirSync(__dirname, { recursive: true });
writeFileSync(outSvg, svg, 'utf8');
writeFileSync(outHtml, `<!doctype html>
<html lang="zh-CN">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>狗熊睡眠参考图重做版</title>
  <style>
    body { margin: 0; background: #f4efe6; font-family: "Noto Sans SC", "Microsoft YaHei", Arial, sans-serif; }
    header { padding: 18px 26px; color: #16325C; background: #fff8ea; border-bottom: 1px solid #eadfca; }
    h1 { margin: 0; font-size: 22px; }
    p { margin: 6px 0 0; color: #667085; }
    main { padding: 18px; overflow: auto; }
    img { display: block; width: 1852px; max-width: none; box-shadow: 0 16px 48px rgba(22,50,92,.18); }
  </style>
</head>
<body>
  <header>
    <h1>狗熊睡眠 UI/UX 参考图重做版</h1>
    <p>按用户参考图重排：品牌区、三台手机、四角色、按钮组件条。素材后续用 gxs_* 独立绘图模型资产替换。</p>
  </header>
  <main><img src="./gouxiong-sleep-reference-redesign.svg" alt="狗熊睡眠 UI/UX 参考图重做版"></main>
</body>
</html>
`, 'utf8');

console.log(JSON.stringify({ svg: outSvg, html: outHtml }, null, 2));
