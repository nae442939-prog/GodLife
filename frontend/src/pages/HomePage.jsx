import { Link } from 'react-router-dom'
import { useAuth } from '../auth/useAuth.js'

const STEPS = [
  {
    title: '챌린지에 참여',
    body: '운동, 공부, 독서처럼 하고 싶은 습관을 골라 참여해요. 포인트를 걸지 않는 무료 모드도 있어요.',
  },
  {
    title: '사진으로 인증',
    body: '정해진 기간 동안 그날의 인증 사진을 카메라로 직접 찍어 올려요. 사진이 챌린지와 맞는지 자동으로 확인해요.',
  },
  {
    title: '성공하면 돌려받아요',
    body: '목표를 채우면 건 포인트를 돌려받고, 중간에 포기한 참가자의 포인트를 성공한 사람들이 나눠 가져요.',
  },
]

const FEATURES = [
  { title: '직접 학습한 AI 인증', body: '외부 AI 서비스가 아니라, 직접 파인튜닝한 이미지 분류 모델이 인증 사진을 확인해요.' },
  { title: '실시간 랭킹', body: '같은 챌린지 참가자끼리 인증 횟수와 연속 달성 기록을 겨뤄요.' },
  { title: '안전한 포인트', body: '포인트는 서비스 안에서만 쓰여요. 현금 출금·환전이 없는 닫힌 구조예요.' },
]

export function HomePage() {
  const { user, status } = useAuth()
  const authed = status === 'authed'

  return (
    <>
      <section className="hero">
        <div className="container hero-inner">
          <p className="eyebrow">습관 챌린지 서비스</p>
          <h1>
            오늘의 습관에 도전하고,
            <br />
            성공하면 돌려받으세요
          </h1>
          <p className="hero-sub">
            {authed
              ? `${user.nickname}님, 오늘도 한 걸음 나아가 볼까요?`
              : '함께 도전하고 사진으로 인증하는 갓생 챌린지, 지금 시작해 보세요.'}
          </p>
          <div className="hero-actions">
            {status === 'loading' ? null : authed ? (
              <Link to="/me" className="btn btn-primary">
                마이페이지
              </Link>
            ) : (
              <>
                <Link to="/signup" className="btn btn-primary">
                  무료로 시작하기
                </Link>
                <Link to="/login" className="btn btn-outline">
                  로그인
                </Link>
              </>
            )}
          </div>
        </div>
      </section>

      <section className="section">
        <div className="container">
          <h2>이렇게 진행돼요</h2>
          <ol className="steps">
            {STEPS.map((step, i) => (
              <li key={step.title} className="step">
                <span className="step-num" aria-hidden="true">
                  {i + 1}
                </span>
                <h3>{step.title}</h3>
                <p>{step.body}</p>
              </li>
            ))}
          </ol>
        </div>
      </section>

      <section className="section section-alt">
        <div className="container">
          <h2>갓생살기의 특징</h2>
          <ul className="features">
            {FEATURES.map((f) => (
              <li key={f.title} className="feature">
                <h3>{f.title}</h3>
                <p>{f.body}</p>
              </li>
            ))}
          </ul>
        </div>
      </section>
    </>
  )
}
