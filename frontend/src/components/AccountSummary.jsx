import { Link } from 'react-router-dom'
import { SocialLoginButtons } from './SocialLoginButtons.jsx'

const PROVIDER_NAMES = { KAKAO: '카카오', NAVER: '네이버', GOOGLE: '구글' }

/**
 * 휴대폰 인증으로 찾은 기존 계정 요약 + 그 계정으로 로그인하는 방법.
 * account 는 POST /api/account/find-id 의 응답이다. (아이디 찾기, 가입 중 번호 중복 안내에서 같이 쓴다)
 */
export function AccountSummary({ account }) {
  const providers = account.providers.map((p) => PROVIDER_NAMES[p] ?? p)
  return (
    <div className="result-box">
      {account.maskedEmail && (
        <p className="result-main">
          <span className="result-label">아이디(이메일)</span>
          <strong>{account.maskedEmail}</strong>
        </p>
      )}
      {providers.length > 0 && (
        <p className="result-main">
          <span className="result-label">소셜 로그인</span>
          <strong>{providers.join(', ')}</strong>
        </p>
      )}
      <p className="field-hint">가입일 {account.createdAt?.slice(0, 10)}</p>

      {account.hasPassword && (
        <>
          <Link to="/login" className="btn btn-primary btn-block">
            이메일로 로그인
          </Link>
          <p className="find-links">
            비밀번호가 기억나지 않나요? <Link to="/find-password">비밀번호 찾기</Link>
          </p>
        </>
      )}
      {account.providers.length > 0 && (
        <>
          <div className="divider">{providers.join('/')}로 로그인</div>
          <SocialLoginButtons only={account.providers} />
        </>
      )}
    </div>
  )
}
