// 백엔드가 OAuth2 Authorization Code 흐름을 처리한다. 여기서는 시작 URL 로 페이지 이동만 한다.
// 로그인이 끝나면 백엔드가 리프레시 쿠키를 심고 홈(/)으로 보내며, 앱이 열릴 때 평소처럼 로그인 상태가 복구된다.
const PROVIDERS = [
  { id: 'kakao', label: '카카오로 로그인', Logo: KakaoLogo },
  { id: 'naver', label: '네이버로 로그인', Logo: NaverLogo },
  { id: 'google', label: 'Google로 로그인', Logo: GoogleLogo },
]

/** only: 보여줄 제공자만 고른다 (예: ['GOOGLE']). 없으면 전부. */
export function SocialLoginButtons({ only }) {
  const list = only ? PROVIDERS.filter((p) => only.includes(p.id.toUpperCase())) : PROVIDERS
  return (
    <ul className="social-icons">
      {list.map(({ id, label, Logo }) => (
        <li key={id}>
          <a href={`/oauth2/authorization/${id}`} className={`social-icon social-${id}`} aria-label={label} title={label}>
            <Logo />
          </a>
        </li>
      ))}
    </ul>
  )
}

function KakaoLogo() {
  return (
    <svg viewBox="0 0 24 24" width="26" height="26" aria-hidden="true">
      <path
        fill="#191919"
        d="M12 4C7.03 4 3 7.13 3 11c0 2.49 1.66 4.67 4.16 5.9l-.9 3.3c-.08.3.25.54.51.37l3.94-2.6c.42.04.85.06 1.29.06 4.97 0 9-3.13 9-7s-4.03-7-9-7z"
      />
    </svg>
  )
}

function NaverLogo() {
  return (
    <svg viewBox="0 0 24 24" width="20" height="20" aria-hidden="true">
      <path fill="#ffffff" d="M15.2 12.6 8.5 3H3v18h5.8v-9.6L15.5 21H21V3h-5.8z" />
    </svg>
  )
}

function GoogleLogo() {
  return (
    <svg viewBox="0 0 18 18" width="22" height="22" aria-hidden="true">
      <path
        fill="#EA4335"
        d="M9 3.48c1.69 0 2.83.73 3.48 1.34l2.54-2.48C13.46.89 11.43 0 9 0 5.48 0 2.44 2.02.96 4.96l2.91 2.26C4.6 5.05 6.62 3.48 9 3.48z"
      />
      <path
        fill="#4285F4"
        d="M17.64 9.2c0-.74-.06-1.28-.19-1.84H9v3.34h4.96c-.1.83-.64 2.08-1.84 2.92l2.84 2.2c1.7-1.57 2.68-3.88 2.68-6.62z"
      />
      <path
        fill="#FBBC05"
        d="M3.88 10.78A5.54 5.54 0 0 1 3.58 9c0-.62.11-1.22.29-1.78L.96 4.96A9.008 9.008 0 0 0 0 9c0 1.45.35 2.82.96 4.04l2.92-2.26z"
      />
      <path
        fill="#34A853"
        d="M9 18c2.43 0 4.47-.8 5.96-2.18l-2.84-2.2c-.76.53-1.78.9-3.12.9-2.38 0-4.4-1.57-5.12-3.74L.97 13.04C2.45 15.98 5.48 18 9 18z"
      />
    </svg>
  )
}
