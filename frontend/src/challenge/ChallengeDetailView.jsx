import { useState } from 'react'
import { Link } from 'react-router-dom'
import { Avatar } from '../components/UserMenu.jsx'
import { MODE_LABEL, dDayText, frequencyText, periodText, pointText, timeText } from './format.js'
import { CategoryIcon } from './icons.jsx'

// 참가자 동그라미 색 (사진이 없으면 닉네임 첫 글자 + 이 색들을 돌아가며)
const TINTS = 4

/**
 * 챌린지 상세 카드. 상세 화면(/challenges/:id)과 초대 링크 화면(/challenges/join/:code)이 같이 쓴다.
 * joinLabel 로 참여 버튼 문구를, backTo 로 맨 위 돌아가기 링크를 바꿀 수 있다.
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
  joinLabel = '참여하기',
  notice,
}) {
  const isBet = c.mode === 'BET'

  return (
    <div className="container page detail-page">
      <Link to="/challenges" className="back-button">
        <svg width="10" height="10" viewBox="0 0 10 10" fill="none" stroke="currentColor" aria-hidden="true">
          <path d="M6.5 1.5L2 5l4.5 3.5" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
        챌린지 목록
      </Link>

      {notice}

      {c.member && (
        <nav className="dt-tabs" aria-label="챌린지 메뉴">
          <span className="dt-tab is-active" aria-current="page">
            정보
          </span>
          <Link to={`/challenges/${c.id}/chat`} className="dt-tab">
            오픈채팅
          </Link>
        </nav>
      )}

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
              {isBet && (c.partialRefund ? ' · 부분 성공 시 비례 환급' : ' · 성공 시 전액 환급')}
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

        {c.inviteCode && (
          <InviteBox
            code={c.inviteCode}
            isPrivate={c.visibility === 'PRIVATE'}
            canRegenerate={c.host && Boolean(onRegenerateInvite)}
            busy={busy}
            onRegenerate={onRegenerateInvite}
          />
        )}

        <div className="dt-action">
          <JoinAction
            challenge={c}
            authed={authed}
            from={from}
            busy={busy}
            joinLabel={joinLabel}
            onJoin={onJoin}
            onLeave={onLeave}
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
                  {p.profileImageUrl ? (
                    <Avatar src={p.profileImageUrl} size={34} />
                  ) : (
                    <span className={`dt-initial t${i % TINTS}`} aria-hidden="true">
                      {p.nickname.slice(0, 1)}
                    </span>
                  )}
                  <span>{p.nickname}</span>
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

/** 개설자·참가자에게만 보이는 초대 링크. 복사해서 친구에게 보내면 그 링크로 참여한다. */
function InviteBox({ code, isPrivate, canRegenerate, busy, onRegenerate }) {
  const link = `${window.location.origin}/challenges/join/${code}`
  const [copied, setCopied] = useState(false)
  const [confirming, setConfirming] = useState(false)

  async function copy() {
    try {
      await navigator.clipboard.writeText(link)
    } catch {
      // 클립보드 권한이 없으면(오래된 브라우저 등) 선택해 두어 직접 복사하게 한다.
      document.getElementById('invite-link')?.select()
      return
    }
    setCopied(true)
    setTimeout(() => setCopied(false), 2000)
  }

  return (
    <section className="dt-section invite-box">
      <h2>친구 초대</h2>
      <p className="invite-help">
        {isPrivate
          ? '비공개 챌린지라 이 링크를 받은 사람만 참여할 수 있어요.'
          : '링크를 보내면 친구가 바로 이 챌린지로 들어와요.'}
      </p>
      <div className="invite-row">
        <input id="invite-link" className="invite-link" value={link} readOnly aria-label="초대 링크" />
        <button type="button" className="btn btn-dark invite-copy" onClick={copy}>
          {copied ? '복사했어요' : '초대 링크 복사'}
        </button>
      </div>
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
              링크가 새어 나갔나요? 초대 링크 새로 만들기
            </button>
          </p>
        ))}
    </section>
  )
}

function JoinAction({ challenge: c, authed, from, busy, joinLabel, onJoin, onLeave }) {
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
  if (!c.recruiting) return <Disabled>모집이 끝났어요</Disabled>
  if (c.participantCount >= c.maxParticipants) return <Disabled>정원이 다 찼어요</Disabled>
  if (c.mode === 'BET') {
    return (
      <>
        <Disabled>포인트 챌린지 참여 준비 중</Disabled>
        <p className="dt-note">포인트 지갑이 열리면 참여할 수 있어요</p>
      </>
    )
  }
  return (
    <button type="button" className="btn btn-dark btn-block dt-btn" onClick={onJoin} disabled={busy}>
      {busy ? '참여하는 중…' : joinLabel}
    </button>
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
