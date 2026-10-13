/** Triggers a browser download of a URL (or blob URL) with a suggested filename. */
export function downloadUrl(url: string, filename: string): void {
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  a.rel = 'noopener'
  document.body.appendChild(a)
  a.click()
  a.remove()
}

/** Starts several downloads in sequence with a short gap, which browsers allow where a burst is blocked. */
export async function downloadMany(files: { url: string; filename: string }[], gapMs = 400): Promise<void> {
  for (let i = 0; i < files.length; i++) {
    if (i > 0) await new Promise((resolve) => setTimeout(resolve, gapMs))
    downloadUrl(files[i].url, files[i].filename)
  }
}

/** Downloads a blob, keeping its object URL alive long enough for the browser to start saving it. */
export function downloadBlob(blob: Blob, filename: string, revokeAfterMs = 60_000): void {
  const url = URL.createObjectURL(blob)
  downloadUrl(url, filename)
  setTimeout(() => URL.revokeObjectURL(url), revokeAfterMs)
}

export async function fetchAsFile(url: string, filename: string, mimeType?: string): Promise<File> {
  const res = await fetch(url)
  if (!res.ok) throw new Error(`Failed to fetch file (${res.status})`)
  const blob = await res.blob()
  return new File([blob], filename, { type: mimeType ?? blob.type })
}
