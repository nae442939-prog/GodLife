// 챌린지 화면 아이콘. 색은 모두 currentColor 라 CSS 로 바꾼다. (장식용이라 스크린리더에서는 숨긴다)

/** 카테고리 id → 아이콘 (1 운동 · 2 공부 · 3 독서 · 4 요리 · 5 기타). 모르는 카테고리는 격자 아이콘. */
export function CategoryIcon({ id, size = 12, strokeWidth = 1.6 }) {
  const common = { width: size, height: size, viewBox: '0 0 24 24', fill: 'none', 'aria-hidden': true }
  if (id === 1) {
    return (
      <svg {...common} stroke="currentColor" strokeWidth={strokeWidth}>
        <rect x="3" y="9" width="3.2" height="6" rx="1" />
        <rect x="17.8" y="9" width="3.2" height="6" rx="1" />
        <path d="M6.2 12h11.6" strokeLinecap="round" />
      </svg>
    )
  }
  if (id === 2) {
    return (
      <svg {...common} stroke="currentColor" strokeWidth={strokeWidth} strokeLinejoin="round">
        <path d="M4 20l1.2-4.5L16 5l3 3L8.2 18.8 4 20z" />
      </svg>
    )
  }
  if (id === 3) {
    return (
      <svg {...common} stroke="currentColor" strokeWidth={strokeWidth} strokeLinejoin="round">
        <path d="M3 5.2c2-1 5-1 7 0v14c-2-1-5-1-7 0V5.2zM21 5.2c-2-1-5-1-7 0v14c2-1 5-1 7 0V5.2z" />
      </svg>
    )
  }
  if (id === 4) {
    // 냄비
    return (
      <svg {...common} stroke="currentColor" strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round">
        <path d="M4 11h16v5a4 4 0 0 1-4 4H8a4 4 0 0 1-4-4v-5z" />
        <path d="M2 11h2M20 11h2M9 7.5c0-1 .8-1.5.8-2.5M14 7.5c0-1 .8-1.5.8-2.5" />
      </svg>
    )
  }
  if (id === 5) {
    // 점 세 개 (그 밖의 습관)
    return (
      <svg {...common} fill="currentColor">
        <circle cx="5.5" cy="12" r="2" />
        <circle cx="12" cy="12" r="2" />
        <circle cx="18.5" cy="12" r="2" />
      </svg>
    )
  }
  return <GridIcon size={size} />
}

export function GridIcon({ size = 12 }) {
  return (
    <svg width={size} height={size} viewBox="0 0 12 12" fill="currentColor" aria-hidden="true">
      <rect x="1" y="1" width="4.2" height="4.2" rx="1" />
      <rect x="6.6" y="1" width="4.2" height="4.2" rx="1" opacity="0.55" />
      <rect x="1" y="6.6" width="4.2" height="4.2" rx="1" opacity="0.55" />
      <rect x="6.6" y="6.6" width="4.2" height="4.2" rx="1" opacity="0.3" />
    </svg>
  )
}

export function SearchIcon() {
  return (
    <svg width="15" height="15" viewBox="0 0 15 15" fill="none" stroke="currentColor" strokeWidth="1.6" aria-hidden="true">
      <circle cx="6.5" cy="6.5" r="5" />
      <path d="M10.2 10.2L13.5 13.5" strokeLinecap="round" />
    </svg>
  )
}

export function CalendarIcon() {
  return (
    <svg width="12" height="12" viewBox="0 0 14 14" fill="none" stroke="currentColor" strokeWidth="1.3" aria-hidden="true">
      <rect x="3" y="3" width="8" height="8" rx="2" />
      <path d="M5 1.5v3M9 1.5v3" strokeLinecap="round" />
    </svg>
  )
}

export function PersonIcon() {
  return (
    <svg width="12" height="12" viewBox="0 0 14 14" fill="none" stroke="currentColor" strokeWidth="1.3" aria-hidden="true">
      <circle cx="7" cy="4.6" r="2.6" />
      <path d="M2 13c0-2.8 2.2-5 5-5s5 2.2 5 5" strokeLinecap="round" />
    </svg>
  )
}

export function PlusIcon() {
  return (
    <svg width="26" height="26" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" aria-hidden="true">
      <path d="M12 2v6M12 16v6M2 12h6M16 12h6" strokeLinecap="round" />
    </svg>
  )
}
