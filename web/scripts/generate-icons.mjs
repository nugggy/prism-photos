// Renders the PWA PNG icons from the shapes in public/logo.svg, with no dependencies beyond
// Node itself (zlib for PNG compression). Run with: node scripts/generate-icons.mjs
// If logo.svg changes, update SHAPES below to match.

import { writeFileSync } from 'node:fs'
import { deflateSync } from 'node:zlib'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const BG = [0x1f, 0x23, 0x26]
const GOLD = [0xe5, 0xa0, 0x0d]

// Geometry in the logo's 108 x 108 viewBox.
const roundRect = (x0, y0, x1, y1, r) => (x, y) => {
  if (x < x0 || x > x1 || y < y0 || y > y1) return false
  const cx = Math.min(Math.max(x, x0 + r), x1 - r)
  const cy = Math.min(Math.max(y, y0 + r), y1 - r)
  return (x - cx) ** 2 + (y - cy) ** 2 <= r * r
}
const circle = (cx, cy, r) => (x, y) => (x - cx) ** 2 + (y - cy) ** 2 <= r * r
const polygon = (points) => (x, y) => {
  let inside = false
  for (let i = 0, j = points.length - 1; i < points.length; j = i++) {
    const [xi, yi] = points[i]
    const [xj, yj] = points[j]
    if (yi > y !== yj > y && x < ((xj - xi) * (y - yi)) / (yj - yi) + xi) inside = !inside
  }
  return inside
}
const union = (...tests) => (x, y) => tests.some((t) => t(x, y))

const BACKGROUND = { test: roundRect(0, 0, 108, 108, 24), colour: BG, alpha: 1 }
const FOREGROUND = [
  { test: union(roundRect(23, 38, 85, 80, 8), polygon([[44, 38.5], [48, 30], [64, 30], [68, 38.5]])), colour: GOLD, alpha: 1 },
  { test: circle(74, 44, 2.5), colour: BG, alpha: 1 },
  { test: circle(54, 59, 16), colour: BG, alpha: 1 },
  { test: circle(54, 59, 12.5), colour: GOLD, alpha: 0.25 },
  { test: polygon([[48.5, 50], [53.5, 50], [59.5, 59], [53.5, 68], [48.5, 68], [54.5, 59]]), colour: GOLD, alpha: 1 },
]

const LAYERS = [BACKGROUND, ...FOREGROUND]
// Maskable icons are cropped to a shape by the launcher, so the background covers everything.
const MASKABLE_LAYERS = [{ test: () => true, colour: BG, alpha: 1 }, ...FOREGROUND]

/** Renders RGBA pixels. Maskable icons fill the whole square and shrink the artwork into the safe zone. */
function render(size, maskable) {
  const SS = 4 // 4 x 4 supersampling for smooth edges
  const pixels = Buffer.alloc(size * size * 4)
  const scale = maskable ? 0.78 : 1
  for (let py = 0; py < size; py++) {
    for (let px = 0; px < size; px++) {
      let r = 0, g = 0, b = 0, a = 0
      for (let sy = 0; sy < SS; sy++) {
        for (let sx = 0; sx < SS; sx++) {
          // Sample position in viewBox units, scaled about the centre for maskable icons.
          const u = ((px + (sx + 0.5) / SS) / size) * 108
          const v = ((py + (sy + 0.5) / SS) / size) * 108
          const x = 54 + (u - 54) / scale
          const y = 54 + (v - 54) / scale
          let cr = 0, cg = 0, cb = 0, ca = 0
          for (const layer of maskable ? MASKABLE_LAYERS : LAYERS) {
            if (!layer.test(x, y)) continue
            const la = layer.alpha
            cr = layer.colour[0] * la + cr * (1 - la)
            cg = layer.colour[1] * la + cg * (1 - la)
            cb = layer.colour[2] * la + cb * (1 - la)
            ca = la + ca * (1 - la)
          }
          r += cr; g += cg; b += cb; a += ca
        }
      }
      const n = SS * SS
      const i = (py * size + px) * 4
      const alpha = a / n
      // Colours were accumulated premultiplied; un-premultiply for PNG.
      pixels[i] = alpha > 0 ? Math.round(r / n / alpha) : 0
      pixels[i + 1] = alpha > 0 ? Math.round(g / n / alpha) : 0
      pixels[i + 2] = alpha > 0 ? Math.round(b / n / alpha) : 0
      pixels[i + 3] = Math.round(alpha * 255)
    }
  }
  return pixels
}

const CRC_TABLE = Array.from({ length: 256 }, (_, n) => {
  let c = n
  for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1
  return c >>> 0
})
function crc32(buf) {
  let c = 0xffffffff
  for (const byte of buf) c = CRC_TABLE[(c ^ byte) & 0xff] ^ (c >>> 8)
  return (c ^ 0xffffffff) >>> 0
}
function chunk(type, data) {
  const len = Buffer.alloc(4)
  len.writeUInt32BE(data.length)
  const body = Buffer.concat([Buffer.from(type, 'ascii'), data])
  const crc = Buffer.alloc(4)
  crc.writeUInt32BE(crc32(body))
  return Buffer.concat([len, body, crc])
}
function encodePng(size, rgba) {
  const ihdr = Buffer.alloc(13)
  ihdr.writeUInt32BE(size, 0)
  ihdr.writeUInt32BE(size, 4)
  ihdr[8] = 8 // bit depth
  ihdr[9] = 6 // RGBA
  const raw = Buffer.alloc(size * (size * 4 + 1))
  for (let y = 0; y < size; y++) {
    raw[y * (size * 4 + 1)] = 0 // filter: none
    rgba.copy(raw, y * (size * 4 + 1) + 1, y * size * 4, (y + 1) * size * 4)
  }
  const signature = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a])
  return Buffer.concat([signature, chunk('IHDR', ihdr), chunk('IDAT', deflateSync(raw, { level: 9 })), chunk('IEND', Buffer.alloc(0))])
}

const publicDir = join(dirname(fileURLToPath(import.meta.url)), '..', 'public')
for (const [name, size, maskable] of [
  ['icon-192.png', 192, false],
  ['icon-512.png', 512, false],
  ['icon-maskable-512.png', 512, true],
]) {
  writeFileSync(join(publicDir, name), encodePng(size, render(size, maskable)))
  console.log(`Wrote public/${name}`)
}
