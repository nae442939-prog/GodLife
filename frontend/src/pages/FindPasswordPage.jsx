import { useState } from 'react'
import { Link } from 'react-router-dom'
import { accountApi } from '../api/client.js'
import { PASSWORD_HINT, passwordError } from '../auth/rules.js'
import { Field } from '../components/Field.jsx'
import { DemoCodeNotice } from '../components/PhoneVerification.jsx'

// 이메일 → 메일로 받은 인증번호 → 새 비밀번호 → 완료
export function FindPasswordPage() {
  const [step, setStep] = useState('email')
  const [email, setEmail] = useState('')
  const [code, setCode] = useState('')
  const [resetToken, setResetToken] = useState('')
  const [password, setPassword] = useState('')
  const [passwordCheck, setPasswordCheck] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [demoCode, setDemoCode] = useState('')

  async function run(action) {
    setError('')
    setBusy(true)
    try {
      await action()
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  const onRequest = (e) => {
    e.preventDefault()
    run(async () => {
      const res = await accountApi.requestPasswordReset(email.trim())
      setDemoCode(res?.demoCode ?? '')
      setCode('')
      setStep('code')
    })
  }

  const onConfirm = (e) => {
    e.preventDefault()
    run(async () => {
      const data = await accountApi.confirmPasswordReset(email.trim(), code)
      setResetToken(data.resetToken)
      setStep('password')
    })
  }

  const onComplete = (e) => {
    e.preventDefault()
    const pw = passwordError(password)
    if (pw) {
      setError(pw)
      return
    }
    if (password !== passwordCheck) {
      setError('비밀번호가 서로 다릅니다.')
      return
    }
    run(async () => {
      try {
        await accountApi.completePasswordReset(resetToken, password)
        setStep('done')
      } catch (err) {
        // 재설정 시간이 지났으면 처음 단계로 돌려보낸다.
        if (err.code === 'INVALID_RESET_TOKEN') setStep('email')
        throw err
      }
    })
  }

  return (
    <div className="container auth-wrap">
      <section className="card auth-card">
        <h1>비밀번호 찾기</h1>

        {step === 'email' && (
          <form onSubmit={onRequest} noValidate>
            <p className="sub">가입한 이메일로 인증번호를 보내 드려요.</p>
            <Field
              label="이메일"
              type="email"
              autoComplete="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
            />
            <ErrorLine error={error} />
            <button type="submit" className="btn btn-primary btn-block" disabled={busy || !email.trim()}>
              인증번호 받기
            </button>
          </form>
        )}

        {step === 'code' && (
          <form onSubmit={onConfirm} noValidate>
            <p className="sub">
              <strong>{email.trim()}</strong> 으로 가입된 계정이 있다면 인증번호를 보냈어요. 10분 안에 입력해 주세요.
            </p>
            {demoCode && <DemoCodeNotice code={demoCode} channel="메일은" />}
            <Field
              label="인증번호"
              inputMode="numeric"
              autoComplete="one-time-code"
              maxLength={6}
              placeholder="6자리"
              value={code}
              onChange={(e) => setCode(e.target.value.replace(/\D/g, ''))}
              hint="소셜 로그인으로만 가입한 계정은 비밀번호가 없어 메일이 가지 않아요."
            />
            <ErrorLine error={error} />
            <button type="submit" className="btn btn-primary btn-block" disabled={busy || code.length !== 6}>
              확인
            </button>
            <button type="button" className="btn btn-ghost btn-block" onClick={() => setStep('email')}>
              이메일 다시 입력 / 다시 받기
            </button>
          </form>
        )}

        {step === 'password' && (
          <form onSubmit={onComplete} noValidate>
            <p className="sub">새 비밀번호를 정해 주세요. 바꾸면 모든 기기에서 로그아웃돼요.</p>
            <Field
              label="새 비밀번호"
              type="password"
              autoComplete="new-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              hint={PASSWORD_HINT}
            />
            <Field
              label="새 비밀번호 확인"
              type="password"
              autoComplete="new-password"
              value={passwordCheck}
              onChange={(e) => setPasswordCheck(e.target.value)}
            />
            <ErrorLine error={error} />
            <button type="submit" className="btn btn-primary btn-block" disabled={busy || !password || !passwordCheck}>
              비밀번호 변경
            </button>
          </form>
        )}

        {step === 'done' && (
          <div className="result-box">
            <p className="result-main">
              <strong>비밀번호를 바꿨어요.</strong>
            </p>
            <p className="field-hint">새 비밀번호로 다시 로그인해 주세요.</p>
            <Link to="/login" className="btn btn-primary btn-block">
              로그인하러 가기
            </Link>
          </div>
        )}

        {step !== 'done' && (
          <p className="switch">
            <Link to="/login">로그인</Link> · <Link to="/find-id">아이디 찾기</Link>
          </p>
        )}
      </section>
    </div>
  )
}

function ErrorLine({ error }) {
  if (!error) return null
  return (
    <p className="form-error" role="alert">
      {error}
    </p>
  )
}
