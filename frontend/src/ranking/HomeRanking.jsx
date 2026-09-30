import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { rankingApi } from '../api/client.js'
import { Podium } from './Podium.jsx'

const format = (v) => `${Math.round(v)}회`

/** 홈 '실시간 랭킹': 이번 달 인증 수 상위를 3D 시상대로 (비로그인도 볼 수 있음) */
export function HomeRanking() {
  const [top, setTop] = useState(null)

  useEffect(() => {
    let cancelled = false
    rankingApi
      .users('month_verify')
      .then((r) => !cancelled && setTop(r.top))
      .catch(() => !cancelled && setTop([]))
    return () => {
      cancelled = true
    }
  }, [])

  if (top === null) return <p className="muted">불러오는 중…</p>
  if (top.length === 0) {
    return (
      <div className="home-empty">
        <p>이번 달 첫 인증의 주인공이 되어 보세요!</p>
        <Link to="/challenges" className="btn btn-dark">
          챌린지 둘러보기
        </Link>
      </div>
    )
  }
  return (
    <div className="home-podium">
      <p className="rn-help">이번 달 인증을 가장 많이 한 사람들이에요.</p>
      <Podium entries={top} format={format} />
    </div>
  )
}
