import { useState } from 'react'
import { Link } from 'react-router-dom'
import { walletApi } from '../api/client.js'
import { Avatar } from '../components/UserMenu.jsx'
import {
  MODE_LABEL,
  dDayText,
  daysBetween,
  frequencyText,
  periodText,
  pointText,
  timeText,
  toIsoDate,
} from './format.js'
import { CategoryIcon } from './icons.jsx'
import { VerificationPanel } from './VerificationPanel.jsx'

// 참가자 동그라미 색 (사진이 없으면 닉네임 첫 글자 + 이 색들을 돌아가며)
const TINTS = 4

/**
 * 챌린지 상세 카드. 상세 화면(/challenges/:id)과 초대 링크 화면(/challenges/join/:code)이 같이 쓴다.
 * joinLabel 로 참여 버튼 문구를, backTo 로 맨 위 돌아가기 링크를 바꿀 수 있다.
 * showVerification 이면 시작한 챌린지에 '오늘의 인증' 칸을 보여 준다 (개설자·참가자만).
 */
export function ChallengeDetailView({
  challenge: c,
  authed,
  from,
  busy,
  actionError,
  onJoin,
  onLeave,
  onRegenerateInvite,
  onDelete,
  onGiveUp,
  joinLabel = '참여하기',
  notice,
  showVerification = false,
}) {
  const isBet = c.mode === 'BET'
  const started = daysBetween(toIsoDate(new Date()), c.startDate) <= 0

  return (
    <div className="container page detail-page">
      <Link to="/challenges" className="back-button">
        <svg width="10" height="10" viewBox="0 0 10 10" fill="none" stroke="currentColor" aria-hidden="true">
          <path d="M6.5 1.5L2 5l4.5 3.5" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
        챌린지 목록
      </Link>

      {notice}

      <article className="card dt-card">
        <div className="dt-top">
          <div className="dt-tags">
            <span className="dt-tag">
              <CategoryIcon id={c.category.id} size={13} strokeWidth={1.8} />
              {c.category.name}
            </span>
            <span className={`dt-tag ${isBet ? 'is-bet' : 'is-free'}`}>{MODE_LABEL[c.mode]}</span>
            {c.visibility === 'PRIVATE' && (
              <span className="dt-tag is-private">
                <LockIcon />
                비공개
              </span>
            )}
          </div>
          <span className="dt-dday">{dDayText(c)}</span>
        </div>

        <h1 className="dt-title">{c.title}</h1>
        {c.member && c.notice && (
          <p className="dt-notice">
            <span aria-hidden="true">📢</span> <span className="sr-only">방장 공지: </span>
            {c.notice}
          </p>
        )}
        <div className="dt-host">
          <Avatar size={24} />
          <span>개설자 {c.hostNickname}</span>
        </div>

        {(c.member || c.inviteCode) && (
          <DetailActions
            challengeId={c.id}
            inviteCode={c.inviteCode}
            canChat={c.member}
            canRegenerate={c.host && Boolean(onRegenerateInvite) && c.recruiting}
            closedReason={inviteClosedReason(c)}
            busy={busy}
            onRegenerate={onRegenerateInvite}
          />
        )}

        {/* 진행 중에는 가장 자주 보는 오늘의 인증을 맨 위에 */}
        {showVerification && c.member && started && <VerificationPanel challenge={c} />}

        <dl className="mc-rows dt-rows">
          <div>
            <dt>기간</dt>
            <dd>{periodText(c)}</dd>
          </div>
          <div>
            <dt>인증</dt>
            <dd>
              {frequencyText(c)}
              {c.verifyFrom && ` · ${timeText(c.verifyFrom)} ~ ${timeText(c.verifyUntil)}에만`}
            </dd>
          </div>
          <div>
            <dt>참가 포인트</dt>
            <dd>
              {pointText(c)}
              {isBet && ' · 인증한 날만큼 환급, 끝나면 한 번에 정산'}
            </dd>
          </div>
          <div>
            <dt>참가자</dt>
            <dd>
              {c.participantCount} / {c.maxParticipants}명
            </dd>
          </div>
        </dl>

        <section className="dt-section">
          <h2>챌린지 소개</h2>
          <p className="dt-description">{c.description}</p>
        </section>

        <div className="dt-action">
          <JoinAction
            challenge={c}
            authed={authed}
            from={from}
            busy={busy}
            joinLabel={joinLabel}
            onJoin={onJoin}
            onLeave={onLeave}
            onGiveUp={onGiveUp}
          />
          {actionError && <p className="form-error">{actionError}</p>}
        </div>

        <section className="dt-section">
          <h2>참가자 {c.participants.length}명</h2>
          {c.participants.length === 0 ? (
            <p className="muted">아직 참가자가 없어요. 첫 번째로 참여해 보세요!</p>
          ) : (
            <ul className="dt-people">
              {c.participants.map((p, i) => (
                <li key={`${p.nickname}-${i}`}>
                  <Link to={`/users/${p.userId}`} className="dt-person">
                    {p.profileImageUrl ? (
                      <Avatar src={p.profileImageUrl} size={34} />
                    ) : (
                      <span className={`dt-initial t${i % TINTS}`} aria-hidden="true">
                        {p.nickname.slice(0, 1)}
                      </span>
                    )}
                    <span>{p.nickname}</span>
                  </Link>
                </li>
              ))}
            </ul>
          )}
        </section>

        {c.host && onDelete && <DeleteBox canDelete={c.canLeave} busy={busy} onDelete={onDelete} />}
      </article>
    </div>
  )
}

