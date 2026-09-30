import { useEffect, useState } from 'react'
import { settlementApi } from '../api/client.js'
import { addDays, toIsoDate } from './format.js'

const p = (n) => `${n.toLocaleString()}P`

// X 를 누르지 않으면 이 시간 뒤 저절로 닫힌다
const INTRO_MS = 10_000

/**
 * 인증 화면에 들어오면 가장 먼저 가운데에 뜨는 어제(주 N회는 지난주) 결과.
 * X 를 누르면 닫히고, 누르지 않으면 10초 뒤 저절로 닫힌다. 끝난 기간이 아직 없으면 아무것도 띄우지 않는다.
 */
export function SettlementIntro({ challengeId }) {
  const [summary, setSummary] = useState(null)
  const [closed, setClosed] = useState(false)

  useEffect(() => {
    let cancelled = false
    settlementApi
      .latest(challengeId)
      .then((s) => !cancelled && setSummary(s))
      .catch(() => {}) // 보조 정보라 실패해도 화면은 그대로
    return () => {
      cancelled = true
    }
  }, [challengeId])

  useEffect(() => {
    if (!summary) return
    const timer = setTimeout(() => setClosed(true), INTRO_MS)
    return () => clearTimeout(timer)
  }, [summary])

  useEffect(() => {
    if (!summary || closed) return
    function onKeyDown(e) {
      if (e.key === 'Escape') setClosed(true)
    }
    document.addEventListener('keydown', onKeyDown)
    return () => document.removeEventListener('keydown', onKeyDown)
  }, [summary, closed])

  if (!summary || closed) return null
  return (
    <div className="vc-intro" role="dialog" aria-modal="true" aria-label="지난 결과">
      <div className="vc-intro-box">
        <button type="button" className="vc-intro-close" onClick={() => setClosed(true)} aria-label="닫기" autoFocus>
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" aria-hidden="true">
            <path d="M6 6l12 12M18 6L6 18" strokeWidth="2" strokeLinecap="round" />
          </svg>
        </button>
        <SettlementCard summary={summary} />
      </div>
    </div>
  )
}

/**
 * 결과 카드. 아무도 실패하지 않았으면 응원 멘트, 포인트 챌린지면 실패분을 누가 얼마씩 나누는지와 내 환급·보상(예정).
 * 내가 못 했으면 위로·응원 멘트와 깎인 포인트를 먼저 보여 준다.
 * 포인트는 매일 결과만 쌓이고 챌린지가 끝나면 한 번에 지급된다.
 */
function SettlementCard({ summary: s }) {
  const yesterday = addDays(toIsoDate(new Date()), -1)
  const when = s.weekly
    ? `${s.periodIndex + 1}주째`
    : s.periodEnd === yesterday
      ? '어제'
      : `${s.periodIndex + 1}일째`
  const total = s.successCount + s.failCount

  if (s.bet && !s.settled) {
    return (
      <div className="sr">
        <p className="sr-title">{when} 결과</p>
        <p className="sr-body">결과를 모으는 중이에요. 잠시 뒤에 다시 확인해 주세요.</p>
      </div>
    )
  }

  // 내가 그 기간 목표를 못 채웠으면: 응원 멘트 먼저, 그 아래 깎인 포인트
  if (s.mySuccess === false) {
    return <MissedCard summary={s} when={when} total={total} />
  }

  return (
    <div className={`sr${s.failCount === 0 && s.successCount > 0 ? ' is-perfect' : ''}`}>
      <p className="sr-title">{when} 결과</p>
      {s.failCount === 0 && s.successCount > 0 ? (
        <p className="sr-body">
          <strong>{when}는 실패한 사람이 없어요!</strong>
          <br />
          {s.weekly ? '이번 주도' : '오늘도'} 다 같이 열심히 갓생 살아볼까요?
        </p>
      ) : s.successCount === 0 ? (
        <p className="sr-body">
          <strong>{when}는 아무도 목표를 채우지 못했어요.</strong>
          <br />
          오늘은 다시 시작해 봐요!
        </p>
      ) : (
        <p className="sr-body">
          <strong>
            {total}명 중 {s.successCount}명 성공
          </strong>
          {s.bet && s.forfeitedPool > 0 && (
            <>
              <br />
              못 한 {s.failCount}명의 {p(s.forfeitedPool)}를 성공한 {s.successCount}명이 {p(s.rewardShare)}씩 나눠 가져요
            </>
          )}
        </p>
      )}
      {s.bet && s.mySuccess != null && !s.paid && (
        <p className={`sr-mine${s.mySuccess ? ' is-good' : ''}`}>
          {s.mySuccess
            ? `내 몫 ${p(s.myRefund)} 환급 예정${s.myReward > 0 ? ` · 보상 ${p(s.myReward)} 예정` : ''}`
            : s.myRefund > 0
              ? `목표를 다 채우지 못해 한 만큼 ${p(s.myRefund)}만 환급 예정이에요`
              : `${when}는 인증을 못 해서 그 몫을 잃었어요`}
        </p>
      )}
      {s.bet && s.mySuccess != null && (
        <p className="sr-total">
          {s.paid ? (
            <>
              정산 완료 · 환급 <strong>+{p(s.totalRefund)}</strong> · 보상 <strong>+{p(s.totalReward)}</strong>이
              지갑에 들어왔어요
            </>
          ) : (
            <>
              지금까지 환급 <strong>{p(s.totalRefund)}</strong> · 보상 <strong>{p(s.totalReward)}</strong> 쌓였어요.
              챌린지가 끝나면 한 번에 들어와요
            </>
          )}
        </p>
      )}
    </div>
  )
}

function MissedCard({ summary: s, when, total }) {
  const partial = s.myRefund > 0 // 주 N회에서 일부만 채움
  return (
    <div className="sr is-missed">
      <p className="sr-title">{when} 결과</p>
      <p className="sr-body">
        <strong>
          {s.weekly
            ? partial
              ? `${when}는 목표를 다 채우지 못했어요 ㅠㅠ`
              : `${when}는 한 번도 인증하지 못했어요 ㅠㅠ`
            : `${when}는 인증을 못 했어요 ㅠㅠ`}
        </strong>
        <br />
        괜찮아요, 다시 시작하면 돼요. {s.weekly ? '이번 주는 꼭 채워서' : '오늘은 꼭 인증하고'} 갓생 이어가요! 💪
      </p>
      {s.bet && s.myLost > 0 && (
        <div className="sr-lost">
          <span>깎인 포인트</span>
          <strong>−{p(s.myLost)}</strong>
          <small>
            {s.successCount > 0
              ? `${when} 성공한 ${s.successCount}명에게 나눠져요`
              : '아무도 성공하지 못해서 누구에게도 나눠지지 않아요'}
          </small>
        </div>
      )}
      <p className="sr-group">
        {total}명 중 {s.successCount}명 성공
      </p>
      {s.bet && (
        <p className="sr-total">
          지금까지 환급 <strong>{p(s.totalRefund)}</strong> · 보상 <strong>{p(s.totalReward)}</strong> 쌓였어요.
          챌린지가 끝나면 한 번에 들어와요
        </p>
      )}
    </div>
  )
}
