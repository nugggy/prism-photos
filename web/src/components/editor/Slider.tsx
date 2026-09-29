export interface SliderProps {
  label: string
  value: number
  min: number
  max: number
  step?: number
  defaultValue?: number
  onChange: (v: number) => void
  onCommit?: (v: number) => void
  suffix?: string
}

/** A labelled range input with a live value readout and a reset-to-default button. */
export function Slider({ label, value, min, max, step = 1, defaultValue = 0, onChange, onCommit, suffix = '' }: SliderProps): React.ReactElement {
  const isDefault = value === defaultValue
  return (
    <div className="editor-slider-row">
      <div className="row-head">
        <span className="muted">{label}</span>
        <span style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
          <span className="muted">
            {value}
            {suffix}
          </span>
          <button
            type="button"
            className="editor-reset-btn"
            aria-label={`Reset ${label.toLowerCase()}`}
            disabled={isDefault}
            onClick={() => {
              onChange(defaultValue)
              onCommit?.(defaultValue)
            }}
          >
            Reset
          </button>
        </span>
      </div>
      <input
        type="range"
        aria-label={label}
        min={min}
        max={max}
        step={step}
        value={value}
        onChange={(e) => onChange(Number(e.target.value))}
        onPointerUp={(e) => onCommit?.(Number((e.target as HTMLInputElement).value))}
        onKeyUp={(e) => onCommit?.(Number((e.target as HTMLInputElement).value))}
      />
    </div>
  )
}