/** 개설자 전용. 시작일 전날까지만 지울 수 있고, 누르면 한 번 더 확인한다. */
function DeleteBox({ canDelete, busy, onDelete }) {
  const [confirming, setConfirming] = useState(false)

  if (!canDelete) {
    return <p className="dt-delete-note">시작된 챌린지는 삭제할 수 없어요.</p>
  }
  if (!confirming) {
    return (
      <div className="dt-delete">
        <button type="button" className="dt-delete-btn" onClick={() => setConfirming(true)}>
          챌린지 삭제
        </button>
      </div>
    )
  }
  return (
    <div className="dt-delete is-confirming" role="alert">
      <p>정말 삭제할까요? 참가자 목록과 오픈채팅도 함께 지워지고 되돌릴 수 없어요.</p>
      <div className="dt-delete-actions">
        <button type="button" className="btn btn-outline" onClick={() => setConfirming(false)} disabled={busy}>
          취소
        </button>
        <button type="button" className="btn dt-delete-confirm" onClick={onDelete} disabled={busy}>
          {busy ? '삭제하는 중…' : '삭제하기'}
        </button>
      </div>
    </div>
  )
}

/**
 * 개설자 줄 아래 버튼 줄: [오픈채팅] (멤버만) · [초대 링크 복사] (멤버에게만 코드가 온다).
 * 방장은 링크가 새어 나갔을 때 새로 만들 수 있다 (이전 링크는 막힘).
 */
