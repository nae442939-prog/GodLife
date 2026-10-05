// 기기 지문: 이 브라우저 · 기기에서 잘 바뀌지 않는 값(화면 · 시간대 · 언어 · 캔버스 그림 등)을 모아 해시한 값.
// 가입 · 로그인 · 토큰 재발급 요청에만 붙여 보내고, 서버는 같은 기기로 여러 계정을 만드는지 볼 때만 쓴다.
// 개인을 알아볼 수 있는 값(이름 · 위치 등)은 넣지 않고, 원문이 아니라 해시만 보낸다.

function canvasSignal() {
  try {
    const canvas = document.createElement('canvas')
    canvas.width = 220
    canvas.height = 40
    const ctx = canvas.getContext('2d')
    ctx.textBaseline = 'top'
    ctx.font = "16px 'Arial'"
    ctx.fillStyle = '#f60'
    ctx.fillRect(100, 2, 60, 22)
    ctx.fillStyle = '#069'
    ctx.fillText('갓생살기 GodLife ✓', 2, 12)
    ctx.fillStyle = 'rgba(102, 204, 0, 0.7)'
    ctx.fillText('갓생살기 GodLife ✓', 4, 14)
    return canvas.toDataURL()
  } catch {
    return ''
  }
}

function signals() {
  const nav = navigator
  return [
    nav.userAgent,
    nav.language,
    (nav.languages ?? []).join(','),
    nav.platform,
    nav.hardwareConcurrency,
    nav.deviceMemory,
    nav.maxTouchPoints,
    screen.width,
    screen.height,
    screen.colorDepth,
    window.devicePixelRatio,
    Intl.DateTimeFormat().resolvedOptions().timeZone,
    canvasSignal(),
  ].join('|')
}

async function sha256Hex(text) {
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(text))
  return [...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, '0')).join('')
}

let cached = null

/** 기기 지문(16진수 64자). 만들 수 없는 환경이면 null — 그래도 가입 · 로그인은 그대로 된다. */
export function deviceFingerprint() {
  if (!cached) {
    cached = sha256Hex(signals()).catch(() => null)
  }
  return cached
}
