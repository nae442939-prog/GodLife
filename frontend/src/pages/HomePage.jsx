import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { challengeApi } from '../api/client.js'
import { useAuth } from '../auth/useAuth.js'
import { ChallengeCard } from '../challenge/ChallengeCard.jsx'
import { HomeRanking } from '../ranking/HomeRanking.jsx'

// body 의 \n 은 문장 단위 줄바꿈이다. (.step-body p 가 white-space: pre-line)
const STEPS = [
  {
    title: '챌린지에 참여',
    body: '운동, 공부, 독서처럼 하고 싶은 습관을 골라 참여해요.\n포인트를 걸지 않는 무료 모드도 있어요.',
  },
  {
    title: '사진으로 인증',
    body: '정해진 기간 동안 그날의 인증 사진을 카메라로 직접 찍어 올려요.\nAI가 사진 속 상황이 챌린지와 맞는지 정확하게 판독해요.',
  },
  {
    title: '성공하면 환급받아요',
    body: '목표를 채우면 건 포인트를 그대로 환급받아요.\n내가 낸 만큼만 정확히 돌려받는 안전한 구조예요.',
  },
  {
    title: '포인트 상점에서 사용해요',
    body: '중간에 포기한 참가자의 포인트는 성공한 사람들에게 보상 포인트로 나눠져요.\n보상 포인트는 포인트 상점에서 헬스장 이용권, 도서 상품권 같은 갓생 리워드로 교환할 수 있어요.',
  },
]

const FEATURES = [
  {
    title: '직접 학습한 AI 인증',
    body: '외부 AI 서비스가 아니라, 직접 파인튜닝한 이미지 분류 모델이 인증 사진을 확인해요.',
  },
  { title: '실시간 랭킹', body: '같은 챌린지 참가자끼리 인증 횟수와 연속 달성 기록을 겨뤄요.' },
  {
    title: '안전한 포인트',
    body: '포인트는 챌린지 참여와 포인트 상점에서만 쓰여요. 직접 충전하고 쓰지 않은 금액만 결제 취소로 환불되고, 챌린지·이벤트로 얻은 포인트는 현금화할 수 없어요.',
  },
]

// 아래 목록(HERO_CHALLENGE / RANKING / COMMUNITY)은 첫 화면 장식·커뮤니티용 목업 데이터다.
// ('실시간 랭킹' 구역은 실제 랭킹 API 를 시상대로 보여 준다. RANKING 은 첫 화면 오른쪽 장식 카드에만 쓴다)
// HERO_CHALLENGE 는 첫 화면 오른쪽 장식 그림용. ('지금 모집 중인 챌린지' 칸은 실제 API 를 쓴다)
const HERO_CHALLENGE = { category: '운동', title: '아침 6시 기상 러닝', participants: 32, points: 10000 }

const RANKING = [
  { rank: 1, nickname: '갓생러123', streak: 28, successRate: 96 },
  { rank: 2, nickname: '오늘도완주', streak: 24, successRate: 93 },
  { rank: 3, nickname: '습관마스터', streak: 21, successRate: 90 },
]

const COMMUNITY = [
  { title: '러닝 챌린지 3주차, 확실히 아침에 덜 피곤해요', author: '갓생러123', comments: 12, likes: 34 },
  { title: '인증샷 팁 - 이렇게 찍으면 한 번에 통과돼요', author: '오늘도완주', comments: 8, likes: 21 },
  { title: '포기하고 싶을 때 저는 이렇게 버텨요', author: '습관마스터', comments: 15, likes: 40 },
]

const HOME_CHALLENGE_COUNT = 3

