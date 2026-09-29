import { useState } from 'react'
import { canShareFiles, copyToClipboard, shareFiles, shareLink, SHARE_TARGETS } from '../lib/share'
import { downloadUrl, fetchAsFile } from '../lib/download'
import { Icon } from './Icon'

export interface ShareSheetProps {
  title: string
  link: string
  fileUrl?: string
  fileName?: string
  onClose: () => void
}

export function ShareSheet({ title, link, fileUrl, fileName, onClose }: ShareSheetProps): React.ReactElement {
  const [busy, setBusy] = useState(false)
  const [copied, setCopied] = useState(false)

  const handleShareFile = async () => {
    if (!fileUrl || !fileName) return
    setBusy(true)
    try {
      const file = await fetchAsFile(fileUrl, fileName)
      if (await canShareFiles([file])) {
        await shareFiles([file], title)
        onClose()
        return
      }
      downloadUrl(fileUrl, fileName)
      onClose()
    } finally {
      setBusy(false)
    }
  }

  const handleShareLink = async () => {
    const ok = await shareLink(link, title)
    if (ok) onClose()
  }

  const handleCopy = async () => {
    const ok = await copyToClipboard(link)
    setCopied(ok)
  }

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-label="Share"
      style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.5)', display: 'flex', alignItems: 'flex-end', justifyContent: 'center', zIndex: 200 }}
      onClick={onClose}
    >
      <div className="card" style={{ width: 420, maxWidth: '100%', borderBottomLeftRadius: 0, borderBottomRightRadius: 0 }} onClick={(e) => e.stopPropagation()}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <h2 style={{ margin: 0, fontSize: 18 }}>Share</h2>
          <button className="btn" onClick={onClose} aria-label="Close">
            <Icon name="close" size={16} />
          </button>
        </div>

        {fileUrl && (
          <button className="btn btn-primary" onClick={() => void handleShareFile()} disabled={busy} style={{ width: '100%', justifyContent: 'center', marginTop: 12 }}>
            {busy ? 'Preparing…' : 'Share file'}
          </button>
        )}
        <button className="btn" onClick={() => void handleShareLink()} style={{ width: '100%', justifyContent: 'center', marginTop: 8 }}>
          Share link
        </button>

        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 8, marginTop: 14 }}>
          {SHARE_TARGETS.map((t) => (
            <a key={t.id} className="chip" href={t.buildUrl(link, title)} target="_blank" rel="noopener noreferrer" style={{ justifyContent: 'center' }}>
              {t.label}
            </a>
          ))}
        </div>

        <div style={{ display: 'flex', gap: 8, marginTop: 14 }}>
          <button className="btn" onClick={() => void handleCopy()} style={{ flex: 1, justifyContent: 'center' }}>
            {copied ? 'Copied' : 'Copy link'}
          </button>
          {fileUrl && fileName && (
            <button className="btn" onClick={() => downloadUrl(fileUrl, fileName)} style={{ flex: 1, justifyContent: 'center' }}>
              Download
            </button>
          )}
        </div>
      </div>
    </div>
  )
}
