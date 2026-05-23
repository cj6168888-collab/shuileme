import { mkdirSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const outSvg = join(__dirname, 'gouxiong-sleep-ui-handoff.svg');
const outHtml = join(__dirname, 'gouxiong-sleep-ui-handoff.html');
const outNotes = join(__dirname, 'figma-handoff-notes.md');

const C = {
  warm: '#FFF8EA',
  blue: '#2F6FEA',
  green: '#4CAF67',
  orange: '#FF9D22',
  red: '#F24A3D',
  title: '#16325C',
  body: '#4E5969',
  muted: '#667085',
  line: '#E8E1D4',
  white: '#FFFFFF',
  softBlue: '#EAF2FF',
  softGreen: '#ECF8EF',
  softOrange: '#FFF2DD',
  softRed: '#FFE7E3',
  night: '#112A48',
  deepNight: '#071A2F',
  bear: '#C89561',
  bearDark: '#8F5C34',
  cream: '#FFE8C2',
};

const W = 1990;
const H = 2380;
const els = [];
let clipId = 0;

const esc = (s) => String(s)
  .replaceAll('&', '&amp;')
  .replaceAll('<', '&lt;')
  .replaceAll('>', '&gt;')
  .replaceAll('"', '&quot;');

function push(s) {
  els.push(s);
}

function rect(x, y, w, h, fill, rx = 0, stroke = 'none', sw = 1, extra = '') {
  push(`<rect x="${x}" y="${y}" width="${w}" height="${h}" rx="${rx}" fill="${fill}" stroke="${stroke}" stroke-width="${sw}" ${extra}/>`);
}

function circle(cx, cy, r, fill, stroke = 'none', sw = 1) {
  push(`<circle cx="${cx}" cy="${cy}" r="${r}" fill="${fill}" stroke="${stroke}" stroke-width="${sw}"/>`);
}

function ellipse(cx, cy, rx, ry, fill, stroke = 'none', sw = 1) {
  push(`<ellipse cx="${cx}" cy="${cy}" rx="${rx}" ry="${ry}" fill="${fill}" stroke="${stroke}" stroke-width="${sw}"/>`);
}

function text(x, y, lines, size = 18, fill = C.body, weight = 400, anchor = 'start', width = 0, line = 1.35) {
  const arr = Array.isArray(lines) ? lines : [lines];
  const family = '"Noto Sans SC","Microsoft YaHei",Arial,sans-serif';
  push(`<text x="${x}" y="${y}" fill="${fill}" font-size="${size}" font-family='${family}' font-weight="${weight}" text-anchor="${anchor}" letter-spacing="0">`);
  arr.forEach((part, i) => {
    const dx = i === 0 ? 0 : -width;
    const dy = i === 0 ? 0 : size * line;
    push(`<tspan x="${x}" dy="${i === 0 ? 0 : dy}">${esc(part)}</tspan>`);
  });
  push('</text>');
}

function button(x, y, w, h, label, fill, fg = C.white) {
  rect(x, y, w, h, fill, Math.min(30, h / 2));
  text(x + w / 2, y + h / 2 + 7, label, 22, fg, 700, 'middle');
}

function chip(x, y, w, label, fg, bg) {
  rect(x, y, w, 38, bg, 19, fg);
  text(x + w / 2, y + 25, label, 15, fg, 600, 'middle');
}

function card(x, y, w, h, title, body, accent = C.blue, fill = C.white) {
  rect(x, y, w, h, fill, 22, C.line, 1);
  rect(x + 18, y + 20, 6, h - 40, accent, 3);
  text(x + 36, y + 43, title, 20, C.title, 700);
  text(x + 36, y + 76, body, 16, C.body, 400);
}

function status(x, y, dark = false) {
  const c = dark ? C.white : C.title;
  text(x + 28, y + 36, '9:41', 14, c, 600);
  rect(x + 300, y + 25, 20, 8, c, 2);
  rect(x + 326, y + 22, 18, 12, c, 3);
  rect(x + 354, y + 22, 24, 12, 'none', 3, c);
  rect(x + 357, y + 25, 14, 6, c, 2);
}

function nav(x, y, active) {
  rect(x, y + 770, 393, 82, C.white, 0, '#EFE6D8');
  const items = [['守护', '盾'], ['早安', '日'], ['我的', '我']];
  items.forEach(([label, glyph], i) => {
    const gx = x + 34 + i * 124;
    const on = label === active;
    const color = on ? (active === '早安' ? C.orange : C.blue) : C.muted;
    circle(gx + 49, y + 803, 17, on ? color : '#F2F4F7');
    text(gx + 49, y + 808, glyph, 14, on ? C.white : C.muted, 700, 'middle');
    text(gx + 49, y + 839, label, 15, color, 600, 'middle');
  });
}

function bear(x, y, s = 1, wake = false) {
  ellipse(x + 93 * s, y + 107 * s, 61 * s, 43 * s, C.bear);
  ellipse(x + 59 * s, y + 61 * s, 43 * s, 39 * s, C.bear);
  circle(x + 26 * s, y + 34 * s, 14 * s, C.bearDark);
  circle(x + 90 * s, y + 34 * s, 14 * s, C.bearDark);
  ellipse(x + 59 * s, y + 65 * s, 11 * s, 7 * s, '#5A3A26');
  if (wake) {
    circle(x + 46 * s, y + 51 * s, 4 * s, C.title);
    circle(x + 78 * s, y + 51 * s, 4 * s, C.title);
  } else {
    rect(x + 39 * s, y + 48 * s, 10 * s, 4 * s, C.title, 2);
    rect(x + 71 * s, y + 48 * s, 10 * s, 4 * s, C.title, 2);
  }
}

function sleepScene(x, y, w, h, night = false) {
  rect(x, y, w, h, night ? C.night : C.softBlue, 28);
  circle(x + w - 56, y + 58, 24, night ? '#FFE7A8' : '#FFD56B');
  rect(x + 36, y + h - 72, w - 72, 44, night ? '#365B87' : '#B9D8FF', 20);
  bear(x + 90, y + 60, 1.04);
  rect(x + 116, y + h - 88, 170, 46, night ? '#426D9F' : '#8FC8FF', 18);
}

function morningScene(x, y, w, h) {
  rect(x, y, w, h, C.softOrange, 28);
  circle(x + w - 58, y + 62, 29, '#FFD66E');
  rect(x + 38, y + 36, 80, 94, '#BCE8FF', 18);
  bear(x + 140, y + 58, 0.95);
  rect(x + 236, y + 92, 54, 18, C.bear, 9);
}

function alarmIcon(x, y) {
  circle(x + 98, y + 98, 56, C.red);
  circle(x + 98, y + 98, 40, C.white);
  rect(x + 96, y + 94, 34, 6, C.red, 3);
  rect(x + 96, y + 72, 6, 30, C.red, 3);
  ellipse(x + 47, y + 39, 27, 19, C.orange);
  ellipse(x + 149, y + 39, 27, 19, C.orange);
  rect(x + 60, y + 150, 28, 12, C.red, 6);
  rect(x + 112, y + 150, 28, 12, C.red, 6);
}

function assistant(x, y) {
  rect(x + 18, y, 210, 242, C.softGreen, 40);
  ellipse(x + 120, y + 84, 58, 44, '#26374A');
  ellipse(x + 120, y + 84, 42, 46, '#F2C9A7');
  rect(x + 48, y + 136, 150, 116, '#7CC9A2', 40);
  rect(x + 92, y + 134, 62, 28, C.warm, 14);
  circle(x + 104, y + 78, 4, C.title);
  circle(x + 138, y + 78, 4, C.title);
  rect(x + 110, y + 104, 36, 5, C.title, 3);
}

function phone(x, y, name, bg = C.warm, draw) {
  const id = `clip${clipId++}`;
  push(`<defs><clipPath id="${id}"><rect x="${x}" y="${y}" width="393" height="852" rx="42"/></clipPath></defs>`);
  rect(x, y, 393, 852, bg, 42, '#D8CEC0');
  push(`<g clip-path="url(#${id})">`);
  draw(x, y);
  push('</g>');
  text(x, y - 18, name, 20, C.title, 700);
}

function top(x, y, title, sub) {
  text(x + 24, y + 88, title, 30, C.title, 700);
  if (sub) text(x + 24, y + 124, sub, 17, C.muted);
  circle(x + 348, y + 80, 22, C.softGreen);
  text(x + 348, y + 87, '熊', 17, C.green, 700, 'middle');
}

mkdirSync(__dirname, { recursive: true });

rect(0, 0, W, H, C.warm);
rect(0, 0, W, 268, C.softBlue);
text(64, 80, '睡了么 APK UI 设计交付稿', 44, C.title, 700);
text(66, 138, '离线本地睡眠守护，不做诊断；重点是异常发生时分级唤醒、确认安全和家人通知。', 22, C.body);
chip(66, 178, 112, '本机处理', C.green, C.softGreen);
chip(196, 178, 132, '大字大按钮', C.blue, C.softBlue);
chip(346, 178, 142, '非诊断建议', C.orange, C.softOrange);
chip(506, 178, 142, '强唤醒确认', C.red, C.softRed);

text(64, 326, '品牌色', 28, C.title, 700);
[
  ['暖白背景', C.warm], ['主按钮蓝', C.blue], ['安全绿', C.green], ['晨光橙', C.orange],
  ['强唤醒红', C.red], ['深蓝标题', C.title], ['正文灰', C.body], ['夜间蓝', C.night],
].forEach(([name, color], i) => {
  const x = 64 + (i % 8) * 170;
  rect(x, 368, 118, 76, color, 18, C.white);
  text(x, 472, name, 16, C.title, 600);
  text(x, 497, color, 13, C.muted);
});

text(64, 568, '核心界面', 28, C.title, 700);
text(210, 569, '第一屏只保留一个主行动；设置和报告下沉；图片不承载按钮和正式文案。', 18, C.body);

const xs = [64, 524, 984, 1444];
const y1 = 650;
const y2 = 1560;

phone(xs[0], y1, '01 欢迎 / 直接开始', C.warm, (x, y) => {
  status(x, y);
  sleepScene(x + 34, y + 128, 325, 226);
  text(x + 196, y + 424, '睡了么', 34, C.title, 700, 'middle');
  text(x + 196, y + 472, '本机识别异常声音和动作，必要时唤醒你。', 19, C.body, 400, 'middle');
  button(x + 36, y + 520, 321, 76, '直接开始', C.blue);
  button(x + 36, y + 612, 321, 66, '简单设置', C.white, C.blue);
  rect(x + 36, y + 612, 321, 66, 'none', 30, C.blue);
  chip(x + 54, y + 710, 86, '不注册', C.green, C.softGreen);
  chip(x + 154, y + 710, 86, '不收费', C.green, C.softGreen);
  chip(x + 254, y + 710, 92, '只在本机', C.orange, C.softOrange);
});

phone(xs[1], y1, '02 守护首页', C.warm, (x, y) => {
  status(x, y);
  top(x, y, '今晚准备好了吗？', '标准模式，打开就能用。');
  chip(x + 24, y + 132, 118, '守护已就绪', C.green, C.softGreen);
  sleepScene(x + 24, y + 190, 345, 210);
  button(x + 32, y + 428, 329, 78, '开始守护', C.blue);
  chip(x + 30, y + 530, 96, '麦克风', C.green, C.softGreen);
  chip(x + 146, y + 530, 108, '音量正常', C.green, C.softGreen);
  chip(x + 274, y + 530, 96, '联系人未设', C.orange, C.softOrange);
  card(x + 24, y + 596, 345, 112, '昨晚摘要', '记录 5 次，自动取消 3 次，强唤醒 0 次。', C.blue);
  card(x + 24, y + 724, 345, 94, '小助手', '睡前可以和我说一句，今天状态我会记住。', C.green);
  nav(x, y, '守护');
});

phone(xs[2], y1, '03 睡前自检', C.warm, (x, y) => {
  status(x, y);
  top(x, y, '睡前自检', '20 秒确认今晚能安全守护。');
  card(x + 24, y + 144, 345, 116, '今晚我会静静守着', '异常时才提醒；没有异常时保持安静。', C.blue, C.softBlue);
  card(x + 24, y + 284, 345, 94, '麦克风', '已允许，用于本机异常声音识别。', C.green);
  card(x + 24, y + 396, 345, 94, '音量 / 蓝牙', '卧室音箱已连接，手机音量合适。', C.green);
  card(x + 24, y + 508, 345, 112, '紧急联系人', '未设置。高风险无确认时只能本机强唤醒。', C.orange);
  button(x + 32, y + 664, 329, 76, '开始守护', C.blue);
  button(x + 32, y + 754, 329, 58, '先去处理', C.white, C.blue);
  rect(x + 32, y + 754, 329, 58, 'none', 28, C.blue);
});

phone(xs[3], y1, '04 守护中 / 夜间状态', C.deepNight, (x, y) => {
  rect(x, y, 393, 852, C.deepNight);
  status(x, y, true);
  text(x + 196, y + 112, '正在守护', 34, C.white, 700, 'middle');
  text(x + 196, y + 152, '安心睡吧，有风险时我再叫醒你。', 18, '#D9E6F5', 400, 'middle');
  sleepScene(x + 34, y + 184, 325, 230, true);
  text(x + 196, y + 516, '02:18', 56, C.white, 700, 'middle');
  text(x + 196, y + 548, '已运行', 18, '#B8C9DA', 400, 'middle');
  card(x + 34, y + 586, 325, 94, '当前模式', '标准模式 / 高风险强唤醒', C.blue, '#173B63');
  card(x + 34, y + 696, 325, 86, '输出设备', '手机扬声器，蓝牙音箱已准备', C.green, '#173B63');
  button(x + 42, y + 780, 309, 58, '停止守护', C.red);
});

phone(xs[0], y2, '05 强唤醒确认', '#2C1512', (x, y) => {
  rect(x, y, 393, 852, '#2C1512');
  circle(x + 196, y + 230, 245, '#682016');
  status(x, y, true);
  alarmIcon(x + 98, y + 132);
  text(x + 196, y + 388, '请醒一下', 44, C.white, 700, 'middle');
  text(x + 196, y + 434, '检测到高风险睡眠异常，请先确认安全。', 22, C.cream, 600, 'middle');
  rect(x + 42, y + 484, 309, 82, '#3F221D', 24, '#8A4A3B');
  text(x + 196, y + 534, '60 秒后通知紧急联系人', 22, C.cream, 700, 'middle');
  button(x + 32, y + 606, 329, 96, '我没事', C.green);
  button(x + 32, y + 724, 154, 58, '暂停 10 分钟', C.white, C.red);
  rect(x + 32, y + 724, 154, 58, 'none', 26, C.red);
  button(x + 207, y + 724, 154, 58, '结束本晚', C.white, C.red);
  rect(x + 207, y + 724, 154, 58, 'none', 26, C.red);
});

phone(xs[1], y2, '06 早安护理', C.warm, (x, y) => {
  status(x, y);
  top(x, y, '早安护理', '先处理能马上执行的小事。');
  morningScene(x + 24, y + 138, 345, 196);
  card(x + 24, y + 358, 345, 98, '喝水', '起床后先喝几口温水。', C.green, C.softGreen);
  button(x + 214, y + 386, 130, 48, '我喝水了', C.green);
  card(x + 24, y + 474, 345, 98, '吃药', '按医嘱或家人交代处理。', C.orange, C.softOrange);
  button(x + 226, y + 502, 118, 48, '已吃药', C.orange);
  card(x + 24, y + 590, 345, 112, '昨晚汇报', '昨晚记录 4 次波动，3 次自动恢复。', C.blue);
  button(x + 36, y + 724, 321, 56, '查看详情', C.blue);
  nav(x, y, '早安');
});

phone(xs[2], y2, '07 小助手 Live', C.warm, (x, y) => {
  status(x, y);
  text(x + 28, y + 94, '我在听您说', 30, C.title, 700);
  text(x + 28, y + 126, '只在聊天页看一眼，不在后台偷拍。', 16, C.muted);
  assistant(x + 74, y + 150);
  rect(x + 36, y + 438, 321, 112, C.white, 28, C.line);
  text(x + 196, y + 484, ['您慢慢说，我在。', '复盘昨晚、找东西、记录今天状态，都可以直接说。'], 20, C.title, 600, 'middle');
  rect(x + 86, y + 584, 220, 64, C.softBlue, 32);
  for (let i = 0; i < 7; i++) rect(x + 112 + i * 24, y + 608 - (i % 3) * 6, 9, 24 + (i % 3) * 12, C.blue, 5);
  button(x + 36, y + 684, 100, 58, '我来说', C.blue);
  button(x + 146, y + 684, 100, 58, '暂停', C.white, C.blue);
  rect(x + 146, y + 684, 100, 58, 'none', 26, C.blue);
  button(x + 256, y + 684, 100, 58, '看一眼', C.green);
  nav(x, y, '我的');
});

phone(xs[3], y2, '08 我的 / 设置', C.warm, (x, y) => {
  status(x, y);
  top(x, y, '我的', '低频设置分组收纳。');
  card(x + 24, y + 144, 345, 100, '安全守护', '睡前自检、检测测试、紧急联系人。', C.blue);
  card(x + 24, y + 264, 345, 100, '声音与家人', '亲人录音、本地歌曲、系统闹钟回退。', C.orange);
  card(x + 24, y + 384, 345, 100, '小助手', '角色、称呼、主人档案、今日状态。', C.green);
  card(x + 24, y + 504, 345, 116, '数据与导出', '本地记录、异常片段、导出给自己或医生沟通。', C.blue);
  rect(x + 24, y + 652, 345, 74, C.softGreen, 22, C.green);
  text(x + 196, y + 688, '默认本地处理；不保存整夜录音；不做医学诊断。', 18, C.green, 600, 'middle');
  nav(x, y, '我的');
});

const svg = `<?xml version="1.0" encoding="UTF-8"?>\n<svg xmlns="http://www.w3.org/2000/svg" width="${W}" height="${H}" viewBox="0 0 ${W} ${H}">\n${els.join('\n')}\n</svg>\n`;

writeFileSync(outSvg, svg, 'utf8');

writeFileSync(outHtml, `<!doctype html>
<html lang="zh-CN">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>睡了么 UI 交付稿</title>
  <style>
    body { margin: 0; background: #f4efe6; font-family: "Noto Sans SC", "Microsoft YaHei", Arial, sans-serif; }
    header { padding: 20px 28px; background: #16325C; color: white; }
    header h1 { margin: 0; font-size: 22px; }
    header p { margin: 6px 0 0; color: #dce8f6; }
    main { padding: 24px; overflow: auto; }
    img { display: block; width: 1990px; max-width: none; box-shadow: 0 18px 50px rgba(22, 50, 92, .18); }
  </style>
</head>
<body>
  <header>
    <h1>睡了么 UI 交付稿</h1>
    <p>Figma 限额恢复后，可直接导入同目录 SVG，或继续由 Codex 写入 Figma 文件。</p>
  </header>
  <main><img src="./gouxiong-sleep-ui-handoff.svg" alt="睡了么 UI 交付稿"></main>
</body>
</html>
`, 'utf8');

writeFileSync(outNotes, `# 睡了么 Figma 交付说明

## 当前状态

- 已创建 Figma 文件：<https://www.figma.com/design/n6CyKae43g7FjXP3O2Spop>
- 已写入 Figma：封面、品牌色、文字层级、组件样例、资产交接说明。
- 未继续写入 Figma 的原因：Starter 计划 MCP 调用限额已触发，Figma 拒绝后续写入。

## 本地可导入稿

- 参考图重做版 SVG：\`docs/figma/gouxiong-sleep-reference-redesign.svg\`
- 参考图重做版 HTML 预览：\`docs/figma/gouxiong-sleep-reference-redesign.html\`
- 参考图重做版 PNG 预览：\`docs/figma/gouxiong-sleep-reference-redesign-board.png\`
- 绘图模型素材任务单：\`docs/figma/reference-redesign-asset-generation-plan.md\`
- SVG：\`docs/figma/gouxiong-sleep-ui-handoff.svg\`
- HTML 预览：\`docs/figma/gouxiong-sleep-ui-handoff.html\`

导入 Figma 时，直接把 SVG 拖入 Figma 文件，或使用 Figma 的 Import 功能选择该 SVG。

优先使用“参考图重做版”作为视觉主稿：它直接对照用户提供的横向参考图，包含左侧品牌区、三台手机、右侧四个小助手和底部组件条。“ui-handoff” 是更完整的信息架构稿。

## 覆盖页面

1. 欢迎 / 直接开始
2. 守护首页
3. 睡前自检
4. 守护中 / 夜间状态
5. 强唤醒确认
6. 早安护理
7. 小助手 Live
8. 我的 / 设置

## 设计边界

- 不做诊断，不下医学结论。
- 强唤醒只说“高风险睡眠异常”，优先让用户确认安全。
- 首页只保留一个主行动，低频入口下沉到宫格或“我的”。
- 图片只负责视觉气质，按钮、文案、状态和无障碍由原生 Android View 承担。
`, 'utf8');

console.log(JSON.stringify({ svg: outSvg, html: outHtml, notes: outNotes }, null, 2));
