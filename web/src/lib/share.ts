// Share sheet helpers: Web Share API (with files when supported), plus direct links.

export interface ShareTarget {
  id: string
  label: string
  buildUrl: (link: string, text: string) => string
}

export const SHARE_TARGETS: ShareTarget[] = [
  { id: 'whatsapp', label: 'WhatsApp', buildUrl: (link, text) => `https://wa.me/?text=${encodeURIComponent(`${text} ${link}`)}` },
  { id: 'telegram', label: 'Telegram', buildUrl: (link) => `https://t.me/share/url?url=${encodeURIComponent(link)}` },
  { id: 'email', label: 'Email', buildUrl: (link, text) => `mailto:?subject=${encodeURIComponent(text)}&body=${encodeURIComponent(link)}` },
  { id: 'messenger', label: 'Messenger', buildUrl: (link) => `fb-messenger://share?link=${encodeURIComponent(link)}` },
]

export async function canShareFiles(files: File[]): Promise<boolean> {
  const nav = navigator as Navigator & { canShare?: (data: ShareData) => boolean }
  if (!nav.canShare) return false
  try {
    return nav.canShare({ files })
  } catch {
    return false
  }
}

export async function shareFiles(files: File[], title: string, text?: string): Promise<boolean> {
  const nav = navigator as Navigator & { share?: (data: ShareData) => Promise<void> }
  if (!nav.share) return false
  try {
    await nav.share({ files, title, text })
    return true
  } catch {
    return false
  }
}

export async function shareLink(link: string, title: string, text?: string): Promise<boolean> {
  const nav = navigator as Navigator & { share?: (data: ShareData) => Promise<void> }
  if (!nav.share) return false
  try {
    await nav.share({ url: link, title, text })
    return true
  } catch {
    return false
  }
}

export async function copyToClipboard(text: string): Promise<boolean> {
  try {
    await navigator.clipboard.writeText(text)
    return true
  } catch {
    return false
  }
}
