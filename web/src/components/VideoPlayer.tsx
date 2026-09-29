import Hls from 'hls.js'
import { useCallback, useEffect, useRef, useState } from 'react'
import type { MediaItem } from '../plex/model'
import type { ServerRef } from '../plex/urls'
import { buildDirectPlayUrl, buildHlsTranscodeStopUrl, buildHlsTranscodeUrl } from '../plex/urls'
import { getClientIdentifier } from '../plex/headers'
import { formatDuration } from '../lib/format'
import { Icon } from './Icon'
import type { TranscodePreference } from '../state/settingsStore'

const POSITION_KEY_PREFIX = 'prism.videoPosition.'

function loadPosition(id: string): number {
  try {
    const raw = localStorage.getItem(POSITION_KEY_PREFIX + id)
    return raw ? Number(raw) : 0
  } catch {
    return 0
  }
}
function savePosition(id: string, seconds: number): void {
  try {
    localStorage.setItem(POSITION_KEY_PREFIX + id, String(seconds))
  } catch {
    /* ignore */
  }
}

export interface VideoPlayerProps {
  item: MediaItem
  server: ServerRef
  transcodePreference: TranscodePreference
  autoPlay?: boolean
}

export function VideoPlayer({ item, server, transcodePreference, autoPlay }: VideoPlayerProps): React.ReactElement {
  const videoRef = useRef<HTMLVideoElement>(null)
  const hlsRef = useRef<Hls | null>(null)
  const sessionRef = useRef<string>(`prism-${Math.random().toString(36).slice(2)}`)

  const [playing, setPlaying] = useState(false)
  const [currentTime, setCurrentTime] = useState(0)
  const [duration, setDuration] = useState(item.durationMs / 1000)
  const [buffered, setBuffered] = useState(0)
  const [scrubbing, setScrubbing] = useState(false)
  const [scrubTime, setScrubTime] = useState(0)
  const [speed, setSpeed] = useState(1)
  const [muted, setMuted] = useState(false)
  const [volume, setVolume] = useState(1)
  const [loop, setLoop] = useState(false)
  const [usingHls, setUsingHls] = useState(transcodePreference === 'hls')
  const [showSpeedMenu, setShowSpeedMenu] = useState(false)

  const stopTranscode = useCallback(() => {
    void fetch(buildHlsTranscodeStopUrl(server, sessionRef.current)).catch(() => {})
  }, [server])

  const startDirect = useCallback(
    (video: HTMLVideoElement) => {
      hlsRef.current?.destroy()
      hlsRef.current = null
      video.src = buildDirectPlayUrl(server, item.partKey)
    },
    [server, item.partKey],
  )

  const startHls = useCallback(
    (video: HTMLVideoElement) => {
      const url = buildHlsTranscodeUrl(server, {
        ratingKey: item.id,
        session: sessionRef.current,
        clientIdentifier: getClientIdentifier(),
      })
      if (Hls.isSupported()) {
        const hls = new Hls()
        hlsRef.current = hls
        hls.loadSource(url)
        hls.attachMedia(video)
      } else {
        video.src = url
      }
    },
    [server, item.id],
  )

  useEffect(() => {
    const video = videoRef.current
    if (!video) return

    if (transcodePreference === 'hls') {
      startHls(video)
      setUsingHls(true)
    } else {
      startDirect(video)
      video.onerror = () => {
        startHls(video)
        setUsingHls(true)
      }
    }

    video.currentTime = loadPosition(item.id)

    return () => {
      hlsRef.current?.destroy()
      hlsRef.current = null
      stopTranscode()
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [item.id, item.partKey, server.baseUrl])

  const toggleTranscodeMode = useCallback(() => {
    const video = videoRef.current
    if (!video) return
    const time = video.currentTime
    stopTranscode()
    if (usingHls) {
      startDirect(video)
      setUsingHls(false)
    } else {
      startHls(video)
      setUsingHls(true)
    }
    const restore = () => {
      video.currentTime = time
      video.removeEventListener('loadedmetadata', restore)
    }
    video.addEventListener('loadedmetadata', restore)
  }, [usingHls, startDirect, startHls, stopTranscode])

  useEffect(() => {
    const video = videoRef.current
    if (!video) return
    const onTime = () => {
      setCurrentTime(video.currentTime)
      savePosition(item.id, video.currentTime)
      if (video.buffered.length > 0) setBuffered(video.buffered.end(video.buffered.length - 1))
    }
    const onLoaded = () => setDuration(video.duration || duration)
    const onPlay = () => setPlaying(true)
    const onPause = () => setPlaying(false)
    video.addEventListener('timeupdate', onTime)
    video.addEventListener('loadedmetadata', onLoaded)
    video.addEventListener('play', onPlay)
    video.addEventListener('pause', onPause)
    return () => {
      video.removeEventListener('timeupdate', onTime)
      video.removeEventListener('loadedmetadata', onLoaded)
      video.removeEventListener('play', onPlay)
      video.removeEventListener('pause', onPause)
    }
  }, [item.id, duration])

  const togglePlay = useCallback(() => {
    const video = videoRef.current
    if (!video) return
    if (video.paused) void video.play()
    else video.pause()
  }, [])

  const seekBy = useCallback((deltaSeconds: number) => {
    const video = videoRef.current
    if (!video) return
    video.currentTime = Math.max(0, Math.min(duration, video.currentTime + deltaSeconds))
  }, [duration])

  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      const target = e.target as HTMLElement
      if (target.tagName === 'INPUT') return
      if (e.key === ' ' || e.key === 'k' || e.key === 'K') {
        togglePlay()
        e.preventDefault()
      } else if (e.key === 'j' || e.key === 'J' || e.key === 'ArrowLeft') {
        seekBy(-10)
      } else if (e.key === 'l' || e.key === 'L' || e.key === 'ArrowRight') {
        seekBy(10)
      }
    }
    window.addEventListener('keydown', handler)
    return () => window.removeEventListener('keydown', handler)
  }, [togglePlay, seekBy])

  useEffect(() => {
    if (autoPlay) void videoRef.current?.play().catch(() => {})
  }, [autoPlay])

  const containerRef = useRef<HTMLDivElement>(null)
  const toggleFullscreen = () => {
    if (document.fullscreenElement) void document.exitFullscreen()
    else void containerRef.current?.requestFullscreen()
  }
  const togglePip = async () => {
    const video = videoRef.current
    if (!video) return
    try {
      if (document.pictureInPictureElement) await document.exitPictureInPicture()
      else await video.requestPictureInPicture()
    } catch {
      /* PiP unsupported */
    }
  }

  const handleDoubleClickSide = (side: 'left' | 'right') => {
    seekBy(side === 'left' ? -10 : 10)
  }

  const progress = duration > 0 ? (scrubbing ? scrubTime : currentTime) / duration : 0

  return (
    <div ref={containerRef} style={{ position: 'relative', width: '100%', height: '100%', background: '#000' }}>
      <video
        ref={videoRef}
        style={{ width: '100%', height: '100%', objectFit: 'contain' }}
        muted={muted}
        loop={loop}
        playsInline
        onClick={togglePlay}
      />
      <div style={{ position: 'absolute', inset: 0, display: 'flex' }}>
        <div style={{ flex: 1 }} onDoubleClick={() => handleDoubleClickSide('left')} />
        <div style={{ flex: 1 }} onDoubleClick={() => handleDoubleClickSide('right')} />
      </div>

      <div
        style={{
          position: 'absolute',
          left: 0,
          right: 0,
          bottom: 0,
          padding: '8px 12px 12px',
          background: 'linear-gradient(transparent, rgba(0,0,0,0.75))',
          color: '#fff',
        }}
      >
        <input
          type="range"
          min={0}
          max={duration || 0}
          step={0.1}
          value={scrubbing ? scrubTime : currentTime}
          onChange={(e) => {
            setScrubbing(true)
            setScrubTime(Number(e.target.value))
          }}
          onMouseUp={() => {
            if (videoRef.current) videoRef.current.currentTime = scrubTime
            setScrubbing(false)
          }}
          onTouchEnd={() => {
            if (videoRef.current) videoRef.current.currentTime = scrubTime
            setScrubbing(false)
          }}
          style={{ width: '100%', accentColor: '#e5a00d' }}
          aria-label="Seek"
        />
        <div style={{ display: 'flex', alignItems: 'center', gap: 10, fontSize: 12, flexWrap: 'wrap' }}>
          <button className="btn" onClick={togglePlay} aria-label={playing ? 'Pause' : 'Play'}>
            <Icon name={playing ? 'pause' : 'play'} size={16} />
          </button>
          <button className="btn" onClick={() => seekBy(-10)} aria-label="Back 10 seconds">
            -10s
          </button>
          <button className="btn" onClick={() => seekBy(10)} aria-label="Forward 10 seconds">
            +10s
          </button>
          <span>
            {formatDuration((scrubbing ? scrubTime : currentTime) * 1000)} / {formatDuration(duration * 1000)}
          </span>
          <span style={{ opacity: 0.6 }}>{Math.round(progress * 100)}% · buffered {formatDuration(buffered * 1000)}</span>
          <div style={{ position: 'relative' }}>
            <button className="btn" onClick={() => setShowSpeedMenu((s) => !s)}>
              {speed}x
            </button>
            {showSpeedMenu && (
              <div className="card" style={{ position: 'absolute', bottom: '110%', left: 0, display: 'flex', flexDirection: 'column', gap: 4 }}>
                {[0.5, 0.75, 1, 1.25, 1.5, 2].map((s) => (
                  <button
                    key={s}
                    className="btn"
                    onClick={() => {
                      setSpeed(s)
                      if (videoRef.current) videoRef.current.playbackRate = s
                      setShowSpeedMenu(false)
                    }}
                  >
                    {s}x
                  </button>
                ))}
              </div>
            )}
          </div>
          <button
            className="btn"
            onClick={() => {
              setMuted((m) => !m)
            }}
            aria-label="Mute"
          >
            {muted ? 'Muted' : 'Sound'}
          </button>
          <input
            type="range"
            min={0}
            max={1}
            step={0.05}
            value={volume}
            onChange={(e) => {
              const v = Number(e.target.value)
              setVolume(v)
              if (videoRef.current) videoRef.current.volume = v
            }}
            aria-label="Volume"
          />
          <button className={`btn${loop ? ' btn-primary' : ''}`} onClick={() => setLoop((l) => !l)} aria-label="Loop">
            Loop
          </button>
          <button className="btn" onClick={() => void togglePip()} aria-label="Picture in picture">
            PiP
          </button>
          <button className={`btn${usingHls ? ' btn-primary' : ''}`} onClick={toggleTranscodeMode} aria-label="Toggle transcode">
            {usingHls ? 'Transcoding' : 'Direct play'}
          </button>
          <button className="btn" onClick={toggleFullscreen} aria-label="Fullscreen">
            <Icon name="fullscreen" size={16} />
          </button>
        </div>
      </div>
    </div>
  )
}
