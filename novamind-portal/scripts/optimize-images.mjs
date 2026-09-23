/**
 * public/resource 图片原地优化。
 * - 遍历 public/resource 下所有 jpg / jpeg / png
 * - 跳过 <200KB 的文件
 * - jpg/jpeg: mozjpeg 质量 80，最长边压到 1920（不放大）
 * - png: 仅无损级压缩（compressionLevel 9），不做有损转换以保护透明背景
 * 原地覆盖，输出优化前后总体积对比。
 */
import fs from 'node:fs'
import path from 'node:path'
import sharp from 'sharp'

const ROOT = path.resolve(process.cwd(), 'public/resource')
const MIN_BYTES = 200 * 1024
const MAX_EDGE = 1920

const formats = new Set(['.jpg', '.jpeg', '.png'])

function walk(dir) {
  const files = []
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name)
    if (entry.isDirectory()) files.push(...walk(full))
    else if (formats.has(path.extname(entry.name).toLowerCase())) files.push(full)
  }
  return files
}

function formatSize(bytes) {
  return `${(bytes / 1024 / 1024).toFixed(2)} MB`
}

async function optimize(file) {
  const stat = fs.statSync(file)
  const ext = path.extname(file).toLowerCase()
  const before = stat.size

  if (before < MIN_BYTES) {
    return { file, before, after: before, skipped: true }
  }

  try {
    let pipeline
    if (ext === '.png') {
      // PNG 仅无损级压缩：compressionLevel 9 + palette 尝试会破坏透明/渐变，保守不加 palette
      pipeline = sharp(file).png({ compressionLevel: 9, effort: 10 })
    } else {
      pipeline = sharp(file)
        .resize({ width: MAX_EDGE, height: MAX_EDGE, fit: 'inside', withoutEnlargement: true })
        .jpeg({ quality: 80, mozjpeg: true })
    }

    const { data, info } = await pipeline.toBuffer({ resolveWithObject: true })
    // 仅当确实变小才覆盖
    if (info.size < before) {
      fs.writeFileSync(file, data)
      return { file, before, after: info.size, skipped: false }
    }
    return { file, before, after: before, skipped: true }
  } catch (err) {
    console.warn(`[skip] ${file}: ${err.message}`)
    return { file, before, after: before, skipped: true, failed: true }
  }
}

const files = walk(ROOT)
console.log(`found ${files.length} images under public/resource`)

let totalBefore = 0
let totalAfter = 0
let changed = 0
let failed = 0

const results = []
for (const file of files) {
  const r = await optimize(file)
  totalBefore += r.before
  totalAfter += r.after
  if (!r.skipped) {
    changed += 1
    console.log(`[ok] ${path.relative(ROOT, r.file)}: ${formatSize(r.before)} -> ${formatSize(r.after)}`)
  }
  if (r.failed) failed += 1
  results.push(r)
}

console.log('--- summary ---')
console.log(`files: ${files.length}, changed: ${changed}, skipped: ${files.length - changed}, failed: ${failed}`)
console.log(`total: ${formatSize(totalBefore)} -> ${formatSize(totalAfter)} (saved ${formatSize(totalBefore - totalAfter)})`)
