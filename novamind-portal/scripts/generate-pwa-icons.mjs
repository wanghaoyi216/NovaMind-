/**
 * 生成 PWA manifest 所需的 192x192 / 512x512 PNG 图标。
 * 纯 Node.js 实现，不依赖任何图像库。
 * 设计：一个深棕底色 + 白色 "N" 字（NovaMind 品牌色 #7a4f2f）。
 */
import fs from 'node:fs'
import path from 'node:path'
import zlib from 'node:zlib'

const COLOR_BG = [0x7a, 0x4f, 0x2f] // 品牌主色
const COLOR_FG = [0xff, 0xff, 0xff] // 白字

function crc32(buf) {
  let c
  const table = new Uint32Array(256)
  for (let n = 0; n < 256; n++) {
    c = n
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1
    table[n] = c >>> 0
  }
  let crc = 0xffffffff
  for (let i = 0; i < buf.length; i++) crc = table[(crc ^ buf[i]) & 0xff] ^ (crc >>> 8)
  return (crc ^ 0xffffffff) >>> 0
}

function chunk(type, data) {
  const len = Buffer.alloc(4)
  len.writeUInt32BE(data.length, 0)
  const typeBuf = Buffer.from(type, 'ascii')
  const crc = Buffer.alloc(4)
  crc.writeUInt32BE(crc32(Buffer.concat([typeBuf, data])), 0)
  return Buffer.concat([len, typeBuf, data, crc])
}

// 在网格上画粗体 "N"（5 像素笔画宽）
function makeMask(size) {
  const grid = Array.from({ length: size }, () => new Uint8Array(size))
  const w = Math.max(8, Math.round(size * 0.18)) // 笔画宽
  const pad = Math.round(size * 0.18)
  const top = pad
  const bottom = size - pad
  const left = pad
  const right = size - pad

  // 左竖
  for (let y = top; y <= bottom; y++)
    for (let x = left; x < left + w; x++) grid[y][x] = 1
  // 右竖
  for (let y = top; y <= bottom; y++)
    for (let x = right - w; x < right; x++) grid[y][x] = 1
  // 左下到右上的对角线
  for (let i = 0; i < bottom - top; i++) {
    const y0 = top + i
    const x0 = left + Math.round((i * (right - left - w)) / (bottom - top))
    for (let dx = 0; dx < w; dx++) {
      const x = x0 + dx
      if (x >= 0 && x < size && y0 >= 0 && y0 < size) grid[y0][x] = 1
    }
  }
  return grid
}

function buildPng(size) {
  const sig = Buffer.from([137, 80, 78, 71, 13, 10, 26, 10])
  const ihdr = Buffer.alloc(13)
  ihdr.writeUInt32BE(size, 0)
  ihdr.writeUInt32BE(size, 4)
  ihdr[8] = 8 // 8 bit depth
  ihdr[9] = 2 // truecolor RGB
  ihdr[10] = 0
  ihdr[11] = 0
  ihdr[12] = 0

  const mask = makeMask(size)
  // 圆角处理：超出 r 半径的角为透明。简化：不做圆角，但减少右上/右下角
  const raw = []
  for (let y = 0; y < size; y++) {
    raw.push(0) // filter byte
    for (let x = 0; x < size; x++) {
      const c = mask[y][x] ? COLOR_FG : COLOR_BG
      raw.push(c[0], c[1], c[2])
    }
  }
  const idatData = zlib.deflateSync(Buffer.from(raw))
  return Buffer.concat([
    sig,
    chunk('IHDR', ihdr),
    chunk('IDAT', idatData),
    chunk('IEND', Buffer.alloc(0)),
  ])
}

const outDir = path.resolve(process.cwd(), 'public/pwa-icons')
fs.mkdirSync(outDir, { recursive: true })

for (const size of [192, 512]) {
  const buf = buildPng(size)
  const file = path.join(outDir, `pwa-${size}x${size}.png`)
  fs.writeFileSync(file, buf)
  console.log(`generated ${file} (${buf.length} bytes)`)
}
