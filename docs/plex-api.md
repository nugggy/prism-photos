# Plex API reference for Prism

This is the single reference both the Android app and the web app implement
against. Always send `Accept: application/json` so Plex returns JSON rather than XML.

## Required headers (every request to plex.tv and to a server)

```
Accept: application/json
X-Plex-Product: Prism
X-Plex-Version: <app version>
X-Plex-Client-Identifier: <stable UUID generated once per install and persisted>
X-Plex-Platform: Android | Web
X-Plex-Platform-Version: <os version or browser>
X-Plex-Device: <device model, e.g. OnePlus 15 or Chrome>
X-Plex-Device-Name: Prism
X-Plex-Token: <token>   (once you have one; may also go in the query string as X-Plex-Token=)
```

For image and video URLs that are loaded by an image loader or a media player
(no custom headers possible), append `X-Plex-Token=<token>` as a query parameter.

## Sign in with a PIN (plex.tv)

1. `POST https://plex.tv/api/v2/pins?strong=true` with the headers above (no token yet).
   Response: `{ "id": 123, "code": "abcd...", "expiresAt": "...", "authToken": null }`
2. Open this URL in the browser for the user:
   `https://app.plex.tv/auth#?clientID=<clientId>&code=<code>&context%5Bdevice%5D%5Bproduct%5D=Prism`
3. Poll `GET https://plex.tv/api/v2/pins/{id}` every 2 seconds until `authToken` is non null
   (give up after 10 minutes). That `authToken` is the account token.
4. `GET https://plex.tv/api/v2/user` with the token returns `{ id, uuid, username, email, thumb, title }`.

## Discover servers (plex.tv)

`GET https://plex.tv/api/v2/resources?includeHttps=1&includeRelay=1&includeIPv6=1`

Returns an array of devices. Keep those where `provides` contains `"server"`.

```json
[{
  "name": "Home Server",
  "product": "Plex Media Server",
  "productVersion": "1.41.0",
  "platform": "Linux",
  "clientIdentifier": "abc123",
  "provides": "server",
  "owned": true,
  "accessToken": "server-scoped token, use this for server requests",
  "publicAddress": "203.0.113.5",
  "httpsRequired": false,
  "connections": [
    { "protocol": "https", "address": "192-168-1-20.abcdef.plex.direct", "port": 32400,
      "uri": "https://192-168-1-20.abcdef.plex.direct:32400", "local": true, "relay": false, "IPv6": false },
    { "protocol": "http", "address": "192.168.1.20", "port": 32400,
      "uri": "http://192.168.1.20:32400", "local": true, "relay": false, "IPv6": false },
    { "protocol": "https", "address": "203-0-113-5.abcdef.plex.direct", "port": 32400,
      "uri": "https://203-0-113-5.abcdef.plex.direct:32400", "local": false, "relay": false, "IPv6": false },
    { "protocol": "https", "address": "abcdef.plex.direct", "port": 443,
      "uri": "https://abcdef.plex.direct:443", "local": false, "relay": true, "IPv6": false }
  ]
}]
```

Connection choosing rules (implement as a pure function so it can be unit tested):

- Mode `AUTO`: test all connections in parallel with `GET {uri}/identity` (2.5 s timeout).
  Among successes pick by rank: local non relay first, then remote non relay, then relay.
  On a tie prefer https.
- Mode `LAN`: same but local connections are tried first and only if none succeed
  are remote and relay tried.
- Mode `REMOTE`: skip connections with `local == true`.
- A user supplied manual URL (for example `http://192.168.1.20:32400`) is tried first in
  every mode when set.
- Web app note: a page served over https cannot load plain `http://` server URLs
  (mixed content). Prefer the `*.plex.direct` https URIs; they resolve to the LAN IP too.

`GET {server}/identity` returns `{ "MediaContainer": { "machineIdentifier": "...", "version": "..." } }`
and needs no token. Use it as the reachability probe.

## Manual sign in

The user can also enter a server URL and token directly. Verify with
`GET {server}/?X-Plex-Token=...` (returns MediaContainer with `friendlyName`).

## Libraries

`GET {server}/library/sections`

```json
{ "MediaContainer": { "Directory": [
  { "key": "3", "type": "photo", "title": "Photos", "agent": "com.plexapp.agents.none",
    "scanner": "Plex Photo Scanner", "uuid": "...", "thumb": "/:/resources/photo.png",
    "updatedAt": 1700000000, "Location": [{ "id": 3, "path": "/media/photos" }] }
] } }
```

Only `type == "photo"` sections matter.

## Listing photos and videos (timeline)

Everything in the library, flattened, newest first, paged:

```
GET {server}/library/sections/{sectionKey}/all?type=13&sort=originallyAvailableAt:desc
GET {server}/library/sections/{sectionKey}/all?type=12&sort=originallyAvailableAt:desc
```

