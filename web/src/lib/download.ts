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

export async function fetchAsFile(url: string, filename: string, mimeType?: string): Promise<File> {
  const res = await fetch(url)
  if (!res.ok) throw new Error(`Failed to fetch file (${res.status})`)
  const blob = await res.blob()
  return new File([blob], filename, { type: mimeType ?? blob.type })
}
