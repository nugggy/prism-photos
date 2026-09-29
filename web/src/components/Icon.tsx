const PATHS: Record<string, string> = {
  photos: 'M4 5h16v14H4zM4 15l4-4 3 3 5-6 4 5',
  albums: 'M4 6h6l2 2h8v10H4z',
  favourites: 'M12 21s-7-4.35-9.5-8.8C.8 8.7 2.7 5.5 6 5.5c1.9 0 3.4 1 4 2.4.6-1.4 2.1-2.4 4-2.4 3.3 0 5.2 3.2 3.5 6.7C19 16.65 12 21 12 21z',
  search: 'M11 4a7 7 0 105.29 12.29l4.7 4.7 1.42-1.41-4.7-4.7A7 7 0 0011 4zm0 2a5 5 0 110 10 5 5 0 010-10z',
  locked: 'M6 10V8a6 6 0 1112 0v2h1a1 1 0 011 1v9a1 1 0 01-1 1H5a1 1 0 01-1-1v-9a1 1 0 011-1zm2 0h8V8a4 4 0 10-8 0z',
  settings: 'M12 8a4 4 0 100 8 4 4 0 000-8zm9.4 4a7.4 7.4 0 01-.1 1.2l2.1 1.6-2 3.5-2.5-1a7.6 7.6 0 01-2 1.2l-.4 2.6h-4l-.4-2.6a7.6 7.6 0 01-2-1.2l-2.5 1-2-3.5 2.1-1.6a7.4 7.4 0 010-2.4L2.6 9.4l2-3.5 2.5 1a7.6 7.6 0 012-1.2L9.5 3h4l.4 2.6a7.6 7.6 0 012 1.2l2.5-1 2 3.5-2.1 1.6c.07.4.1.8.1 1.1z',
  close: 'M6 6l12 12M18 6L6 18',
  check: 'M5 13l4 4L19 7',
  heart: 'M12 21s-7-4.35-9.5-8.8C.8 8.7 2.7 5.5 6 5.5c1.9 0 3.4 1 4 2.4.6-1.4 2.1-2.4 4-2.4 3.3 0 5.2 3.2 3.5 6.7C19 16.65 12 21 12 21z',
  heartFilled: 'M12 21s-7-4.35-9.5-8.8C.8 8.7 2.7 5.5 6 5.5c1.9 0 3.4 1 4 2.4.6-1.4 2.1-2.4 4-2.4 3.3 0 5.2 3.2 3.5 6.7C19 16.65 12 21 12 21z',
  lock: 'M6 10V8a6 6 0 1112 0v2h1a1 1 0 011 1v9a1 1 0 01-1 1H5a1 1 0 01-1-1v-9a1 1 0 011-1zm2 0h8V8a4 4 0 10-8 0z',
  unlock: 'M6 10V8a6 6 0 0111.3-2.7l-1.8 1a4 4 0 00-7.5 1.7v2h9a1 1 0 011 1v9a1 1 0 01-1 1H5a1 1 0 01-1-1v-9a1 1 0 011-1z',
  share: 'M18 8a3 3 0 10-2.83-4H15a3 3 0 000 6h.02l-6.1 3.66a3 3 0 100 2.68l6.1 3.66A3 3 0 1018 17a2.98 2.98 0 00-.54.05l-6.1-3.66a3.1 3.1 0 000-.78l6.1-3.66c.17.03.35.05.54.05z',
  download: 'M12 3v12m0 0l-4-4m4 4l4-4M4 19h16',
  trash: 'M6 7h12l-1 14H7L6 7zm3-3h6l1 2H8l1-2zM10 10v8m4-8v8',
  edit: 'M4 20h4l11-11-4-4L4 16v4zM14 5l4 4',
  play: 'M8 5v14l11-7z',
  pause: 'M7 5h4v14H7zm6 0h4v14h-4z',
  arrowLeft: 'M20 12H4m0 0l6-6m-6 6l6 6',
  arrowRight: 'M4 12h16m0 0l-6-6m6 6l-6 6',
  chevronRight: 'M9 6l6 6-6 6',
  chevronLeft: 'M15 6l-6 6 6 6',
  info: 'M12 8h.01M11 12h1v6h1M12 22a10 10 0 100-20 10 10 0 000 20z',
  sun: 'M12 4V2m0 20v-2M4 12H2m20 0h-2M6.3 6.3L4.9 4.9m14.2 1.4l1.4-1.4M6.3 17.7l-1.4 1.4m14.2-1.4l1.4 1.4M12 8a4 4 0 100 8 4 4 0 000-8z',
  moon: 'M20 14.5A8.5 8.5 0 0110.5 5a1 1 0 00-1.2-1A9 9 0 1021 15.7a1 1 0 00-1-1.2z',
  fullscreen: 'M4 9V4h5M20 9V4h-5M4 15v5h5m11-5v5h-5',
  crop: 'M6 2v14a2 2 0 002 2h14M2 6h14a2 2 0 012 2v14',
  rotate: 'M3 12a9 9 0 1015-6.7M3 12V5m0 7h7',
  flip: 'M12 3v18M6 7l6-4 6 4M6 17l6 4 6-4',
  undo: 'M9 14l-4-4 4-4M5 10h9a5 5 0 015 5v1',
  fingerprint: 'M12 2a5 5 0 015 5v2M7 7V5a5 5 0 019.9-1M4 9v2a8 8 0 0016 0V9M8 21a12 12 0 010-14M16 21a12 12 0 000-14M12 12v4',
  cameraStop: 'M4 6h4l1-2h6l1 2h4v13H4z',
  plus: 'M12 5v14M5 12h14',
  more: 'M12 6a1.5 1.5 0 110-3 1.5 1.5 0 010 3zm0 7.5a1.5 1.5 0 110-3 1.5 1.5 0 010 3zM12 21a1.5 1.5 0 110-3 1.5 1.5 0 010 3z',
}

export interface IconProps {
  name: keyof typeof PATHS
  size?: number
  filled?: boolean
  className?: string
}

export function Icon({ name, size = 20, filled = false, className }: IconProps): React.ReactElement {
  const isHeart = name === 'heartFilled'
  return (
    <svg
      className={`tab-icon ${className ?? ''}`}
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill={filled || isHeart ? 'currentColor' : 'none'}
      stroke="currentColor"
      strokeWidth={filled || isHeart ? 0 : 1.8}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d={PATHS[name]} />
    </svg>
  )
}