`type=13` is photo, `type=12` is clip (video in a photo library). Fetch both and
merge by `originallyAvailableAt` (fallback `addedAt`). Page with headers
`X-Plex-Container-Start: 0` and `X-Plex-Container-Size: 500` (or query params of the
same names). `MediaContainer.totalSize` gives the total.

Include these query params to get richer metadata:
`includeExtras=0&includeRelated=0&excludeFields=summary` is NOT needed; keep defaults.

Item shape (`MediaContainer.Metadata[]`):

```json
{
  "ratingKey": "1234", "key": "/library/metadata/1234", "guid": "...",
  "type": "photo",                       // or "clip"
  "title": "IMG_0001.jpg",
  "summary": "",
  "index": 1,
  "year": 2024,
  "thumb": "/library/metadata/1234/thumb/1700000000",
  "originallyAvailableAt": "2024-03-14",  // date only; use Media.originallyAvailableAt or createdAtTZOffset when present
  "addedAt": 1710403200, "updatedAt": 1710403200,
  "userRating": 10.0,                    // present only when rated; 10 == favourite
  "createdAtAccuracy": "local", "createdAtTZOffset": "36000",
  "parentRatingKey": "1200", "parentKey": "/library/metadata/1200", "parentTitle": "2024-03",
  "duration": 12345,                     // clips only, milliseconds
  "Media": [{
    "id": 5678, "width": 4032, "height": 3024, "aspectRatio": 1.33, "container": "jpeg",
    "aperture": "f/1.8", "exposure": "1/120", "iso": 50, "lens": "23mm", "make": "OnePlus", "model": "OnePlus 15",
    "videoCodec": "hevc", "audioCodec": "aac", "duration": 12345, "videoResolution": "4k", // clips
    "Part": [{ "id": 5678, "key": "/library/parts/5678/1700000000/file.jpg", "file": "/media/photos/2024/IMG_0001.jpg",
               "size": 3456789, "container": "jpeg", "duration": 12345 }]
  }],
  "Tag": [{ "tag": "Beach" }],            // Plex auto tags, when present
  "Country": [{ "tag": "Australia" }],
  "Place": [{ "tag": "Port Macquarie" }]   // may be absent
}
```

Domain mapping:

- `id` = `ratingKey`
- `isVideo` = `type == "clip"`
- `takenAt` = parse `originallyAvailableAt` (YYYY-MM-DD) as local midnight; if absent use `addedAt`.
- `width/height` from `Media[0]`
- `partKey` = `Media[0].Part[0].key` (full resolution file)
- `thumb` = `thumb`
- `favourite` = `userRating >= 10`
- `exif` from `Media[0]` fields; `fileSize` from `Part[0].size`; `filePath` from `Part[0].file`
- `duration` in ms for clips
- `albumId` = `parentRatingKey`

## Albums (photo albums / folders)

Top level of a library: `GET {server}/library/sections/{sectionKey}/all` with no `type`
returns a mix of albums (`type == "photoalbum"`, key like `/library/metadata/1200/children`)
and loose items at the root.

Children of an album: `GET {server}/library/metadata/{albumRatingKey}/children`
returns nested albums (`photoalbum`) and items (`photo`, `clip`). Album objects have
`ratingKey`, `title`, `thumb`, `composite` (a collage thumb), `leafCount`, `addedAt`.

Album cover URL: use `composite` if present else `thumb`.

## Images

Thumbnail via the server transcoder (always use this for grids; it is fast and small):

```
{server}/photo/:/transcode?width={w}&height={h}&minSize=1&upscale=1&url={urlencoded thumb path}&X-Plex-Token={token}
```

Where `url` is the item `thumb` (e.g. `/library/metadata/1234/thumb/1700000000`). Use
`width=400&height=400` for grid cells, `width=1600&height=1600` for the viewer's first
frame, and the original for zooming:

Full resolution original: `{server}{partKey}?X-Plex-Token={token}` (e.g.
`{server}/library/parts/5678/1700000000/file.jpg?X-Plex-Token=...`). The response has
the file's real content type. Downloads use `?download=1` appended.

## Video

Direct play (preferred, works for mp4/mov h264/hevc on modern devices and browsers):

```
{server}{partKey}?X-Plex-Token={token}
```

Transcode fallback as HLS (when the container or codec is not supported):

```
{server}/video/:/transcode/universal/start.m3u8
  ?path={urlencoded "/library/metadata/" + ratingKey}
  &mediaIndex=0&partIndex=0&protocol=hls&fastSeek=1&directPlay=0&directStream=1
  &videoQuality=100&maxVideoBitrate=20000&videoResolution=1920x1080
  &session={random session id}&X-Plex-Client-Identifier={clientId}
  &X-Plex-Platform={platform}&X-Plex-Product=Prism&X-Plex-Token={token}
```

Stop a transcode session when leaving: `GET {server}/video/:/transcode/universal/stop?session={session}`.

