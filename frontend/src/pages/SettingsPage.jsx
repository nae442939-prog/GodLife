import { useEffect, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { blockApi, inquiryApi, notificationApi, userApi } from '../api/client.js'
import { disablePush, enablePush, pushStatus } from '../api/push.js'
import { PASSWORD_HINT, passwordError } from '../auth/rules.js'
import { useAuth } from '../auth/useAuth.js'
import { Field } from '../components/Field.jsx'
import { PhoneVerification } from '../components/PhoneVerification.jsx'
import { Avatar } from '../components/UserMenu.jsx'

const SOCIAL_LABEL = { KAKAO: '카카오', GOOGLE: '구글', NAVER: '네이버' }

// 왼쪽 메뉴. 고른 항목은 주소(?tab=)에 둔다
const TABS = [
  { key: 'info', label: '내 정보' },
  { key: 'password', label: '비밀번호 변경' },
  { key: 'security', label: '보안' },
  { key: 'blocked', label: '차단한 사용자' },
  { key: 'alarm', label: '알림' },
  { key: 'support', label: '고객센터' },
  { key: 'withdraw', label: '회원 탈퇴' },
]

/**
 * 설정 (/settings): 왼쪽 메뉴에서 고르면 오른쪽에 그 내용이 나온다.
 * 내 정보(휴대폰 번호 변경) · 비밀번호 변경 · 보안(자동 로그인) · 차단한 사용자 · 알림(종류별 켜기/끄기) · 고객센터(자주 묻는 질문 · 1:1 문의) · 회원 탈퇴.
 * 프로필(사진 · 닉네임 · 한 줄 소개) 수정은 마이페이지에서 한다.
 */
export function SettingsPage() {
  const [params, setParams] = useSearchParams()
  const tab = TABS.some((t) => t.key === params.get('tab')) ? params.get('tab') : 'info'
  const [account, setAccount] = useState({ data: null, error: '' })

  useEffect(() => {
    let cancelled = false
    userApi
      .account()
      .then((data) => !cancelled && setAccount({ data, error: '' }))
      .catch((err) => !cancelled && setAccount({ data: null, error: err.message }))
    return () => {
      cancelled = true
    }
  }, [])

  return (
    <div className="container page">
      <div className="ch-head cl-head">
        <div>
          <h1 className="page-title">설정</h1>
          <p className="page-sub">내 정보와 계정을 관리합니다.</p>
        </div>
        <Link to="/me" className="btn btn-dark-outline">
          마이페이지로
        </Link>
      </div>

      <div className="settings-layout">
        <nav className="settings-nav" aria-label="설정 메뉴">
          {TABS.map((t) => (
            <button
              key={t.key}
              type="button"
              className={`settings-nav-item${tab === t.key ? ' is-active' : ''}`}
              aria-current={tab === t.key ? 'page' : undefined}
              onClick={() => setParams({ tab: t.key }, { replace: true })}
            >
              {t.label}
              {t.soon && <span className="badge settings-badge">준비 중</span>}
            </button>
          ))}
        </nav>

        <div className="settings-panel">
          {tab === 'info' && <InfoSection account={account} />}
          {tab === 'password' && <PasswordSection account={account} />}
          {tab === 'security' && <SecuritySection />}
          {tab === 'blocked' && <BlockedUsers />}
          {tab === 'alarm' && <AlarmSection />}
          {tab === 'support' && <SupportSection />}
          {tab === 'withdraw' && <WithdrawSection />}
        </div>
      </div>
    </div>
  )
}

/** 내 정보: 이메일 · 닉네임 · 가입일 · 연결된 소셜 계정 + 휴대폰 번호 바꾸기(새 번호로 인증번호를 받아 확인해야 바뀐다) */
function InfoSection({ account }) {
  const { user, updateUser } = useAuth()
  const a = account.data
  const [changing, setChanging] = useState(false)
  // 증표는 1회용이라, 제출에 실패하면 인증 칸을 새로 그려 다시 인증받게 한다
  const [phoneKey, setPhoneKey] = useState(0)
  const [phone, setPhone] = useState({ error: '', notice: '' })

  async function onVerified(phoneProof) {
    try {
      updateUser(await userApi.registerPhone(phoneProof))
      setChanging(false)
      setPhone({ error: '', notice: '휴대폰 번호를 바꿨어요.' })
    } catch (err) {
      setPhone({ error: err.message, notice: '' })
      setPhoneKey((k) => k + 1)
    }
  }

  return (
    <section className="card settings-block">
      <h2>내 정보</h2>
      {account.error && <p className="form-error">{account.error}</p>}
      <dl className="settings-facts">
        <div>
          <dt>이메일</dt>
          <dd>
            {user.email}
            <small>로그인 아이디 · 변경 불가</small>
          </dd>
        </div>
        <div>
          <dt>닉네임</dt>
          <dd>{user.nickname}</dd>
        </div>
        <div>
          <dt>가입일</dt>
          <dd>{user.createdAt ? user.createdAt.slice(0, 10).replaceAll('-', '.') : '—'}</dd>
        </div>
        <div>
          <dt>연결된 소셜 계정</dt>
          <dd>
            {!a
              ? '—'
              : a.socialProviders.length === 0
                ? '없음'
                : a.socialProviders.map((p) => SOCIAL_LABEL[p] ?? p).join(' · ')}
          </dd>
        </div>
        <div>
          <dt>휴대폰 번호</dt>
          <dd>
            {user.phone ?? (user.phoneVerified ? '인증 완료' : '아직 인증하지 않았어요')}
            {user.phoneVerified && user.phone && <small>인증 완료</small>}
            {user.phoneVerified && !user.phone && <small>번호를 다시 인증하면 여기에 번호가 보여요</small>}
            {!changing && (
              <button
                type="button"
                className="btn btn-outline btn-sm settings-inline-btn"
                onClick={() => {
                  setChanging(true)
                  setPhone({ error: '', notice: '' })
                }}
              >
                {user.phoneVerified ? '번호 변경' : '인증하기'}
              </button>
            )}
          </dd>
        </div>
      </dl>

      {phone.notice && (
        <p className="settings-notice" role="status">
          {phone.notice}
        </p>
      )}
      {changing && (
        <div className="settings-subform">
          <h3>휴대폰 번호 변경</h3>
          <p className="settings-help">
            새 번호로 인증번호를 받아 확인하면 바뀌어요. 번호는 한 계정에만 쓸 수 있고, 아이디 찾기에 쓰여요.
          </p>
          <PhoneVerification key={phoneKey} onVerified={onVerified} label="새 휴대폰 번호" />
          {phone.error && (
            <p className="form-error" role="alert">
              {phone.error}
            </p>
          )}
          <button type="button" className="btn btn-ghost btn-sm" onClick={() => setChanging(false)}>
            취소
          </button>
        </div>
      )}
    </section>
  )
}

/** 비밀번호 변경 (바꾸면 모든 기기에서 로그아웃되어 다시 로그인한다) */
function PasswordSection({ account }) {
  const { logout } = useAuth()
  const navigate = useNavigate()
  const [form, setForm] = useState({ current: '', next: '', confirm: '' })
  const [errors, setErrors] = useState({})
  const [busy, setBusy] = useState(false)
  const a = account.data

  const onChange = (e) => {
    setForm((f) => ({ ...f, [e.target.name]: e.target.value }))
    setErrors((cur) => ({ ...cur, [e.target.name]: '', form: '' }))
  }

  async function changePassword(e) {
    e.preventDefault()
    const next = {}
    if (!form.current) next.current = '현재 비밀번호를 입력해 주세요.'
    const problem = passwordError(form.next)
    if (problem) next.next = problem
    else if (form.next === form.current) next.next = '지금 쓰는 비밀번호와 다른 비밀번호를 입력해 주세요.'
    if (form.confirm !== form.next) next.confirm = '새 비밀번호와 똑같이 입력해 주세요.'
    if (Object.keys(next).length > 0) {
      setErrors(next)
      return
    }
    setBusy(true)
    setErrors({})
    try {
      await userApi.changePassword(form.current, form.next)
      // 서버가 모든 기기에서 로그아웃시켰으므로 이 화면도 로그아웃하고 새 비밀번호로 다시 로그인하게 한다
      await logout()
      navigate('/login', { replace: true, state: { notice: '비밀번호를 바꿨어요. 새 비밀번호로 다시 로그인해 주세요.' } })
    } catch (err) {
      setErrors({ form: err.message })
      setBusy(false)
    }
  }

  return (
    <section className="card settings-block">
      <h2>비밀번호 변경</h2>
      {!a ? (
        !account.error && <p className="muted">불러오는 중…</p>
      ) : !a.hasPassword ? (
        <p className="settings-help settings-help-last">
          소셜 로그인으로 가입한 계정이라 비밀번호가 없어요. 로그인은 연결된 소셜 계정으로 해 주세요.
        </p>
      ) : (
        <form onSubmit={changePassword} noValidate>
          <p className="settings-help">바꾸면 모든 기기에서 로그아웃되고, 새 비밀번호로 다시 로그인해야 해요.</p>
          <Field
            label="현재 비밀번호"
            name="current"
            type="password"
            autoComplete="current-password"
            value={form.current}
            error={errors.current}
            onChange={onChange}
          />
          <Field
            label="새 비밀번호"
            name="next"
            type="password"
            autoComplete="new-password"
            value={form.next}
            hint={PASSWORD_HINT}
            error={errors.next}
            onChange={onChange}
          />
          <Field
            label="새 비밀번호 확인"
            name="confirm"
            type="password"
            autoComplete="new-password"
            value={form.confirm}
            error={errors.confirm}
            onChange={onChange}
          />
          {errors.form && (
            <p className="form-error" role="alert">
              {errors.form}
            </p>
          )}
          <button type="submit" className="btn btn-dark" disabled={busy}>
            {busy ? '바꾸는 중…' : '비밀번호 변경'}
          </button>
        </form>
      )}
    </section>
  )
}

/**
 * 보안: 자동 로그인 켜기/끄기. 누르는 즉시 저장한다.
 * 켜면 브라우저를 닫았다 열어도 로그인이 유지되고(14일), 끄면 브라우저를 닫을 때 로그아웃된다.
 */
function SecuritySection() {
  const { user, updateUser } = useAuth()
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  async function toggle() {
    setBusy(true)
    setError('')
    try {
      updateUser(await userApi.setAutoLogin(!user.autoLogin))
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <section className="card settings-block">
      <h2>보안</h2>
      <p className="settings-help">로그인 상태를 어떻게 유지할지 정해요.</p>
      {error && <p className="form-error">{error}</p>}
      <ul className="alarm-list">
        <li>
          <span className="alarm-text">
            <strong>자동 로그인</strong>
            <span>
              {user.autoLogin
                ? '브라우저를 닫았다 다시 열어도 로그인이 유지돼요. (마지막으로 쓴 뒤 14일 동안)'
                : '브라우저를 닫으면 로그아웃돼요. 다시 올 때마다 로그인해야 해요.'}
            </span>
          </span>
          <button
            type="button"
            role="switch"
            aria-checked={user.autoLogin}
            aria-label="자동 로그인"
            className={`alarm-switch${user.autoLogin ? ' is-on' : ''}`}
            onClick={toggle}
            disabled={busy}
          >
            <span aria-hidden="true" />
          </button>
        </li>
      </ul>
      <p className="settings-help settings-help-last alarm-note">
        여럿이 같이 쓰는 컴퓨터라면 꺼 두는 게 안전해요. 이 설정은 내 계정으로 로그인하는 모든 기기에 적용돼요.
      </p>
    </section>
  )
}

// 알림 설정 항목 (서버 notification_settings 의 칸과 같다)
const ALARMS = [
  { key: 'verifyReminder', title: '오늘 인증하는 날', body: '진행 중인 챌린지의 인증을 아직 안 했을 때 알려 드려요.' },
  { key: 'challengeResult', title: '챌린지 결과', body: '챌린지가 끝나고 정산되면 알려 드려요.' },
  { key: 'social', title: '팔로우 · 메시지', body: '누군가 나를 팔로우하거나 메시지 요청을 보내면 알려 드려요.' },
]

/** 알림 설정: 종류별 켜기/끄기. 누르는 즉시 저장한다. 끈 종류는 알림함에 쌓이지 않는다 */
function AlarmSection() {
  const [state, setState] = useState({ settings: null, error: '' })
  const [busyKey, setBusyKey] = useState(null)

  useEffect(() => {
    let cancelled = false
    notificationApi
      .settings()
      .then((settings) => !cancelled && setState({ settings, error: '' }))
      .catch((err) => !cancelled && setState({ settings: null, error: err.message }))
    return () => {
      cancelled = true
    }
  }, [])

  async function toggle(key) {
    const next = { ...state.settings, [key]: !state.settings[key] }
    setBusyKey(key)
    try {
      setState({ settings: await notificationApi.updateSettings(next), error: '' })
    } catch (err) {
      setState((s) => ({ ...s, error: err.message }))
    } finally {
      setBusyKey(null)
    }
  }

  return (
    <section className="card settings-block">
      <h2>알림</h2>
      <p className="settings-help">받을 알림을 골라 주세요. 알림은 화면 위쪽의 종 모양에 쌓여요.</p>
      {state.error && <p className="form-error">{state.error}</p>}
      {!state.settings ? (
        !state.error && <p className="muted">불러오는 중…</p>
      ) : (
        <ul className="alarm-list">
          {ALARMS.map((a) => (
            <li key={a.key}>
              <span className="alarm-text">
                <strong>{a.title}</strong>
                <span>{a.body}</span>
              </span>
              <button
                type="button"
                role="switch"
                aria-checked={state.settings[a.key]}
                aria-label={`${a.title} 알림`}
                className={`alarm-switch${state.settings[a.key] ? ' is-on' : ''}`}
                onClick={() => toggle(a.key)}
                disabled={busyKey === a.key}
              >
                <span aria-hidden="true" />
              </button>
            </li>
          ))}
        </ul>
      )}
      <p className="settings-help settings-help-last alarm-note">
        문의 답변처럼 꼭 알아야 하는 알림은 설정과 상관없이 보내 드려요.
      </p>
      <PushToggle />
    </section>
  )
}

const PUSH_NOTE = {
  unsupported: '이 브라우저는 푸시 알림을 지원하지 않아요.',
  unavailable: '지금은 푸시 알림을 쓸 수 없어요.',
  denied: '브라우저에서 이 사이트의 알림이 막혀 있어요. 주소창 옆 사이트 설정에서 알림을 허용해 주세요.',
}

/**
 * 이 브라우저에서 푸시 알림 받기. 위에서 켜 둔 종류의 알림이 화면을 닫아 두어도 브라우저 알림으로 온다.
 * 기기(브라우저)마다 따로 켠다 — 켜면 브라우저가 알림 권한을 묻는다.
 */
function PushToggle() {
  const [status, setStatus] = useState(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    let cancelled = false
    pushStatus()
      .then((s) => !cancelled && setStatus(s))
      .catch(() => !cancelled && setStatus('unavailable'))
    return () => {
      cancelled = true
    }
  }, [])

  async function toggle() {
    setBusy(true)
    setError('')
    try {
      setStatus(await (status === 'on' ? disablePush() : enablePush()))
    } catch (err) {
      setError(err.message || '푸시 알림 설정을 바꾸지 못했어요.')
    } finally {
      setBusy(false)
    }
  }

  const canToggle = status === 'on' || status === 'off'
  return (
    <div className="push-toggle">
      <ul className="alarm-list">
        <li>
          <span className="alarm-text">
            <strong>이 브라우저에서 푸시 알림 받기</strong>
            <span>
              {PUSH_NOTE[status] ?? '갓생살기를 닫아 두어도 위에서 켠 알림을 브라우저 알림으로 받아요. 기기마다 따로 켜요.'}
            </span>
          </span>
          <button
            type="button"
            role="switch"
            aria-checked={status === 'on'}
            aria-label="이 브라우저에서 푸시 알림 받기"
            className={`alarm-switch${status === 'on' ? ' is-on' : ''}`}
            onClick={toggle}
            disabled={busy || !canToggle}
          >
            <span aria-hidden="true" />
          </button>
        </li>
      </ul>
      {error && <p className="form-error">{error}</p>}
    </div>
  )
}

const INQUIRY_CATEGORY = { ACCOUNT: '계정', CHALLENGE: '챌린지 · 인증', POINT: '포인트', BUG: '오류 신고', ETC: '기타' }

// 자주 묻는 질문: 지금 서비스가 실제로 동작하는 대로만 적는다
const FAQ = [
  {
    q: '인증은 언제까지 해야 하나요?',
    a: '하루는 00:00부터 24:00까지예요. 챌린지에 인증 가능 시간이 따로 정해져 있으면 그 시간 안에만 할 수 있어요. 인증은 하루에 한 번이고 올린 뒤에는 바꿀 수 없어요.',
  },
  {
    q: '포인트 챌린지에 건 포인트는 어떻게 돌려받나요?',
    a: '인증한 날의 몫은 그대로 돌려받고, 못 한 날의 몫은 그날 성공한 참가자들에게 보상 포인트로 나눠져요. 결과는 매일 계산되고, 지급은 챌린지가 끝날 때 한 번에 돼요.',
  },
  {
    q: '포인트를 현금으로 바꿀 수 있나요?',
    a: '직접 충전한 포인트 중 쓰지 않은 금액만 결제 취소로 환불받을 수 있어요. 챌린지 보상으로 받은 포인트는 포인트 상점에서만 쓸 수 있고 현금으로 바꿀 수 없어요.',
  },
  {
    q: '챌린지를 중간에 그만둘 수 있나요?',
    a: '시작 전에는 참여를 취소할 수 있고 건 포인트도 돌려받아요. 시작한 뒤에는 포기만 할 수 있고, 포기하면 남은 날은 모두 실패로 처리돼요.',
  },
  {
    q: '모르는 사람에게도 메시지를 보낼 수 있나요?',
    a: '서로 팔로우했거나 같은 챌린지를 한 사이는 바로 대화할 수 있어요. 그 밖의 사람에게는 메시지 요청으로 3개까지 보낼 수 있고, 상대가 수락해야 이어서 보낼 수 있어요.',
  },
  {
    q: '일기는 다른 사람도 볼 수 있나요?',
    a: '아니요. 갓생기록의 일기와 일기 사진은 항상 나만 볼 수 있어요.',
  },
]

/** 고객센터: 자주 묻는 질문 + 1:1 문의 남기기 + 내 문의 내역 */
function SupportSection() {
  const [list, setList] = useState({ items: null, error: '' })
  const [form, setForm] = useState({ category: 'ETC', title: '', content: '' })
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState({ kind: '', text: '' })

  useEffect(() => {
    let cancelled = false
    inquiryApi
      .mine()
      .then((items) => !cancelled && setList({ items, error: '' }))
      .catch((err) => !cancelled && setList({ items: null, error: err.message }))
    return () => {
      cancelled = true
    }
  }, [])

  const onChange = (e) => setForm((f) => ({ ...f, [e.target.name]: e.target.value }))

  async function submit(e) {
    e.preventDefault()
    if (!form.title.trim() || !form.content.trim()) {
      setMessage({ kind: 'error', text: '제목과 내용을 입력해 주세요.' })
      return
    }
    setBusy(true)
    setMessage({ kind: '', text: '' })
    try {
      await inquiryApi.create({ ...form, title: form.title.trim(), content: form.content.trim() })
      setForm({ category: 'ETC', title: '', content: '' })
      setMessage({ kind: 'ok', text: '문의를 남겼어요. 답변이 달리면 아래 내 문의 내역에서 볼 수 있어요.' })
      setList({ items: await inquiryApi.mine(), error: '' })
    } catch (err) {
      setMessage({ kind: 'error', text: err.message })
    } finally {
      setBusy(false)
    }
  }

  return (
    <section className="card settings-block">
      <h2>고객센터</h2>
      <p className="settings-help">자주 묻는 질문을 먼저 확인해 보고, 해결되지 않으면 1:1 문의를 남겨 주세요.</p>

      <h3 className="settings-sub">자주 묻는 질문</h3>
      <div className="faq">
        {FAQ.map((f) => (
          <details key={f.q}>
            <summary>{f.q}</summary>
            <p>{f.a}</p>
          </details>
        ))}
      </div>

      <h3 className="settings-sub">1:1 문의하기</h3>
      <form onSubmit={submit} noValidate>
        <div className="field">
          <label htmlFor="inquiry-category">문의 종류</label>
          <select id="inquiry-category" name="category" value={form.category} onChange={onChange}>
            {Object.entries(INQUIRY_CATEGORY).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>
        </div>
        <Field label="제목" name="title" value={form.title} maxLength={100} onChange={onChange} />
        <div className="field">
          <label htmlFor="inquiry-content">내용</label>
          <textarea
            id="inquiry-content"
            name="content"
            value={form.content}
            rows={5}
            maxLength={2000}
            placeholder="어떤 화면에서 무엇을 하다가 생긴 일인지 적어 주시면 더 빨리 도와드릴 수 있어요."
            onChange={onChange}
          />
          <p className="field-hint">{form.content.length}/2000</p>
        </div>
        {message.text && (
          <p className={message.kind === 'error' ? 'form-error' : 'settings-notice'} role="status">
            {message.text}
          </p>
        )}
        <button type="submit" className="btn btn-dark" disabled={busy}>
          {busy ? '보내는 중…' : '문의 남기기'}
        </button>
      </form>

      <h3 className="settings-sub">내 문의 내역</h3>
      {list.error && <p className="form-error">{list.error}</p>}
      {!list.items ? (
        !list.error && <p className="muted">불러오는 중…</p>
      ) : list.items.length === 0 ? (
        <p className="muted">아직 남긴 문의가 없어요.</p>
      ) : (
        <ul className="inquiry-list">
          {list.items.map((q) => (
            <li key={q.id}>
              <div className="inquiry-head">
                <span className={`inquiry-status${q.status === 'ANSWERED' ? ' is-answered' : ''}`}>
                  {q.status === 'ANSWERED' ? '답변 완료' : '답변 대기'}
                </span>
                <strong>{q.title}</strong>
                <small>
                  {INQUIRY_CATEGORY[q.category]} · {q.createdAt.slice(0, 10).replaceAll('-', '.')}
                </small>
              </div>
              <p className="inquiry-content">{q.content}</p>
              {q.answer && (
                <p className="inquiry-answer">
                  <b>답변</b>
                  {q.answer}
                </p>
              )}
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}

/** 회원 탈퇴: 탈퇴할 수 있는지 확인 → 본인 확인(비밀번호 또는 확인 문구) → '그래도 탈퇴하시겠습니까?' 경고 → 탈퇴. 되돌릴 수 없다 */
function WithdrawSection() {
  const { logout } = useAuth()
  const navigate = useNavigate()
  const [state, setState] = useState({ check: null, error: '' })
  const [value, setValue] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  // 본인 확인을 통과하면 마지막 경고 창을 띄운다
  const [warning, setWarning] = useState(false)

  useEffect(() => {
    let cancelled = false
    userApi
      .withdrawalCheck()
      .then((check) => !cancelled && setState({ check, error: '' }))
      .catch((err) => !cancelled && setState({ check: null, error: err.message }))
    return () => {
      cancelled = true
    }
  }, [])

  const check = state.check

  const body = () => (check.hasPassword ? { password: value } : { confirm: value })

  // 1단계: 비밀번호(또는 확인 문구)가 맞는지 먼저 확인하고, 맞으면 마지막 경고를 띄운다
  async function verify(e) {
    e.preventDefault()
    setBusy(true)
    setError('')
    try {
      await userApi.verifyWithdrawal(body())
      setWarning(true)
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  // 2단계: 경고 창에서 [탈퇴하기]를 눌렀을 때만 실제로 탈퇴한다
  async function withdraw() {
    setBusy(true)
    setError('')
    try {
      await userApi.withdraw(body())
      await logout()
      navigate('/', { replace: true })
    } catch (err) {
      setWarning(false)
      setError(err.message)
      setBusy(false)
    }
  }

  return (
    <section className="card settings-block">
      <h2>회원 탈퇴</h2>
      {state.error && <p className="form-error">{state.error}</p>}
      {!check ? (
        !state.error && <p className="muted">불러오는 중…</p>
      ) : (
        <>
          <ul className="withdraw-notes">
            <li>이메일 · 닉네임 · 휴대폰 번호 · 프로필 사진 · 한 줄 소개 · 일기 · 팔로우가 모두 지워져요.</li>
            <li>챌린지 참여 · 인증 · 채팅 기록은 다른 참가자의 기록과 이어져 있어 '탈퇴한회원' 이름으로 남아요.</li>
            <li>
              보상 포인트는 현금으로 바꿀 수 없어서 그대로 사라져요.
              {check.rewardBalance > 0 && <b> 지금 {check.rewardBalance.toLocaleString()}P가 사라져요.</b>}
            </li>
            <li>탈퇴한 뒤에는 되돌릴 수 없어요.</li>
          </ul>

          {!check.canWithdraw ? (
            <div className="withdraw-blocked">
              <strong>지금은 탈퇴할 수 없어요</strong>
              <ul>
                {check.blockers.map((b) => (
                  <li key={b}>{b}</li>
                ))}
              </ul>
            </div>
          ) : (
            <form onSubmit={verify} noValidate>
              <Field
                label={check.hasPassword ? '비밀번호 확인' : "확인을 위해 '탈퇴'라고 입력해 주세요"}
                name="confirm"
                type={check.hasPassword ? 'password' : 'text'}
                autoComplete={check.hasPassword ? 'current-password' : 'off'}
                value={value}
                onChange={(e) => {
                  setValue(e.target.value)
                  setError('')
                }}
              />
              {error && (
                <p className="form-error" role="alert">
                  {error}
                </p>
              )}
              <button
                type="submit"
                className="btn btn-danger"
                disabled={busy || (check.hasPassword ? !value : value.trim() !== '탈퇴')}
              >
                {busy && !warning ? '확인하는 중…' : '회원 탈퇴'}
              </button>
            </form>
          )}
        </>
      )}

      {warning && (
        <div className="fl-back" onClick={() => !busy && setWarning(false)}>
          <div
            className="fl-pop wd-pop"
            role="alertdialog"
            aria-modal="true"
            aria-labelledby="wd-title"
            onClick={(e) => e.stopPropagation()}
          >
            <span className="wd-icon" aria-hidden="true">
              !
            </span>
            <h3 id="wd-title">그래도 탈퇴하시겠습니까?</h3>
            <p>
              탈퇴하면 계정과 개인정보, 일기, 팔로우가 모두 지워지고
              {check.rewardBalance > 0 && ` 보상 포인트 ${check.rewardBalance.toLocaleString()}P도 사라지며`} 다시
              되돌릴 수 없어요.
            </p>
            <div className="wd-actions">
              <button type="button" className="btn btn-outline" onClick={() => setWarning(false)} disabled={busy}>
                취소
              </button>
              <button type="button" className="btn btn-danger" onClick={withdraw} disabled={busy}>
                {busy ? '탈퇴하는 중…' : '탈퇴하기'}
              </button>
            </div>
          </div>
        </div>
      )}
    </section>
  )
}

/** 차단한 사용자. 풀면 그 사람의 채팅이 다시 보인다. */
function BlockedUsers() {
  const [users, setUsers] = useState(null)
  const [error, setError] = useState('')

  useEffect(() => {
    blockApi
      .list()
      .then(setUsers)
      .catch((err) => setError(err.message))
  }, [])

  async function unblock(userId) {
    try {
      await blockApi.unblock(userId)
      setUsers((cur) => cur.filter((u) => u.userId !== userId))
    } catch (err) {
      setError(err.message)
    }
  }

  return (
    <section className="card settings-block">
      <h2>차단한 사용자</h2>
      <p className="settings-help">차단한 사람의 오픈채팅 메시지는 내 화면에서만 보이지 않아요. 상대에게는 알리지 않아요.</p>
      {error && <p className="form-error">{error}</p>}
      {users === null ? (
        <p className="muted">불러오는 중…</p>
      ) : users.length === 0 ? (
        <p className="muted">차단한 사용자가 없어요.</p>
      ) : (
        <ul className="blocked-list">
          {users.map((u) => (
            <li key={u.userId}>
              <Avatar src={u.profileImageUrl} size={32} />
              <span className="blocked-name">{u.nickname}</span>
              <button type="button" className="btn btn-outline btn-sm" onClick={() => unblock(u.userId)}>
                차단 해제
              </button>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}