export function HomePage() {
  const { user, status } = useAuth()
  const authed = status === 'authed'
  // 모집 중인 공개 챌린지 중 참가자 많은 순 3개 (누르면 상세로). 실패하면 빈 목록으로 둔다.
  const [popular, setPopular] = useState(null)

  useEffect(() => {
    let cancelled = false
    challengeApi
      .list({ sort: 'popular' })
      .then((data) => !cancelled && setPopular(data.items.slice(0, HOME_CHALLENGE_COUNT)))
      .catch(() => !cancelled && setPopular([]))
    return () => {
      cancelled = true
    }
  }, [])

  return (
    <>
      <section className="hero">
        <div className="container hero-split-inner">
          <div className="hero-copy">
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
            <p className="hero-stat">누적 참가자 1,240명 · 이번 주 인증 3,410회</p>
          </div>

          <div className="hero-preview" aria-hidden="true">
            <div className="hero-preview-bg" />
            <div className="hero-preview-card hero-preview-card-challenge">
              <span className="challenge-category">{HERO_CHALLENGE.category}</span>
              <h3>{HERO_CHALLENGE.title}</h3>
              <div className="hero-preview-meta">
                <span>{HERO_CHALLENGE.participants}명 참여</span>
                <span>{HERO_CHALLENGE.points.toLocaleString()}P</span>
              </div>
            </div>
            <div className="hero-preview-card hero-preview-card-ranking">
              <p className="hero-preview-label">실시간 랭킹</p>
              {RANKING.slice(0, 2).map((r, i) => (
                <div key={r.rank} className="hero-preview-rank-row">
                  <span className={`hero-preview-rank-num${i === 0 ? ' is-first' : ''}`}>{r.rank}</span>
                  <span className="hero-preview-rank-name">{r.nickname}</span>
                  <span className="hero-preview-rank-detail">연속 {r.streak}일</span>
                </div>
              ))}
            </div>
          </div>
        </div>
      </section>

      <section className="section section-alt">
        <div className="container">
          <div className="section-head">
            <h2>지금 모집 중인 챌린지</h2>
            <Link to="/challenges" className="section-more">
              전체 보기 →
            </Link>
          </div>
          {popular === null ? (
            <p className="muted">불러오는 중…</p>
          ) : popular.length === 0 ? (
            <div className="home-empty">
              <p>지금 모집 중인 챌린지가 없어요.</p>
              <Link to="/challenges/new" className="btn btn-dark-outline">
                첫 챌린지 만들기
              </Link>
            </div>
          ) : (
            <ul className="cl-grid">
              {popular.map((c) => (
                <ChallengeCard key={c.id} challenge={c} />
              ))}
            </ul>
          )}
        </div>
      </section>

      <section className="section">
        <div className="container">
          <h2>이렇게 진행돼요</h2>
          <ol className="steps">
            {STEPS.map((step, i) => (
              <li key={step.title} className={`step${i % 2 === 1 ? ' is-reverse' : ''}`}>
                <div className="step-num" aria-hidden="true">
                  <span>{i + 1}</span>
                </div>
                <div className="step-body">
                  <h3>{step.title}</h3>
                  <p>{step.body}</p>
                </div>
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

      <section className="section">
        <div className="container">
          <div className="section-head">
            <h2>실시간 랭킹</h2>
            <Link to="/rankings?tab=users" className="section-more">
              전체 보기 →
            </Link>
          </div>
          <HomeRanking />
        </div>
      </section>

      <section className="section section-alt">
        <div className="container">
          <div className="section-head">
            <h2>커뮤니티 인기글</h2>
            <span className="badge">준비 중</span>
          </div>
          <ul className="community-list">
            {COMMUNITY.map((p) => (
              <li key={p.title} className="community-row">
                <div className="community-main">
                  <h3>{p.title}</h3>
                  <p>{p.author}</p>
                </div>
                <div className="community-stats">
                  <span>댓글 {p.comments}</span>
                  <span>좋아요 {p.likes}</span>
                </div>
              </li>
            ))}
          </ul>
        </div>
      </section>

      {!authed && status !== 'loading' && (
        <section className="cta-band">
          <div className="container cta-band-inner">
            <h2>오늘부터, 갓생 시작해볼까요?</h2>
            <p>지금 회원가입하고 첫 번째 챌린지에 도전해보세요.</p>
            <Link to="/signup" className="btn btn-primary">
              무료로 시작하기
            </Link>
          </div>
        </section>
      )}
    </>
  )
}
