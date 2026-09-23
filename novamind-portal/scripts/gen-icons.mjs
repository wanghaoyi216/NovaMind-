/**
 * 生成 PWA / Apple Touch 品牌图标。
 * 输入源：用户手作应用图标 ../SVG/novamind-icon-static.svg（1024 squircle 深空星云母版）。
 * 用 sharp 栅格化输出：
 *   public/pwa-icons/pwa-192x192.png
 *   public/pwa-icons/pwa-512x512.png
 *   public/pwa-icons/apple-touch-icon.png (180x180, 压平到深空底，全出血)
 */
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import sharp from 'sharp'

const projectRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const sourceSvg = path.resolve(projectRoot, '../SVG/novamind-icon-static.svg')
const outDir = path.resolve(projectRoot, 'public/pwa-icons')

if (!fs.existsSync(sourceSvg)) {
  console.error(`source icon not found: ${sourceSvg}`)
  process.exit(1)
}

const svgSource = fs.readFileSync(sourceSvg, 'utf-8')

async function renderPng({ size, outFile, flatten = false }) {
  let pipeline = sharp(Buffer.from(svgSource)).resize(size, size, { fit: 'cover' })
  if (flatten) {
    // Apple Touch Icon：squircle 透明角压平到深空底色（iOS 自行裁圆角）
    pipeline = pipeline.flatten({ background: '#050410' })
  }
  const out = path.join(outDir, outFile)
  await pipeline.png({ compressionLevel: 9 }).toFile(out)
  const stat = fs.statSync(out)
  console.log(`generated ${out} (${stat.size} bytes)`)
}

fs.mkdirSync(outDir, { recursive: true })

await renderPng({ size: 512, outFile: 'pwa-512x512.png' })
await renderPng({ size: 192, outFile: 'pwa-192x192.png' })
await renderPng({ size: 180, outFile: 'apple-touch-icon.png', flatten: true })

console.log('done.')
