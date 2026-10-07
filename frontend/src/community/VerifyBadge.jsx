import { shortDate } from '../challenge/format.js'

/**
 * 글에 붙은 인증 결과. 글쓴이가 적은 것이 아니라 글을 쓸 때 서버가 인증 기록을 보고 붙인 값이다.
 * compact = 목록용 (한 줄), 아니면 상세용 (챌린지 제목 + 날짜)
 */
export function VerifyBadge({ verify, compact = false }) {
  if (!verify) return null
  const state = verify.success ? 'is-success' : 'is-fail'
  if (compact) {
    return (
      <span className={`cm-verify is-compact ${state}`}>
        {verify.success ? '인증 완료' : '인증 전'} · {verify.challengeTitle}
      </span>
    )
  }
  return (
    <div className={`cm-verify ${state}`}>
      <span className="cm-verify-mark" aria-hidden="true">
        {verify.success ? '✓' : '✕'}
      </span>
      <div>
        <strong>{verify.challengeTitle}</strong>
        <span>
          {shortDate(verify.date)} · {verify.success ? '이날 인증을 완료했어요' : '글을 쓸 때는 아직 인증 전이었어요'}
        </span>
      </div>
      <small>서버가 확인한 인증 기록</small>
    </div>
  )
}