function DetailActions({ challengeId, inviteCode, canChat, canRegenerate, closedReason, busy, onRegenerate }) {
  const [copied, setCopied] = useState(false)
  const [confirming, setConfirming] = useState(false)
  const [blocked, setBlocked] = useState(false)

  async function copy() {
    // 시작한 챌린지는 초대 링크로 들어와도 참여할 수 없으니 복사 대신 이유를 알려 준다
    if (closedReason) {
      setBlocked(true)
      setTimeout(() => setBlocked(false), 3500)
      return
    }
    const link = `${window.location.origin}/challenges/join/${inviteCode}`
    try {
      await navigator.clipboard.writeText(link)
    } catch {
      // 클립보드 권한이 없으면(오래된 브라우저 등) 링크를 보여 주고 직접 복사하게 한다.
      window.prompt('아래 초대 링크를 복사해 주세요', link)
      return
    }
    setCopied(true)
    setTimeout(() => setCopied(false), 2000)
  }

  return (
    <div className="dt-buttons">
      <div className="dt-buttons-row">
        {canChat && (
          <Link to={`/challenges/${challengeId}/chat`} className="dt-pill-btn">
            <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" aria-hidden="true">
              <path
                d="M4 5h16a1 1 0 0 1 1 1v10a1 1 0 0 1-1 1H9l-5 4v-4H4a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1z"
                strokeWidth="1.7"
                strokeLinejoin="round"
              />
            </svg>
            오픈채팅
          </Link>
        )}
        {inviteCode && (
          <button type="button" className="dt-pill-btn" onClick={copy}>
            <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" aria-hidden="true">
              <path
                d="M9.5 13.5a4 4 0 0 0 5.7 0.3l2.6-2.6a4 4 0 1 0-5.6-5.6L10.8 6"
                strokeWidth="1.7"
                strokeLinecap="round"
              />
              <path
                d="M14.5 10.5a4 4 0 0 0-5.7-0.3l-2.6 2.6a4 4 0 1 0 5.6 5.6l1.4-1.4"
                strokeWidth="1.7"
                strokeLinecap="round"
              />
            </svg>
            {copied ? '복사했어요' : '초대 링크 복사'}
          </button>
        )}
      </div>
      {blocked && (
        <p className="invite-closed" role="status">
          {closedReason}
        </p>
      )}
      {canRegenerate &&
        (confirming ? (
          <p className="invite-regen">
            새 링크를 만들면 지금 링크로는 더 이상 들어올 수 없어요.
            <button
              type="button"
              className="link-button"
              disabled={busy}
              onClick={() => {
                setConfirming(false)
                onRegenerate()
              }}
            >
              새로 만들기
            </button>
            <button type="button" className="link-button is-muted" onClick={() => setConfirming(false)}>
              취소
            </button>
          </p>
        ) : (
          <p className="invite-regen">
            <button type="button" className="link-button is-muted" onClick={() => setConfirming(true)}>
              초대 링크가 새어 나갔나요? 새로 만들기
            </button>
          </p>
        ))}
    </div>
  )
}

/** 초대 링크로 참여할 수 없는 이유. 모집 중(시작일 당일까지)이면 null */
function inviteClosedReason(c) {
  if (c.recruiting) return null
  if (daysBetween(toIsoDate(new Date()), c.endDate) < 0) {
    return '끝난 챌린지라 초대 링크로 참여할 수 없어요.'
  }
  return '이미 진행 중인 챌린지라 새로 참여할 수 없어요. 초대 링크는 시작일까지만 쓸 수 있어요.'
}

function JoinAction({ challenge: c, authed, from, busy, joinLabel, onJoin, onLeave, onGiveUp }) {
  if (!authed) {
    return (
      <Link to="/login" state={{ from }} className="btn btn-dark btn-block dt-btn">
        로그인하고 참여하기
      </Link>
    )
  }
  if (c.joined) {
    return (
      <div className="joined">
        <p className="joined-badge">참여 중인 챌린지예요</p>
        {onLeave && c.canLeave ? (
          <button type="button" className="btn btn-outline btn-block dt-btn" onClick={onLeave} disabled={busy}>
            참여 취소
          </button>
        ) : onGiveUp && c.status !== 'ENDED' && c.status !== 'SETTLED' ? (
          <GiveUpBox busy={busy} onGiveUp={onGiveUp} />
        ) : onLeave ? (
          <p className="dt-note">시작된 챌린지는 참여를 취소할 수 없어요.</p>
        ) : (
          <Link to={`/challenges/${c.id}`} className="btn btn-outline btn-block dt-btn">
            챌린지로 가기
          </Link>
        )}
      </div>
    )
  }
  // 끝났거나 포기한 챌린지: 참여 버튼 대신 결과
  if (c.myStatus === 'COMPLETED') return <p className="joined-badge dt-result">성공한 챌린지예요 🎉</p>
  if (c.myStatus === 'FAILED') return <p className="dt-result is-failed">아쉽게 실패한 챌린지예요</p>
  if (c.myStatus === 'GAVE_UP') return <p className="dt-result is-failed">포기하고 나간 챌린지예요</p>
  if (!c.recruiting) return <Disabled>모집이 끝났어요</Disabled>
  if (c.participantCount >= c.maxParticipants) return <Disabled>정원이 다 찼어요</Disabled>
  if (c.mode === 'BET') {
    return <BetJoin challenge={c} busy={busy} onJoin={onJoin} />
  }
  return (
    <button type="button" className="btn btn-dark btn-block dt-btn" onClick={onJoin} disabled={busy}>
      {busy ? '참여하는 중…' : joinLabel}
    </button>
  )
}

