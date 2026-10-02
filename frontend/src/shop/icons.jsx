// 포인트 상점 화면이 같이 쓰는 아이콘 (사용자 시안의 그림)

export function CartIcon({ size = 15 }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" aria-hidden="true">
      <path
        d="M3 4h2l2.4 12.2A2 2 0 0 0 9.4 18H18a2 2 0 0 0 2-1.6L21.5 8H6"
        stroke="currentColor"
        strokeWidth="1.8"
        fill="none"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <circle cx="10" cy="21" r="1.4" fill="currentColor" />
      <circle cx="18" cy="21" r="1.4" fill="currentColor" />
    </svg>
  )
}

/** 쿠폰 상품(이용권 · 상품권)의 기본 그림: 티켓 */
export function TicketIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" aria-hidden="true">
      <path
        d="M3 9a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2v1.5a1.5 1.5 0 0 0 0 3V16a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-2.5a1.5 1.5 0 0 0 0-3V9z"
        strokeWidth="1.6"
        strokeLinejoin="round"
      />
      <path d="M10 7v10" strokeWidth="1.6" strokeDasharray="2 2" />
    </svg>
  )
}

/** 실물 상품의 기본 그림: 상자 */
export function BoxIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" aria-hidden="true">
      <path d="M3 9l9-5 9 5-9 5-9-5z" strokeWidth="1.6" strokeLinejoin="round" />
      <path d="M3 9v7l9 5 9-5V9" strokeWidth="1.6" strokeLinejoin="round" />
      <path d="M12 14v7" strokeWidth="1.6" />
    </svg>
  )
}

export function CloseIcon({ size = 13 }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" aria-hidden="true">
      <path d="M5 5l14 14M19 5L5 19" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
    </svg>
  )
}

export function BackIcon({ size = 12 }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" aria-hidden="true">
      <path d="M15 5l-7 7 7 7" stroke="currentColor" strokeWidth="2" fill="none" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  )
}
