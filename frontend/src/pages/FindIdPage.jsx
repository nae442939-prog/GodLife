import { useState } from 'react'
import { Link } from 'react-router-dom'
import { accountApi } from '../api/client.js'
import { AccountSummary } from '../components/AccountSummary.jsx'
import { PhoneVerification } from '../components/PhoneVerification.jsx'

export function FindIdPage() {
  const [result, setResult] = useState(null)
  const [error, setError] = useState('')
  const [phoneKey, setPhoneKey] = useState(0)

  async function onVerified(phoneProof) {
    setError('')
    try {
      setResult(await accountApi.findId(phoneProof))
    } catch (err) {
      setError(err.message)
      setPhoneKey((k) => k + 1)
    }
  }

  return (
    <div className="container auth-wrap">
      <section className="card auth-card">
        <h1>아이디 찾기</h1>
        {result ? (
          <>
            <p className="sub">이 번호로 가입된 계정이에요.</p>
            <AccountSummary account={result} />
          </>
        ) : (
          <>
            <p className="sub">가입할 때 인증한 휴대폰 번호로 찾을 수 있어요.</p>
            <PhoneVerification key={phoneKey} onVerified={onVerified} />
            {error && (
              <p className="form-error" role="alert">
                {error}
              </p>
            )}
          </>
        )}
        <p className="switch">
          <Link to="/login">로그인</Link> · <Link to="/find-password">비밀번호 찾기</Link>
        </p>
      </section>
    </div>
  )
}