/**
 * 포인트 챌린지 참여: 누르면 내 충전 포인트를 불러와 "얼마를 걸고, 참여 후 얼마가 남는지"를 보여 주고 한 번 더 확인한다.
 * 모자라면 지갑으로 안내한다. (서버가 잠근 채로 다시 확인하므로 여기 숫자는 안내용)
 */
function BetJoin({ challenge: c, busy, onJoin }) {
  const [wallet, setWallet] = useState(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const fee = c.entryFee

  async function open() {
    setLoading(true)
    setError('')
    try {
      setWallet(await walletApi.get())
    } catch (err) {
      setError(err.message)
    } finally {
      setLoading(false)
    }
  }

  if (!wallet) {
    return (
      <>
        <button type="button" className="btn btn-dark btn-block dt-btn" onClick={open} disabled={loading || busy}>
          {loading ? '불러오는 중…' : `${fee.toLocaleString()}P 걸고 참여하기`}
        </button>
        {error && <p className="form-error">{error}</p>}
      </>
    )
  }

  const enough = wallet.chargedBalance >= fee
  return (
    <div className="bet-confirm" role="alert">
      <p className="bet-confirm-title">{fee.toLocaleString()}P를 걸고 참여할까요?</p>
      <p className="bet-confirm-balance">
        충전 포인트 <strong>{wallet.chargedBalance.toLocaleString()}P</strong>
        {enough && (
          <>
            {' '}
            → <strong>{(wallet.chargedBalance - fee).toLocaleString()}P</strong>
          </>
        )}
      </p>
      <p className="bet-confirm-help">
        건 포인트를 하루 몫으로 나눠, 인증한 날은 그날 몫을 돌려받고 못 한 날 몫은 그날 성공한 사람들이 나눠 가져요(보상
        포인트). 결과는 매일 보여 드리고, 포인트는 챌린지가 끝나면 한 번에 들어와요. 시작 전에 참여를 취소하면 전부
        돌려받아요.
      </p>
      {enough ? (
        <div className="dt-delete-actions">
          <button type="button" className="btn btn-outline" onClick={() => setWallet(null)} disabled={busy}>
            취소
          </button>
          <button type="button" className="btn btn-dark" onClick={onJoin} disabled={busy}>
            {busy ? '참여하는 중…' : '참여하기'}
          </button>
        </div>
      ) : (
        <div className="dt-delete-actions">
          <button type="button" className="btn btn-outline" onClick={() => setWallet(null)}>
            닫기
          </button>
          <Link to="/wallet" className="btn btn-dark">
            충전 포인트가 모자라요 · 충전하러 가기
          </Link>
        </div>
      )}
    </div>
  )
}

/** 진행 중 포기. 한 번 더 확인한다. 포기하면 실패로 치고 채팅·인증 사진에서도 나간다. */
function GiveUpBox({ busy, onGiveUp }) {
  const [confirming, setConfirming] = useState(false)

  if (!confirming) {
    return (
      <p className="dt-giveup">
        <button type="button" className="link-button is-muted" onClick={() => setConfirming(true)}>
          챌린지 포기하기
        </button>
      </p>
    )
  }
  return (
    <div className="dt-delete is-confirming" role="alert">
      <p>
        정말 포기할까요? 실패로 처리되고 챌린지에서 나가요. 오픈채팅과 인증 사진도 더는 볼 수 없고, 다시 참여할 수
        없어요.
      </p>
      <div className="dt-delete-actions">
        <button type="button" className="btn btn-outline" onClick={() => setConfirming(false)} disabled={busy}>
          계속하기
        </button>
        <button type="button" className="btn dt-delete-confirm" onClick={onGiveUp} disabled={busy}>
          {busy ? '처리하는 중…' : '포기하기'}
        </button>
      </div>
    </div>
  )
}

function Disabled({ children }) {
  return (
    <button type="button" className="btn btn-block dt-btn dt-btn-off" disabled>
      {children}
    </button>
  )
}

function LockIcon() {
  return (
    <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" aria-hidden="true">
      <rect x="5" y="11" width="14" height="10" rx="2.5" strokeWidth="1.8" />
      <path d="M8 11V8a4 4 0 0 1 8 0v3" strokeWidth="1.8" strokeLinecap="round" />
    </svg>
  )
}
