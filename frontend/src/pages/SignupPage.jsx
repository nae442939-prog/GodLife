import { useState } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { accountApi } from '../api/client.js'
import { NICKNAME_HINT, PASSWORD_HINT, nicknameError, passwordError } from '../auth/rules.js'
import { useAuth } from '../auth/useAuth.js'
import { AccountSummary } from '../components/AccountSummary.jsx'
import { Field } from '../components/Field.jsx'
import { PhoneVerification } from '../components/PhoneVerification.jsx'
import { SocialLoginButtons } from '../components/SocialLoginButtons.jsx'

function validate({ email, password, passwordConfirm, nickname }) {
  const errors = {}
  if (!/^\S+@\S+\.\S+$/.test(email)) errors.email = '이메일 형식이 올바르지 않습니다.'
  const pw = passwordError(password)
  if (pw) errors.password = pw
  if (!passwordConfirm) errors.passwordConfirm = '비밀번호를 한 번 더 입력해 주세요.'
  else if (passwordConfirm !== password) errors.passwordConfirm = '비밀번호가 서로 달라요.'
  const nick = nicknameError(nickname)
  if (nick) errors.nickname = nick
  return errors
}

export function SignupPage() {
  const { status, signup, login } = useAuth()
  const navigate = useNavigate()

  // passwordConfirm 은 화면에서만 확인하고 서버로 보내지 않는다
  const [form, setForm] = useState({ email: '', password: '', passwordConfirm: '', nickname: '' })
  // 휴대폰 인증 증표(1회용). 가입이 증표 문제로 실패하면 phoneKey 를 바꿔 인증 칸을 새로 그린다.
  const [phoneProof, setPhoneProof] = useState('')
  const [phoneKey, setPhoneKey] = useState(0)
  const [errors, setErrors] = useState({})
  const [formError, setFormError] = useState('')
  const [submitting, setSubmitting] = useState(false)
  // 인증한 번호로 이미 가입된 계정 (있으면 가입 대신 그 계정으로 로그인하도록 안내한다)
  const [existingAccount, setExistingAccount] = useState(null)

  if (status === 'authed') return <Navigate to="/" replace />

  const onChange = (e) => {
    const { name, value } = e.target
    const next = { ...form, [name]: value }
    setForm(next)
    // 닉네임은 쓰는 즉시 특수문자/공백 등을 알려 준다. (길이 부족은 제출할 때만)
    const live = name === 'nickname' ? nicknameError(value, { checkLength: false }) : ''
    // 비밀번호 재확인도 쓰는 즉시: 재확인 칸을 채운 뒤 두 값이 다르면 바로 알려 준다
    const mismatch =
      (name === 'password' || name === 'passwordConfirm') &&
      next.passwordConfirm &&
      next.passwordConfirm !== next.password
        ? '비밀번호가 서로 달라요.'
        : undefined
    setErrors((prev) => ({
      ...prev,
      [name]: live || undefined,
      ...(name === 'password' || name === 'passwordConfirm' ? { passwordConfirm: mismatch } : {}),
    }))
  }

  function resetPhone(message) {
    setFormError(message)
    setPhoneProof('')
    setPhoneKey((k) => k + 1)
  }

  // 가입이 실패하면 증표는 쓰이지 않은 채 남으므로, 같은 증표로 기존 계정을 조회할 수 있다.
  async function showExistingAccount() {
    try {
      setExistingAccount(await accountApi.findId(phoneProof))
    } catch {
      resetPhone('이미 다른 계정에 등록된 휴대폰 번호입니다.')
    }
  }

  async function onSubmit(e) {
    e.preventDefault()
    setFormError('')
    const { passwordConfirm, ...fields } = form
    const payload = { ...fields, email: form.email.trim() }
    const clientErrors = validate({ ...payload, passwordConfirm })
    if (Object.keys(clientErrors).length > 0) {
      setErrors(clientErrors)
      return
    }
    if (!phoneProof) {
      setFormError('휴대폰 인증을 완료해 주세요.')
      return
    }
    setSubmitting(true)
    try {
      await signup({ ...payload, phoneProof })
      await login({ email: payload.email, password: payload.password })
      navigate('/', { replace: true })
    } catch (err) {
      if (err.code === 'DUPLICATE_EMAIL') setErrors({ email: err.message })
      else if (err.code === 'DUPLICATE_NICKNAME') setErrors({ nickname: err.message })
      else if (err.code === 'PHONE_ALREADY_REGISTERED') await showExistingAccount()
      else if (err.code === 'PHONE_VERIFICATION_REQUIRED') resetPhone(err.message)
      else if (Object.keys(err.fieldErrors ?? {}).length > 0) setErrors(err.fieldErrors)
      else setFormError(err.message)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="container auth-wrap">
      <section className="card auth-card">
        <h1>회원가입</h1>
        {existingAccount ? (
          <>
            <p className="sub">이 휴대폰 번호로 이미 가입된 계정이 있어요. 기존 계정으로 로그인해 주세요.</p>
            <AccountSummary account={existingAccount} />
          </>
        ) : (
          <>
            <p className="sub">갓생살기와 함께 습관을 만들어 보세요.</p>
            <form onSubmit={onSubmit} noValidate>
              <Field
                label="이메일"
                name="email"
                type="email"
                autoComplete="email"
                value={form.email}
                onChange={onChange}
                error={errors.email}
              />
              <Field
                label="비밀번호"
                name="password"
                type="password"
                autoComplete="new-password"
                value={form.password}
                onChange={onChange}
                error={errors.password}
                hint={PASSWORD_HINT}
              />
              <Field
                label="비밀번호 재확인"
                name="passwordConfirm"
                type="password"
                autoComplete="new-password"
                value={form.passwordConfirm}
                onChange={onChange}
                error={errors.passwordConfirm}
              />
              <Field
                label="닉네임"
                name="nickname"
                type="text"
                autoComplete="nickname"
                value={form.nickname}
                onChange={onChange}
                error={errors.nickname}
                hint={NICKNAME_HINT}
              />
              <PhoneVerification key={phoneKey} onVerified={setPhoneProof} label="휴대폰 본인인증" />
              {formError && (
                <p className="form-error" role="alert">
                  {formError}
                </p>
              )}
              <button type="submit" className="btn btn-primary btn-block" disabled={submitting}>
                {submitting ? '가입 중…' : '가입하기'}
              </button>
            </form>
            <div className="divider">SNS 계정으로 시작하기</div>
            <SocialLoginButtons />
          </>
        )}
        <p className="switch">
          이미 계정이 있으신가요? <Link to="/login">로그인</Link>
        </p>
      </section>
    </div>
  )
}
