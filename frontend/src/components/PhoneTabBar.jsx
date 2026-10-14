// 폰에서 웹 화면(충전 · 상점 · 설정처럼 앱에 없는 화면)을 볼 때 아래에 붙는 탭.
// 앱 화면(/app/, Flutter)의 아래 탭과 같은 순서 · 모양이라, 웹 화면으로 넘어와도 탭이 그대로 있는 것처럼 보인다.
// 누르면 앱 화면의 그 탭으로 간다 (화면을 통째로 옮기므로 Link 가 아니라 a 를 쓴다).

const TABS = [
  { tab: 'challenges', label: '챌린지', icon: 'M5 21V4h9l1 2h5v9h-6l-1-2H7v8z' },
  { tab: 'community', label: '커뮤니티', icon: 'M4 4h12v9H8l-4 3zM18 8h2v12l-3-3H9v-2h9z' },
  { tab: 'home', label: '홈', icon: 'M4 11l8-7 8 7v9h-5v-6H9v6H4z' },
  { tab: 'records', label: '갓생기록', icon: 'M5 5h14v15H5zM5 9h14M9 3v4M15 3v4M8 13h2M12 13h2M8 16h2' },
  { tab: 'my', label: '마이페이지', icon: 'M12 12a4 4 0 100-8 4 4 0 000 8zM5 20c0-3.5 3-5 7-5s7 1.5 7 5z' },
]

/**
 * 폰에서 웹 화면 맨 위에 붙는 [‹ 뒤로]: 앱에서 넘어왔으면 앱으로 돌아가고,
 * 처음부터 이 주소로 들어왔으면(돌아갈 곳이 없으면) 앱의 마이페이지로 간다.
 */
export function PhoneBackBar() {
  function goBack() {
    const fromHere = document.referrer.startsWith(window.location.origin)
    if (fromHere && window.history.length > 1) window.history.back()
    else window.location.href = '/app/?tab=my'
  }
  return (
    <div className="phone-back">
      <button type="button" onClick={goBack} aria-label="뒤로 가기">
        <svg viewBox="0 0 24 24" width="20" height="20" aria-hidden="true">
          <path d="M15 5l-7 7 7 7" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
        뒤로
      </button>
    </div>
  )
}

export function PhoneTabBar() {
  return (
    <nav className="phone-tabs" aria-label="앱 메뉴">
      {TABS.map((t) => (
        <a key={t.tab} href={`/app/?tab=${t.tab}`} className="phone-tab">
          <svg viewBox="0 0 24 24" width="24" height="24" aria-hidden="true">
            <path d={t.icon} fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinejoin="round" />
          </svg>
          <span>{t.label}</span>
        </a>
      ))}
    </nav>
  )
}
