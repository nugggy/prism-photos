#!/usr/bin/env node
// Minimal mock of the Plex Media Server endpoints Prism uses (see docs/plex-api.md).
// Handy for developing and testing both clients without a real server.
//
//   node scripts/mock-plex-server.mjs [port] [sample.mp4]
//
// Android emulator: connect manually to http://10.0.2.2:32400 with any token.
// Web app:          connect manually to http://localhost:32400 with any token.
//
// Photos are proxied from picsum.photos (cached on disk), videos come from the sample file.

import http from 'node:http';
import https from 'node:https';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const PORT = Number(process.argv[2] || 32400);
const SAMPLE_MP4 = process.argv[3] || path.join(path.dirname(fileURLToPath(import.meta.url)), 'sample.mp4');
const CACHE_DIR = path.join(path.dirname(fileURLToPath(import.meta.url)), '.mock-cache');
fs.mkdirSync(CACHE_DIR, { recursive: true });

const MACHINE_ID = 'mock-machine-0001';
const SECTION_KEY = '3';

// ---------- data ----------
const albums = [
  { ratingKey: '1000', title: 'Holidays 2025', count: 0 },
  { ratingKey: '1001', title: 'Family', count: 0 },
  { ratingKey: '1002', title: 'Port Macquarie', count: 0 },
  { ratingKey: '1003', title: 'Screenshots', count: 0 },
];
const tagsPool = ['Beach', 'Family', 'Sunset', 'Dog', 'Food', 'Hiking'];
const items = [];
const start = Date.UTC(2025, 8, 20); // 20 September 2025
for (let i = 1; i <= 150; i++) {
  const isClip = i % 7 === 3;
  const taken = new Date(start - i * 26 * 60 * 60 * 1000);
  const album = albums[i % 4];
  album.count++;
  const portrait = i % 3 === 0;
  items.push({
    ratingKey: String(i),
    key: `/library/metadata/${i}`,
    type: isClip ? 'clip' : 'photo',
    title: isClip ? `VID_${1000 + i}.mp4` : `IMG_${1000 + i}.jpg`,
    summary: i % 10 === 0 ? 'A note about this photo.' : '',
    thumb: `/library/metadata/${i}/thumb/1`,
    originallyAvailableAt: taken.toISOString().slice(0, 10),
    addedAt: Math.floor(taken.getTime() / 1000),
    updatedAt: Math.floor(taken.getTime() / 1000),
    year: taken.getUTCFullYear(),
    userRating: i % 9 === 0 ? 10 : undefined,
    parentRatingKey: album.ratingKey,
    parentTitle: album.title,
    duration: isClip ? 15000 : undefined,
    Media: [{
      id: 5000 + i,
      width: portrait ? 3024 : 4032,
      height: portrait ? 4032 : 3024,
      aspectRatio: portrait ? 0.75 : 1.33,
      container: isClip ? 'mp4' : 'jpeg',
      aperture: 'f/1.8', exposure: '1/120', iso: 50, lens: '23mm', make: 'OnePlus', model: 'OnePlus 15',
      videoCodec: isClip ? 'h264' : undefined, audioCodec: isClip ? 'aac' : undefined,
      duration: isClip ? 15000 : undefined, videoResolution: isClip ? '1080' : undefined,
      Part: [{
        id: 5000 + i,
        key: `/library/parts/${5000 + i}/1/file.${isClip ? 'mp4' : 'jpg'}`,
        file: `/media/photos/${taken.getUTCFullYear()}/${isClip ? 'VID' : 'IMG'}_${1000 + i}.${isClip ? 'mp4' : 'jpg'}`,
        size: 2_500_000 + i * 1000,
        container: isClip ? 'mp4' : 'jpeg',
        duration: isClip ? 15000 : undefined,
      }],
    }],
    Tag: i % 5 === 0 ? [{ tag: tagsPool[i % tagsPool.length] }, { tag: tagsPool[(i + 1) % tagsPool.length] }] : undefined,
    Country: [{ tag: 'Australia' }],
    Place: i % 4 === 0 ? [{ tag: 'Port Macquarie' }] : undefined,
  });
}

function albumDirectory(a) {
  return {
    ratingKey: a.ratingKey, key: `/library/metadata/${a.ratingKey}/children`, type: 'photoalbum',
    title: a.title, thumb: items.find((x) => x.parentRatingKey === a.ratingKey)?.thumb,
    composite: `/library/metadata/${a.ratingKey}/composite/1`, leafCount: a.count, addedAt: Math.floor(start / 1000),
  };
}

