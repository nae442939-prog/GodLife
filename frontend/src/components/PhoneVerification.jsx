import { useEffect, useId, useState } from 'react'
import { phoneApi } from '../api/client.js'

const PHONE_RE = /^01[016789]-?\d{3,4}-?\d{4}$/
// 서버의 재전송 간격(PhoneVerificationService.RESEND_COOLDOWN)과 같다.
const RESEND_SECONDS = 10

/**
 * 휴대폰 번호 입력 → 인증번호 받기 → 6자리 확인. 성공하면 onVerified(phoneProof) 를 부른다.
 * 증표는 1회용이라, 부모가 제출에 실패하면 key 를 바꿔 이 컴포넌트를 새로 그려 다시 인증받게 한다.
 */
export function PhoneVerification({ onVerified, label = '휴대폰 번호' }) {
  const phoneId = useId()
  const codeId = useId()
  const [phone, setPhone] = useState('')
  const [code, setCode] = useState('')
  const [sentTo, setSentTo] = useState('')
  const [verified, setVerified] = useState(false)
  const [cooldown, setCooldown] = useState(0)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [info, setInfo] = useState('')
  // 데모 모드(서버가 실제 문자를 보내지 않음)면 서버가 인증번호를 돌려준다.
  const [demoCode, setDemoCode] = useState('')

  useEffect(() => {
    if (cooldown <= 0) return undefined
    const t = setTimeout(() => setCooldown((c) => c - 1), 1000)
    return () => clearTimeout(t)
  }, [cooldown])

  async function send() {
    setError('')
    if (!PHONE_RE.test(phone.trim())) {
      setError('휴대폰 번호 형식이 올바르지 않습니다. (예: 010-1234-5678)')
      return
    }
    setBusy(true)
    try {
      const res = await phoneApi.send(phone.trim())
      setSentTo(phone.trim())
      setCode('')
      setCooldown(RESEND_SECONDS)
      setDemoCode(res?.demoCode ?? '')
      setInfo(res?.demoCode ? '' : '인증번호를 보냈습니다. 3분 안에 입력해 주세요.')
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  async function confirm() {
    setError('')
    if (!/^\d{6}$/.test(code)) {
      setError('인증번호 6자리를 입력해 주세요.')
      return
    }
    setBusy(true)
    try {
      const { phoneProof } = await phoneApi.confirm(sentTo, code)
      setVerified(true)
      setInfo('')
      onVerified(phoneProof)
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  if (verified) {
    return (
      <div className="field">
        <span className="field-label">{label}</span>
        <p className="verified-badge">✓ {sentTo} 인증 완료</p>
      </div>
    )
  }

  return (
    <div className="field">
      <label htmlFor={phoneId}>{label}</label>
      <div className="inline-row">
        <input
          id={phoneId}
          type="tel"
          inputMode="numeric"
          autoComplete="tel"
          placeholder="010-1234-5678"
          value={phone}
          onChange={(e) => setPhone(e.target.value)}
        />
        <button type="button" className="btn btn-outline" onClick={send} disabled={busy || cooldown > 0}>
          {cooldown > 0 ? `재전송 ${cooldown}초` : sentTo ? '다시 받기' : '인증번호 받기'}
        </button>
      </div>

      {demoCode && <DemoCodeNotice code={demoCode} channel="문자는" />}

      {sentTo && (
        <>
          <label htmlFor={codeId} className="sr-only">
            인증번호
          </label>
          <div className="inline-row inline-row-gap">
            <input
              id={codeId}
              inputMode="numeric"
              autoComplete="one-time-code"
              maxLength={6}
              placeholder="인증번호 6자리"
              value={code}
              onChange={(e) => setCode(e.target.value.replace(/\D/g, ''))}
            />
            <button type="button" className="btn btn-primary" onClick={confirm} disabled={busy || code.length !== 6}>
              확인
            </button>
          </div>
        </>
      )}

      {error ? (
        <p className="field-error" role="alert">
          {error}
        </p>
      ) : info ? (
        <p className="field-hint">{info}</p>
      ) : null}
    </div>
  )
}

/** 데모 모드 안내 + 인증번호. 실제 발송 모드에서는 서버가 번호를 주지 않으므로 나타나지 않는다. */
export function DemoCodeNotice({ code, channel }) {
  return (
    <div className="demo-code" role="status">
      <span className="demo-code-tag">데모 모드</span>
      <span>
        실제 {channel} 발송되지 않아요. 인증번호: <strong>{code}</strong>
      </span>
    </div>
  )
}