Video thumbnail for grids uses the same `/photo/:/transcode` URL with the clip's `thumb`.

## Favourites

Rate 10 to favourite, remove rating to unfavourite:

```
PUT {server}/:/rate?key={ratingKey}&identifier=com.plexapp.plugins.library&rating=10
PUT {server}/:/rate?key={ratingKey}&identifier=com.plexapp.plugins.library&rating=-1
```

List favourites: `GET {server}/library/sections/{sectionKey}/all?type=13&userRating>=10`
and the same with `type=12`. (The `>=` must be URL encoded as `%3E%3D`.)

## Search

```
GET {server}/library/sections/{sectionKey}/search?type=13&query={q}
GET {server}/library/sections/{sectionKey}/search?type=12&query={q}
```

Also supports filtering the timeline by tag or year:
`/library/sections/{sectionKey}/all?type=13&tag={tagId}` and `&year=2024`.

## Editing metadata (rename, description, tags)

Plex edit endpoint (PUT). `type` must match the item type (13 photo, 12 clip, 14 photoalbum).

```
PUT {server}/library/sections/{sectionKey}/all?type=13&id={ratingKey}&title.value={t}&title.locked=1
PUT {server}/library/sections/{sectionKey}/all?type=13&id={ratingKey}&summary.value={s}&summary.locked=1
PUT {server}/library/sections/{sectionKey}/all?type=13&id={ratingKey}&tag[0].tag.tag={tag}&tag.locked=1
PUT {server}/library/sections/{sectionKey}/all?type=13&id={ratingKey}&tag[].tag.tag-={tag}    // remove
```

Album cover: `POST {server}/library/metadata/{albumRatingKey}/posters?url={urlencoded image url}`.
This is not reliable for photo albums on all server versions, so also keep a local
override (item ratingKey to use as cover) and prefer the local override when set.

## Delete

`DELETE {server}/library/metadata/{ratingKey}` deletes the file on disk. It only works
if the server setting "Allow media deletion" is on; otherwise the server returns 403.
Always confirm with the user first and show a clear message on 403.

## Plex.tv account

Sign out: `DELETE https://plex.tv/api/v2/users/signout` with the account token (best effort).

## Errors

- 401: token invalid or expired. Clear the session and return to sign in.
- 403 on delete: media deletion disabled on the server.
- Connection failures: re-run the connection chooser once, then surface the error.

## Verified against a real server (Plex Media Server 1.43.4, 30/09/2026)

These findings override the sections above where they differ. Both clients implement them.

- `all?type=13` and `all?type=12` return NOTHING on 1.43 (size 0), sorted or not, paged or not.
- The flat timeline is `all?clusterZoomLevel=1` (no type). It returns photos and videos together,
  supports `sort=originallyAvailableAt:desc` and the `X-Plex-Container-Start/Size` headers,
  and reports `totalSize`. `type=13&clusterZoomLevel=1` gives photos only, `type=12` videos only.
- Albums: `all?type=14` lists albums (paged, `totalSize`). They come back with `type: "photo"`,
  a `key` ending in `/children`, no `Media`, and a `composite` image. Treat any entry whose key
  ends in `/children` as an album regardless of its `type`. `leafCount` is absent.
- The plain `all` (no params) returns only the root level (albums, and any loose items).
- `/library/metadata/{albumId}/children` returns the album's photos (`type: "photo"`), videos
  (`type: "clip"`) and nested albums (key ends in `/children`).
- Favourites: `all?clusterZoomLevel=1&userRating>=10` with NO type filter. The `>=` must be sent
  raw; `userRating%3E%3D10` is a 400 Bad Request.
- Search: `search?type=13&query=...` works; `search?query=...` without a type is a 400.
  `all?clusterZoomLevel=1&title=IMG` filters by title substring and works as a fallback.
- Hubs: `/hubs/sections/{key}` returns Recently Added, Recently Favorited and year hubs.
- Filters available for `type=13`: year, make, model, aperture, exposure, iso, lens, tag, trash, location, place.
- Photo playlists (the user-editable albums Plex apps show):
  - list: `GET /playlists?playlistType=photo` (a smart "Favorites" playlist exists by default)
  - items: `GET /playlists/{id}/items` (each item has `playlistItemID`)
  - create: `POST /playlists?type=photo&smart=0&title={t}&uri={uri}` where
    `uri = server://{machineIdentifier}/com.plexapp.plugins.library/library/metadata/{id1,id2}` (URL encoded)
  - add items: `PUT /playlists/{id}/items?uri={uri}` (duplicates are accepted silently)
  - remove item: `DELETE /playlists/{id}/items/{playlistItemID}`
  - rename: `PUT /playlists/{id}?title={t}`
  - delete: `DELETE /playlists/{id}` (204)
  Folder albums (`type=14`) cannot be modified through the API.
