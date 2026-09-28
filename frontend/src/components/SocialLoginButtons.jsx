// 백엔드가 OAuth2 Authorization Code 흐름을 처리한다. 여기서는 시작 URL 로 페이지 이동만 한다.
// 로그인이 끝나면 백엔드가 리프레시 쿠키를 심고 홈(/)으로 보내며, 앱이 열릴 때 평소처럼 로그인 상태가 복구된다.
const PROVIDERS = [
  { id: 'kakao', label: '카카오로 시작하기' },
  { id: 'naver', label: '네이버로 시작하기' },
  { id: 'google', label: 'Google로 시작하기' },
]

export function SocialLoginButtons() {
  return (
    <div className="social-login">
      {PROVIDERS.map((p) => (
        <a key={p.id} href={`/oauth2/authorization/${p.id}`} className={`btn btn-block btn-social btn-${p.id}`}>
          {p.label}
        </a>
      ))}
    </div>
  )
}
