import { useEffect, useLayoutEffect, useRef, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { challengeApi, verificationApi } from '../api/client.js'
import { closedText, hasProgress } from '../challenge/format.js'
import { SettlementIntro } from '../challenge/SettlementIntro.jsx'
import { VerifyPhoto } from '../challenge/VerifyPhoto.jsx'

const MAX_SIDE = 1600
// 팝업이 그리드로 날아가 사라지는 시간 (CSS vcReveal 애니메이션과 맞춘다)
const REVEAL_MS = 4000
const POPUP_WIDTH = 300
const POLL_MS = 10_000

/**
 * 인증 화면 (헤더 없이 전체 화면).
 * 1단계: 카메라만 크게. 촬영 버튼을 누르면 바로 올라간다 (하루 한 번, 다시 찍기 없음).
 * 2단계: "촬영이 확인됐습니다!" 팝업이 뜬 뒤 내 사진이 그리드 칸으로 끌려가 자리를 잡고,
 *        오늘 인증 현황(게이지 + 참가자 사진)이 나타난다.
 * 올릴 때 AI 가 사진을 본다: 챌린지와 다른 사진이면 저장하지 않고 돌려보내 다시 찍게 하고,
 * 애매하면 일단 인증으로 받고 '확인 중'으로 표시한다 (관리자가 본 뒤 확정).
 * 오늘 이미 인증했거나 지금 인증할 수 없으면 바로 2단계를 보여 준다.
 */
export function ChallengeVerifyPage() {
  const { id } = useParams()
  const [data, setData] = useState({ challenge: null, mine: null, items: [], error: '' })

  useEffect(() => {
    let cancelled = false
    challengeApi
      .get(id)
      .then((challenge) =>
        Promise.all([
          hasProgress(challenge) ? verificationApi.mine(id) : Promise.resolve(null),
          verificationApi.list(id),
        ]).then(([mine, items]) => !cancelled && setData({ challenge, mine, items, error: '' })),
      )
      .catch((err) => !cancelled && setData((d) => ({ ...d, error: err.message })))
    return () => {
      cancelled = true
    }
  }, [id])

  const { challenge: c, error } = data

  return (
    <div className="vc">
      <header className="vc-nav">
        <Link to={`/challenges/${id}`} className="vc-back" aria-label="챌린지로 돌아가기">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" aria-hidden="true">
            <path d="M15 5l-7 7 7 7" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
          </svg>
        </Link>
        <span className="vc-nav-title">{c?.title}</span>
      </header>

      {c && <SettlementIntro challengeId={c.id} />}

      {error && !c ? (
        <div className="vc-body">
          <p className="vc-error">{error}</p>
        </div>
      ) : c ? (
        <VerifyScreen
          challenge={c}
          initialMine={data.mine}
          initialItems={data.items}
        />
      ) : null}
    </div>
  )
}

function VerifyScreen({ challenge: c, initialMine, initialItems }) {
  const [mine, setMine] = useState(initialMine)
  const [items, setItems] = useState(initialItems)
  // camera → (촬영) → reveal(팝업 애니메이션) → result
  const [phase, setPhase] = useState(initialMine?.state === 'OPEN' ? 'camera' : 'result')
  const [shotUrl, setShotUrl] = useState(null)
  const [shotInReview, setShotInReview] = useState(false)
  const [zoom, setZoom] = useState(null)

  useEffect(() => {
    if (phase !== 'reveal') return
    const timer = setTimeout(() => setPhase('result'), REVEAL_MS + 200)
    return () => clearTimeout(timer)
  }, [phase])

  // 현황 화면에서는 다른 참가자가 인증하면 사진이 바로 추가되도록 10초마다 다시 불러온다 (날아가는 연출 중에는 멈춤)
  useEffect(() => {
    if (phase !== 'result') return
    let cancelled = false
    const timer = setInterval(() => {
      if (document.visibilityState !== 'visible') return
      verificationApi
        .list(c.id)
        .then((list) => {
          if (cancelled) return
          // 방금 올린 내 사진은 이미 받아 둔 것(localUrl)을 그대로 쓴다
          setItems((prev) => list.map((v) => ({ ...v, localUrl: prev.find((p) => p.id === v.id)?.localUrl })))
        })
        .catch(() => {}) // 잠깐 끊겨도 다음 차례에 다시 시도
    }, POLL_MS)
    return () => {
      cancelled = true
      clearInterval(timer)
    }
  }, [phase, c.id])

  function onUploaded(verification, localUrl) {
    setShotUrl(localUrl)
    setShotInReview(verification.status === 'IN_REVIEW')
    setItems((list) => [...list, { ...verification, localUrl }])
    setMine((m) => ({ ...m, state: 'DONE_TODAY', successDays: m.successDays + 1 }))
    setPhase('reveal')
  }

  if (phase === 'camera') {
    return <CameraStage challengeId={c.id} onUploaded={onUploaded} />
  }

  return (
    <>
      <ResultStage
        challenge={c}
        mine={mine}
        items={items}
        revealing={phase === 'reveal'}
        onOpen={setZoom}
      />
      {phase === 'reveal' && <RevealPopup url={shotUrl} inReview={shotInReview} />}
      {zoom && (
        <PhotoModal
          challengeId={c.id}
          // 신고하면 목록의 값이 바뀌므로 목록에서 다시 찾는다
          item={items.find((v) => v.id === zoom.id) ?? zoom}
          onClose={() => setZoom(null)}
          onReported={(id) => setItems((list) => list.map((v) => (v.id === id ? { ...v, reported: true } : v)))}
        />
      )}
    </>
  )
}

/**
 * 1단계: 실시간 카메라. 누르면 그 장면을 찍어 바로 올린다.
 * 카메라는 https 또는 localhost 에서만 켜진다 (브라우저 보안 규칙).
 */
function CameraStage({ challengeId, onUploaded }) {
  const videoRef = useRef(null)
  const [facing, setFacing] = useState('environment')
  const [camera, setCamera] = useState({ ready: false, error: '', canSwitch: false })
  const [frozen, setFrozen] = useState(null) // 올리는 동안 멈춰 보여 줄 사진
  const [uploadError, setUploadError] = useState('')
  const supported = Boolean(navigator.mediaDevices?.getUserMedia)

  useEffect(() => {
    if (!supported) return
    let stream = null
    let cancelled = false
    navigator.mediaDevices
      .enumerateDevices()
      // 카메라가 아예 없으면 권한부터 묻지 않고 바로 '카메라를 찾지 못했어요'
      .then((devices) => {
        if (!devices.some((d) => d.kind === 'videoinput')) {
          throw new DOMException('no camera', 'NotFoundError')
        }
        return navigator.mediaDevices.getUserMedia({
          video: { facingMode: facing, width: { ideal: 1920 }, height: { ideal: 1080 } },
          audio: false,
        })
      })
      .then(async (s) => {
        if (cancelled) {
          s.getTracks().forEach((t) => t.stop())
          return
        }
        stream = s
        videoRef.current.srcObject = s
        const devices = await navigator.mediaDevices.enumerateDevices()
        if (!cancelled) {
          setCamera({ ready: true, error: '', canSwitch: devices.filter((d) => d.kind === 'videoinput').length > 1 })
        }
      })
      .catch((err) => {
        if (cancelled) return
        const denied = err.name === 'NotAllowedError' || err.name === 'SecurityError'
        setCamera({
          ready: false,
          canSwitch: false,
          error: denied
            ? '카메라 권한이 막혀 있어요.\n주소창 옆 카메라 아이콘에서 허용한 뒤 다시 들어와 주세요.'
            : '카메라를 찾지 못했어요.\n카메라가 연결되어 있는지 확인해 주세요.',
        })
      })
    return () => {
      cancelled = true
      stream?.getTracks().forEach((t) => t.stop())
    }
  }, [facing, supported])

  const error = supported
    ? camera.error
    : '이 주소에서는 카메라를 켤 수 없어요.\nhttps 주소나 localhost 로 접속해 주세요.'
  // 개발 서버에서 카메라가 안 켜져 있으면 샘플 사진으로 찍는다 (배포 빌드에는 들어가지 않음 → 운영은 카메라 직촬만)
  const testMode = import.meta.env.DEV && !camera.ready

  function shoot() {
    if (frozen) return
    const canvas = testMode ? testPhoto() : captureVideo(videoRef.current)
    if (!canvas) return
    canvas.toBlob(
      async (blob) => {
        if (!blob) return
        const url = URL.createObjectURL(blob)
        setFrozen(url)
        setUploadError('')
        try {
          const saved = await verificationApi.submit(challengeId, new File([blob], 'camera.jpg', { type: 'image/jpeg' }))
          onUploaded(saved, url)
        } catch (err) {
          URL.revokeObjectURL(url)
          setFrozen(null)
          setUploadError(
            err.code === 'VERIFICATION_REJECTED' ? `${err.message}\n오늘 인증 횟수에는 들어가지 않았어요.` : err.message,
          )
        }
      },
      'image/jpeg',
      0.9,
    )
  }

  return (
    <div className="vc-body">
      <p className="vc-label">오늘의 인증</p>

      <div className="vc-camera">
        <span className="vc-corner is-tl" aria-hidden="true" />
        <span className="vc-corner is-tr" aria-hidden="true" />
        <span className="vc-corner is-bl" aria-hidden="true" />
        <span className="vc-corner is-br" aria-hidden="true" />
        <video
          ref={videoRef}
          className={`vc-video${facing === 'user' ? ' is-mirrored' : ''}`}
          autoPlay
          playsInline
          muted
          hidden={Boolean(frozen) || Boolean(error)}
        />
        {frozen && <img className="vc-video" src={frozen} alt="찍은 인증 사진" />}
        {error && !frozen && (
          <div className="vc-camera-off">
            <CameraOffIcon />
            <p>{error}</p>
            {testMode && <p className="vc-test-note">개발용 테스트 모드: 촬영 버튼을 누르면 샘플 사진으로 인증해요</p>}
          </div>
        )}
        {!error && !camera.ready && !frozen && (
          <div className="vc-camera-off">
            <p>카메라를 켜는 중…</p>
            {testMode && <p className="vc-test-note">개발용 테스트 모드: 촬영 버튼을 누르면 샘플 사진으로 인증해요</p>}
          </div>
        )}
      </div>

      <div className="vc-controls">
        <span className="vc-side">
          {camera.canSwitch && !frozen && (
            <button
              type="button"
              className="vc-switch"
              onClick={() => setFacing((f) => (f === 'user' ? 'environment' : 'user'))}
              aria-label="앞·뒤 카메라 바꾸기"
            >
              <SwitchIcon />
            </button>
          )}
        </span>
        <button
          type="button"
          className="vc-shutter"
          onClick={shoot}
          disabled={(!camera.ready && !testMode) || Boolean(frozen)}
          aria-label="촬영"
        >
          <CameraIcon />
        </button>
        <span className="vc-side" />
      </div>
      <p className="vc-shutter-hint">{frozen ? '올리는 중…' : '눌러서 촬영'}</p>
      {uploadError && <p className="vc-error">{uploadError}</p>}
    </div>
  )
}

/** 카메라 화면의 지금 장면을 캔버스로 (긴 쪽 1600px 이하) */
function captureVideo(video) {
  if (!video?.videoWidth) return null
  const scale = Math.min(1, MAX_SIDE / Math.max(video.videoWidth, video.videoHeight))
  const canvas = document.createElement('canvas')
  canvas.width = Math.round(video.videoWidth * scale)
  canvas.height = Math.round(video.videoHeight * scale)
  canvas.getContext('2d').drawImage(video, 0, 0, canvas.width, canvas.height)
  return canvas
}

/** 개발용 샘플 사진. 찍은 시각을 넣어 매번 다른 파일이 되게 한다 (같은 사진 재사용 차단에 걸리지 않게). */
function testPhoto() {
  const canvas = document.createElement('canvas')
  canvas.width = 960
  canvas.height = 1200
  const g = canvas.getContext('2d')
  const hue = Math.floor(Math.random() * 360)
  const bg = g.createLinearGradient(0, 0, 960, 1200)
  bg.addColorStop(0, `hsl(${hue} 45% 82%)`)
  bg.addColorStop(1, `hsl(${(hue + 40) % 360} 40% 62%)`)
  g.fillStyle = bg
  g.fillRect(0, 0, 960, 1200)
  g.fillStyle = 'rgba(255, 255, 255, 0.55)'
  g.beginPath()
  g.arc(480, 520, 220, 0, Math.PI * 2)
  g.fill()
  g.fillStyle = '#2a2925'
  g.textAlign = 'center'
  g.font = 'bold 72px "Malgun Gothic", sans-serif'
  g.fillText('테스트 촬영', 480, 545)
  g.font = '44px "Malgun Gothic", sans-serif'
  g.fillText(new Date().toLocaleTimeString('ko-KR'), 480, 1040)
  return canvas
}

/** 2단계: 오늘 인증 현황 (제목 + 게이지 + 참가자 사진 그리드) */
function ResultStage({ challenge: c, mine, items, revealing, onOpen }) {
  const total = Math.max(c.participantCount, items.length)
  const percent = total > 0 ? Math.round((items.length / total) * 100) : 0
  const empty = Math.max(0, total - items.length)
  const note = mine ? (mine.state === 'DONE_TODAY' ? null : closedText(mine.state, c)) : '참가자만 인증할 수 있어요.'
  const myReview = items.some((v) => v.mine && v.status === 'IN_REVIEW')

  return (
    <div className={`vc-body vc-result${revealing ? ' is-revealing' : ''}`}>
      <h1 className="vc-result-title">오늘 인증 현황</h1>
      <p className="vc-result-count">
        오늘 <strong>{items.length}</strong> / {total}명 인증 · <strong>{percent}%</strong>
        {note && <span className="vc-result-note"> · {note}</span>}
      </p>
      <div className="vc-gauge" role="progressbar" aria-label="오늘 인증한 참가자 비율" aria-valuenow={percent} aria-valuemin={0} aria-valuemax={100}>
        <span style={{ width: `${percent}%` }} />
      </div>

      {myReview && (
        <p className="vc-review-note">
          내 사진은 한 번 더 확인하고 있어요. 그동안은 인증한 것으로 쳐요. 인정되지 않으면 알림으로 알려 드려요.
        </p>
      )}

      <ul className="vc-grid">
        {items.map((v) => (
          // 막 올린 내 칸은 날아온 사진을 이어받아 스르륵 나타난다 (사진 칸만 커지게 해서 착지 위치가 흔들리지 않게)
          <li key={v.id} className={v.localUrl && revealing ? 'vc-tile-pop' : undefined} data-mine={v.mine || undefined}>
            <button type="button" className="vc-tile" onClick={() => onOpen(v)} aria-label={`${v.mine ? '내' : `${v.nickname}의`} 인증 사진 크게 보기`}>
              {v.localUrl ? (
                <img className="vf-photo" src={v.localUrl} alt="" />
              ) : (
                <VerifyPhoto challengeId={c.id} verificationId={v.id} alt="" />
              )}
              {v.status === 'IN_REVIEW' ? <span className="vc-review-chip">확인 중</span> : <CheckBadge />}
            </button>
            <p className="vc-tile-caption">
              <span className={v.mine ? 'is-mine' : undefined}>{v.mine ? '나' : v.nickname}</span>
              <time>{v.receivedAt.slice(11, 16)}</time>
            </p>
          </li>
        ))}
        {empty > 0 && (
          <li>
            <div className="vc-tile is-waiting">
              <strong>{empty}명</strong>
              <span>아직 인증 전</span>
            </div>
          </li>
        )}
      </ul>
    </div>
  )
}

/**
 * 촬영 직후 가운데에 크게 뜨는 내 사진 + "촬영이 확인됐습니다!".
 * 잠시 멈췄다가 그리드의 내 칸으로 끌려가듯 날아가 작아지며 사라진다.
 * 날아갈 위치는 그리드에서 내 칸을 재서 CSS 변수로 넘긴다.
 */
function RevealPopup({ url, inReview }) {
  const anchorRef = useRef(null)
  const revealRef = useRef(null)
  const cardRef = useRef(null)

  // 그리기 전에 내 칸 위치를 재서 CSS 변수로 넘기고 애니메이션을 시작한다
  useLayoutEffect(() => {
    const tile = document.querySelector('.vc-grid [data-mine] .vc-tile')
    const anchor = anchorRef.current
    const reveal = revealRef.current
    if (!tile || !anchor || !reveal) return
    // 내 칸은 커지는 애니메이션 중이라 크기는 offsetWidth(변형 전)로, 위치는 가운데 좌표로 잰다
    const t = tile.getBoundingClientRect()
    const a = anchor.getBoundingClientRect()
    const scale = tile.offsetWidth / POPUP_WIDTH
    const origin = { x: a.left + a.width / 2, y: a.top + a.height / 2 }
    const cardY = a.top + cardRef.current.offsetHeight / 2
    // 가운데(origin)를 기준으로 줄어든 뒤 옮겨지므로, 줄어들며 카드 중심이 움직이는 만큼을 빼 준다
    const fx = t.left + t.width / 2 - origin.x
    const fy = t.top + t.height / 2 - origin.y - (cardY - origin.y) * scale
    // 85%: 내 칸 바로 앞(90% 지점)까지 끌려옴 → 100%: 내 칸 위치·크기에 정확히 겹쳐 앉으며 사라짐
    reveal.style.setProperty('--vc-near-x', `${fx * 0.9}px`)
    reveal.style.setProperty('--vc-near-y', `${fy * 0.9}px`)
    reveal.style.setProperty('--vc-near-s', scale + (1 - scale) * 0.25)
    reveal.style.setProperty('--vc-fx', `${fx}px`)
    reveal.style.setProperty('--vc-fy', `${fy}px`)
    reveal.style.setProperty('--vc-fs', scale)
    reveal.classList.add('is-playing')
  }, [])

  return (
    <div className="vc-reveal-anchor" ref={anchorRef} aria-live="polite">
      <div className="vc-reveal" ref={revealRef}>
        <div className="vc-reveal-card" ref={cardRef}>
          {url && <img src={url} alt="방금 올린 내 인증 사진" />}
          <span className="vc-reveal-name">나</span>
          <CheckBadge large />
        </div>
        <p className="vc-reveal-text">{inReview ? '인증됐어요 · 사진은 확인 중이에요' : '촬영이 확인됐습니다!'}</p>
      </div>
    </div>
  )
}

const REPORT_REASONS = ['챌린지와 관계없는 사진이에요', '예전에 찍은 사진 같아요', '다른 사람의 사진 같아요']

/** 사진 크게 보기 아래의 신고 (남의 인증만). 신고하면 관리자가 사진을 확인한다 */
function ReportBox({ challengeId, item, onReported }) {
  const [open, setOpen] = useState(false)
  const [reason, setReason] = useState(REPORT_REASONS[0])
  const [detail, setDetail] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  if (item.reported) {
    return <p className="vc-report-done">신고한 인증이에요. 관리자가 확인한 뒤 결과를 알림으로 알려 드려요.</p>
  }
  if (!open) {
    return (
      <button type="button" className="vc-report-open" onClick={() => setOpen(true)}>
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" aria-hidden="true">
          <path d="M5 21V4m0 1h11l-2 4 2 4H5" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
        이 인증 신고하기
      </button>
    )
  }

  async function submit(e) {
    e.preventDefault()
    setBusy(true)
    setError('')
    try {
      const text = detail.trim()
      await verificationApi.report(challengeId, item.id, text ? `${reason} — ${text}` : reason)
      onReported(item.id)
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <form className="vc-report" onSubmit={submit}>
      <p className="vc-report-title">어떤 점이 의심되나요?</p>
      <div className="vc-report-reasons" role="radiogroup" aria-label="신고 이유">
        {REPORT_REASONS.map((r) => (
          <button
            key={r}
            type="button"
            role="radio"
            aria-checked={reason === r}
            className={`vc-report-reason${reason === r ? ' is-active' : ''}`}
            onClick={() => setReason(r)}
          >
            {r}
          </button>
        ))}
      </div>
      <input
        type="text"
        className="vc-report-detail"
        value={detail}
        maxLength={120}
        placeholder="자세한 내용 (선택)"
        aria-label="자세한 내용"
        onChange={(e) => setDetail(e.target.value)}
      />
      {error && <p className="vc-error">{error}</p>}
      <div className="vc-report-foot">
        <button type="button" className="vc-report-cancel" disabled={busy} onClick={() => setOpen(false)}>
          취소
        </button>
        <button type="submit" className="vc-report-submit" disabled={busy}>
          {busy ? '보내는 중…' : '신고하기'}
        </button>
      </div>
    </form>
  )
}

function PhotoModal({ challengeId, item, onClose, onReported }) {
  useEffect(() => {
    function onKeyDown(e) {
      if (e.key === 'Escape') onClose()
    }
    document.addEventListener('keydown', onKeyDown)
    return () => document.removeEventListener('keydown', onKeyDown)
  }, [onClose])

  const name = item.mine ? '나' : item.nickname
  return (
    <div className="vc-modal" role="dialog" aria-modal="true" aria-label={`${name}의 인증 사진`} onClick={onClose}>
      <div className="vc-modal-col">
        <div className="vc-modal-card">
          {item.localUrl ? (
            <img className="vf-photo" src={item.localUrl} alt="" />
          ) : (
            <VerifyPhoto challengeId={challengeId} verificationId={item.id} alt="" />
          )}
          <span className="vc-modal-name">
            {name} · {item.receivedAt.slice(11, 16)}
          </span>
          <CheckBadge large />
        </div>
        {!item.mine && (
          // 신고 칸을 눌러도 창이 닫히지 않게
          <div className="vc-report-wrap" onClick={(e) => e.stopPropagation()}>
            <ReportBox challengeId={challengeId} item={item} onReported={onReported} />
          </div>
        )}
      </div>
    </div>
  )
}

function CheckBadge({ large }) {
  return (
    <span className={`vc-check${large ? ' is-large' : ''}`} aria-hidden="true">
      <svg viewBox="0 0 12 12" fill="none">
        <path d="M2 6l2.6 2.6L10 3" stroke="#14251b" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" />
      </svg>
    </span>
  )
}

function CameraIcon() {
  return (
    <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" aria-hidden="true">
      <path
        d="M4 8a2 2 0 0 1 2-2h2l1.2-1.8A2 2 0 0 1 10.9 3h2.2a2 2 0 0 1 1.7 1.2L16 6h2a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V8z"
        strokeWidth="1.7"
        strokeLinejoin="round"
      />
      <circle cx="12" cy="13" r="3.4" strokeWidth="1.7" />
    </svg>
  )
}

function CameraOffIcon() {
  return (
    <svg width="42" height="42" viewBox="0 0 24 24" fill="none" stroke="currentColor" aria-hidden="true">
      <path
        d="M4 8a2 2 0 0 1 2-2h2l1.2-1.8A2 2 0 0 1 10.9 3h2.2a2 2 0 0 1 1.7 1.2L16 6h2a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V8z"
        strokeWidth="1.5"
        strokeLinejoin="round"
      />
      <circle cx="12" cy="13" r="3.4" strokeWidth="1.5" />
      <path d="M3 3l18 18" strokeWidth="1.5" strokeLinecap="round" />
    </svg>
  )
}

function SwitchIcon() {
  return (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" aria-hidden="true">
      <path d="M4 12a8 8 0 0 1 13.7-5.6L20 8.5M20 4v4.5h-4.5" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
      <path d="M20 12a8 8 0 0 1-13.7 5.6L4 15.5M4 20v-4.5h4.5" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  )
}
