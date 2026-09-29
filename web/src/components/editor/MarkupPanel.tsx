import { MARKUP_COLORS, STICKER_EMOJIS, type MarkupElement, type MarkupTool } from '../../lib/markup'
import { Icon } from '../Icon'
import { Slider } from './Slider'

export interface MarkupPanelProps {
  tool: MarkupTool
  onToolChange: (tool: MarkupTool) => void
  color: string
  onColorChange: (color: string) => void
  brushSize: number
  onBrushSizeChange: (size: number) => void
  textColor: string
  onTextColorChange: (color: string) => void
  textBackground: boolean
  onTextBackgroundChange: (on: boolean) => void
  textSize: number
  onTextSizeChange: (size: number) => void
  stickerEmoji: string
  onStickerEmojiChange: (emoji: string) => void
  stickerSize: number
  onStickerSizeChange: (size: number) => void
  selectedElement: MarkupElement | null
  onUpdateSelected: (patch: { rotation?: number; scale?: number }) => void
  onDeleteSelected: () => void
  onClearAll: () => void
  hasElements: boolean
}

const TOOLS: { id: MarkupTool; label: string; icon: 'pen' | 'highlighter' | 'arrowTool' | 'rectangleTool' | 'ellipseTool' | 'textTool' | 'sticker' | 'blurTool' | 'eraser' }[] = [
  { id: 'pen', label: 'Pen', icon: 'pen' },
  { id: 'highlighter', label: 'Highlight', icon: 'highlighter' },
  { id: 'arrow', label: 'Arrow', icon: 'arrowTool' },
  { id: 'rectangle', label: 'Rectangle', icon: 'rectangleTool' },
  { id: 'ellipse', label: 'Ellipse', icon: 'ellipseTool' },
  { id: 'text', label: 'Text', icon: 'textTool' },
  { id: 'sticker', label: 'Sticker', icon: 'sticker' },
  { id: 'blur', label: 'Blur', icon: 'blurTool' },
  { id: 'pixelate', label: 'Pixelate', icon: 'blurTool' },
  { id: 'eraser', label: 'Eraser', icon: 'eraser' },
]

export function MarkupPanel(props: MarkupPanelProps): React.ReactElement {
  const {
    tool,
    onToolChange,
    color,
    onColorChange,
    brushSize,
    onBrushSizeChange,
    textColor,
    onTextColorChange,
    textBackground,
    onTextBackgroundChange,
    textSize,
    onTextSizeChange,
    stickerEmoji,
    onStickerEmojiChange,
    stickerSize,
    onStickerSizeChange,
    selectedElement,
    onUpdateSelected,
    onDeleteSelected,
    onClearAll,
    hasElements,
  } = props

  const showColor = tool === 'pen' || tool === 'highlighter' || tool === 'arrow' || tool === 'rectangle' || tool === 'ellipse'
  const showBrushSize = showColor || tool === 'eraser' || tool === 'blur' || tool === 'pixelate'

  return (
    <div>
      <h3 style={{ fontSize: 13, margin: '0 0 8px' }}>Tool</h3>
      <div className="tool-grid">
        {TOOLS.map((t) => (
          <button key={t.id} type="button" className={`tool-btn${tool === t.id ? ' active' : ''}`} onClick={() => onToolChange(t.id)} aria-pressed={tool === t.id} aria-label={t.label}>
            <Icon name={t.icon} size={18} />
            <span>{t.label}</span>
          </button>
        ))}
      </div>

      {showColor && (
        <>
          <h3 style={{ fontSize: 13, margin: '0 0 8px' }}>Colour</h3>
          <div className="color-swatch-row">
            {MARKUP_COLORS.map((c) => (
              <button key={c} type="button" className={`color-swatch${color === c ? ' active' : ''}`} style={{ background: c }} aria-label={`Colour ${c}`} onClick={() => onColorChange(c)} />
            ))}
          </div>
        </>
      )}

      {showBrushSize && <Slider label="Brush size" value={Math.round(brushSize * 1000)} min={4} max={120} defaultValue={20} onChange={(v) => onBrushSizeChange(v / 1000)} />}

      {tool === 'text' && (
        <>
          <h3 style={{ fontSize: 13, margin: '12px 0 8px' }}>Text style</h3>
          <div className="color-swatch-row">
            {MARKUP_COLORS.map((c) => (
              <button key={c} type="button" className={`color-swatch${textColor === c ? ' active' : ''}`} style={{ background: c }} aria-label={`Text colour ${c}`} onClick={() => onTextColorChange(c)} />
            ))}
          </div>
          <label style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 13, marginBottom: 8 }}>
            <input type="checkbox" checked={textBackground} onChange={(e) => onTextBackgroundChange(e.target.checked)} />
            Background pill
          </label>
          <Slider label="Text size" value={Math.round(textSize * 1000)} min={20} max={150} defaultValue={60} onChange={(v) => onTextSizeChange(v / 1000)} />
          <p className="muted" style={{ fontSize: 11 }}>
            Tap the canvas to place text. Drag a placed label to move it.
          </p>
        </>
      )}

      {tool === 'sticker' && (
        <>
          <h3 style={{ fontSize: 13, margin: '12px 0 8px' }}>Sticker</h3>
          <div className="sticker-grid">
            {STICKER_EMOJIS.map((emoji) => (
              <button key={emoji} type="button" className={`sticker-btn${stickerEmoji === emoji ? ' active' : ''}`} onClick={() => onStickerEmojiChange(emoji)} aria-label={`Sticker ${emoji}`}>
                {emoji}
              </button>
            ))}
          </div>
          <Slider label="Sticker size" value={Math.round(stickerSize * 1000)} min={30} max={200} defaultValue={80} onChange={(v) => onStickerSizeChange(v / 1000)} />
          <p className="muted" style={{ fontSize: 11 }}>
            Tap the canvas to place a sticker. Drag a placed sticker to move it.
          </p>
        </>
      )}

      {selectedElement && (selectedElement.type === 'text' || selectedElement.type === 'sticker') && (
        <div style={{ marginTop: 14, padding: 10, background: 'var(--surface-2)', borderRadius: 8 }}>
          <h3 style={{ fontSize: 13, margin: '0 0 8px' }}>Selected {selectedElement.type}</h3>
          <Slider
            label="Rotation"
            value={selectedElement.rotation}
            min={-180}
            max={180}
            defaultValue={0}
            suffix="°"
            onChange={(v) => onUpdateSelected({ rotation: v })}
          />
          {selectedElement.type === 'text' && (
            <Slider label="Scale" value={Math.round(selectedElement.scale * 100)} min={50} max={300} defaultValue={100} suffix="%" onChange={(v) => onUpdateSelected({ scale: v / 100 })} />
          )}
          <button type="button" className="btn btn-danger" style={{ width: '100%', justifyContent: 'center' }} onClick={onDeleteSelected}>
            Delete
          </button>
        </div>
      )}

      {hasElements && (
        <button type="button" className="btn" style={{ width: '100%', marginTop: 14, justifyContent: 'center' }} onClick={onClearAll}>
          Clear all markup
        </button>
      )}
    </div>
  )
}
