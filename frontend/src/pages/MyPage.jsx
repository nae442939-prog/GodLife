import { Link } from 'react-router-dom'
import { useAuth } from '../auth/useAuth.js'

// 마이페이지는 내 챌린지 / 포인트 지갑 / 랭킹으로 들어가는 허브다.
// 각 기능이 만들어지면 to 를 채워 링크로 바꾼다.
const HUB = [
  { title: '내 챌린지', body: '참여 중인 챌린지와 인증 현황', to: '/challenges/mine' },
  { title: '포인트 지갑', body: '보유 포인트와 거래 내역', to: '/wallet' },
  { title: '랭킹', body: '내 순위와 성공률, 연속 달성' },
]

export function MyPage() {
  const { user } = useAuth()

  return (
    <div className="container page">
      <h1 className="page-title">마이페이지</h1>
      <p className="page-sub">
        {user.nickname} · {user.email}
      </p>
      <ul className="hub">
        {HUB.map((item) => (
          <li key={item.title} className={`hub-card${item.to ? ' is-link' : ''}`}>
            {item.to ? (
              <Link to={item.to} className="hub-link">
                <h3>{item.title}</h3>
                <p>{item.body}</p>
              </Link>
            ) : (
              <>
                <h3>{item.title}</h3>
                <p>{item.body}</p>
                <span className="badge">준비 중</span>
              </>
            )}
          </li>
        ))}
      </ul>
    </div>
  )
}