// ---------- helpers ----------
function json(res, body, status = 200) {
  const s = JSON.stringify(body);
  res.writeHead(status, { 'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*', 'Access-Control-Allow-Headers': '*', 'Access-Control-Allow-Methods': 'GET,PUT,POST,DELETE,OPTIONS', 'Access-Control-Expose-Headers': '*' });
  res.end(s);
}
function container(extra) {
  return { MediaContainer: { size: (extra.Metadata || extra.Directory || []).length, ...extra } };
}
function paged(req, url, list) {
  const startAt = Number(req.headers['x-plex-container-start'] ?? url.searchParams.get('X-Plex-Container-Start') ?? 0);
  const size = Number(req.headers['x-plex-container-size'] ?? url.searchParams.get('X-Plex-Container-Size') ?? list.length);
  return { slice: list.slice(startAt, startAt + size), totalSize: list.length, offset: startAt };
}
function filterItems(url) {
  let list = items.slice();
  const type = url.searchParams.get('type');
  if (type === '13') list = list.filter((i) => i.type === 'photo');
  if (type === '12') list = list.filter((i) => i.type === 'clip');
  // userRating>=10 arrives as key "userRating>" value "10" or literal "userRating>=10"
  for (const [k, v] of url.searchParams) {
    if (k.startsWith('userRating>')) list = list.filter((i) => (i.userRating ?? 0) >= Number(v || 10));
  }
  const year = url.searchParams.get('year');
  if (year) list = list.filter((i) => String(i.year) === year);
  const query = url.searchParams.get('query');
  if (query) {
    const q = query.toLowerCase();
    list = list.filter((i) => i.title.toLowerCase().includes(q) || (i.Tag || []).some((t) => t.tag.toLowerCase().includes(q)) || (i.Place || []).some((p) => p.tag.toLowerCase().includes(q)));
  }
  const sort = url.searchParams.get('sort') || '';
  if (sort.startsWith('originallyAvailableAt')) {
    list.sort((a, b) => a.originallyAvailableAt.localeCompare(b.originallyAvailableAt));
    if (sort.endsWith(':desc')) list.reverse();
  }
  return list;
}

const memCache = new Map();
function proxyImage(res, upstream, cacheKey) {
  const file = path.join(CACHE_DIR, cacheKey.replace(/[^a-z0-9]/gi, '_'));
  if (fs.existsSync(file)) {
    res.writeHead(200, { 'Content-Type': 'image/jpeg', 'Access-Control-Allow-Origin': '*', 'Cache-Control': 'public, max-age=86400' });
    fs.createReadStream(file).pipe(res);
    return;
  }
  const follow = (u, hops = 0) => {
    https.get(u, (up) => {
      if ([301, 302, 303, 307, 308].includes(up.statusCode) && up.headers.location && hops < 5) { up.resume(); return follow(up.headers.location, hops + 1); }
      if (up.statusCode !== 200) { res.writeHead(502); res.end(); return; }
      res.writeHead(200, { 'Content-Type': up.headers['content-type'] || 'image/jpeg', 'Access-Control-Allow-Origin': '*', 'Cache-Control': 'public, max-age=86400' });
      const tmp = `${file}.${process.pid}-${Math.random().toString(36).slice(2)}.part`;
      const out = fs.createWriteStream(tmp);
      up.on('data', (c) => { out.write(c); res.write(c); });
      up.on('end', () => { out.end(() => { try { fs.renameSync(tmp, file); } catch { try { fs.unlinkSync(tmp); } catch {} } }); res.end(); });
      up.on('error', () => { out.destroy(); res.end(); });
    }).on('error', () => { res.writeHead(502); res.end(); });
  };
  follow(upstream);
}

function serveVideo(req, res) {
  if (!fs.existsSync(SAMPLE_MP4)) { res.writeHead(404); res.end('sample.mp4 missing'); return; }
  const stat = fs.statSync(SAMPLE_MP4);
  const range = req.headers.range;
  const common = { 'Content-Type': 'video/mp4', 'Accept-Ranges': 'bytes', 'Access-Control-Allow-Origin': '*' };
  if (range) {
    const m = /bytes=(\d*)-(\d*)/.exec(range);
    const s = m[1] ? Number(m[1]) : 0;
    const e = m[2] ? Math.min(Number(m[2]), stat.size - 1) : stat.size - 1;
    res.writeHead(206, { ...common, 'Content-Range': `bytes ${s}-${e}/${stat.size}`, 'Content-Length': e - s + 1 });
    fs.createReadStream(SAMPLE_MP4, { start: s, end: e }).pipe(res);
  } else {
    res.writeHead(200, { ...common, 'Content-Length': stat.size });
    fs.createReadStream(SAMPLE_MP4).pipe(res);
  }
}

// ---------- server ----------
process.on('uncaughtException', (e) => console.error('mock error:', e.message));

const server = http.createServer((req, res) => {
  const url = new URL(req.url, `http://${req.headers.host}`);
  const p = url.pathname;
  const log = `${req.method} ${p}${url.search ? url.search.slice(0, 80) : ''}`;
  if (!p.startsWith('/photo/') && !p.startsWith('/library/parts/')) console.log(log);

  if (req.method === 'OPTIONS') { json(res, {}, 204); return; }

  if (p === '/identity') return json(res, container({ machineIdentifier: MACHINE_ID, version: '1.41.0-mock' }));
  if (p === '/') return json(res, container({ friendlyName: 'Mock Plex Server', machineIdentifier: MACHINE_ID, version: '1.41.0-mock' }));
  if (p === '/library/sections') return json(res, container({ Directory: [{ key: SECTION_KEY, type: 'photo', title: 'Photos', agent: 'com.plexapp.agents.none', scanner: 'Plex Photo Scanner', uuid: 'mock-photos', thumb: '/:/resources/photo.png', updatedAt: Math.floor(Date.now() / 1000), Location: [{ id: 3, path: '/media/photos' }] }] }));

  if (p === `/library/sections/${SECTION_KEY}/all` && req.method === 'GET') {
    if (!url.searchParams.get('type')) {
      return json(res, container({ Metadata: [...albums.map(albumDirectory), ...items.slice(0, 5)], totalSize: albums.length + 5 }));
    }
    const list = filterItems(url);
    const { slice, totalSize, offset } = paged(req, url, list);
    return json(res, container({ Metadata: slice, totalSize, offset }));
  }
  if (p === `/library/sections/${SECTION_KEY}/all` && req.method === 'PUT') {
    const id = url.searchParams.get('id');
    const item = items.find((i) => i.ratingKey === id) || null;
    const album = albums.find((a) => a.ratingKey === id) || null;
    const target = item || album;
    if (!target) return json(res, {}, 404);
    const title = url.searchParams.get('title.value'); if (title != null) target.title = title;
    const summary = url.searchParams.get('summary.value'); if (summary != null && item) item.summary = summary;
    for (const [k, v] of url.searchParams) {
      if (item && /^tag\[\d*\]\.tag\.tag$/.test(k)) { item.Tag = [...(item.Tag || []), { tag: v }]; }
      if (item && /^tag\[\]\.tag\.tag-$/.test(k)) { item.Tag = (item.Tag || []).filter((t) => t.tag !== v); }
    }
    return json(res, container({}));
  }
  if (p === `/library/sections/${SECTION_KEY}/search`) {
    const list = filterItems(url);
    return json(res, container({ Metadata: list.slice(0, 200), totalSize: list.length }));
  }
  let m;
  if ((m = /^\/library\/metadata\/(\d+)\/children$/.exec(p))) {
    const kids = items.filter((i) => i.parentRatingKey === m[1]);
    return json(res, container({ Metadata: kids, totalSize: kids.length }));
  }
  if ((m = /^\/library\/metadata\/(\d+)$/.exec(p))) {
    if (req.method === 'DELETE') {
      const idx = items.findIndex((i) => i.ratingKey === m[1]);
      if (idx >= 0) items.splice(idx, 1);
      return json(res, container({}));
    }
    const item = items.find((i) => i.ratingKey === m[1]);
    if (item) return json(res, container({ Metadata: [item] }));
    const album = albums.find((a) => a.ratingKey === m[1]);
    if (album) return json(res, container({ Metadata: [albumDirectory(album)] }));
    return json(res, {}, 404);
  }
  if (p === '/:/rate') {
    const item = items.find((i) => i.ratingKey === url.searchParams.get('key'));
    if (item) { const r = Number(url.searchParams.get('rating')); item.userRating = r > 0 ? r : undefined; }
    return json(res, container({}));
  }
  if (p === '/photo/:/transcode') {
    const target = url.searchParams.get('url') || '';
    const id = (/metadata\/(\d+)/.exec(target) || [])[1] || '1';
    const w = Math.min(Number(url.searchParams.get('width') || 400), 1600);
    const h = Math.min(Number(url.searchParams.get('height') || 400), 1600);
    return proxyImage(res, `https://picsum.photos/seed/prism${id}/${w}/${h}`, `thumb-${id}-${w}x${h}`);
  }
  if ((m = /^\/library\/parts\/(\d+)\/\d+\/file\.(jpg|mp4)$/.exec(p))) {
    if (m[2] === 'mp4') return serveVideo(req, res);
    const id = Number(m[1]) - 5000;
    const item = items.find((i) => i.ratingKey === String(id));
    const portrait = item ? item.Media[0].height > item.Media[0].width : false;
    return proxyImage(res, `https://picsum.photos/seed/prism${id}/${portrait ? 1200 : 1600}/${portrait ? 1600 : 1200}`, `full-${id}`);
  }
  if (p.startsWith('/video/:/transcode/universal/start')) { res.writeHead(404); res.end(); return; }
  if (p.startsWith('/video/:/transcode/universal/stop')) return json(res, container({}));
  res.writeHead(404, { 'Access-Control-Allow-Origin': '*' });
  res.end();
});

server.listen(PORT, '0.0.0.0', () => console.log(`Mock Plex server on http://localhost:${PORT} (emulator: http://10.0.2.2:${PORT})`));
