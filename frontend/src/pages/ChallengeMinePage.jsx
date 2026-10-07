import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { verificationApi } from '../api/client.js'
import { MyChallengeList } from '../challenge/MyChallengeList.jsx'

/** 내 챌린지: 참여 중이거나 개설한, 아직 끝나지 않은 챌린지 (진행 중인 것이 먼저) */
export function ChallengeMinePage() {
  const [state, setState] = useState({ loaded: false, items: [], error: '' })

  useEffect(() => {
    let cancelled = false
    verificationApi
      .myChallenges()
      .then((items) => !cancelled && setState({ loaded: true, items, error: '' }))
      .catch((err) => !cancelled && setState({ loaded: true, items: [], error: err.message }))
    return () => {
      cancelled = true
    }
  }, [])

  const ongoing = state.items.filter((c) => c.inProgress)
  const upcoming = state.items.filter((c) => !c.inProgress)

  return (
    <div className="container page">
      {/* 제목 줄은 챌린지 목록과 같은 모양 (같은 폭·크기·밑줄) */}
      <div className="ch-head cl-head">
        <div>
          <h1 className="page-title">내 챌린지</h1>
          <p className="page-sub">참여 중이거나 내가 만든 챌린지예요. 오늘 인증도 여기서 바로 할 수 있어요.</p>
        </div>
        <Link to="/challenges" className="btn btn-dark-outline">
          챌린지 둘러보기
        </Link>
      </div>

      <div className="my-ch-body">
        {state.error && <p className="form-error">{state.error}</p>}
        {!state.loaded ? (
          <p className="muted">불러오는 중…</p>
        ) : state.items.length === 0 ? (
          !state.error && (
            <div className="my-ch-empty">
              <p>아직 참여 중인 챌린지가 없어요.</p>
              <Link to="/challenges" className="btn btn-dark">
                챌린지 둘러보기
              </Link>
            </div>
          )
        ) : (
          <>
            {ongoing.length > 0 && (
              <section className="my-section">
                <h2>진행 중 {ongoing.length}</h2>
                <MyChallengeList items={ongoing} />
              </section>
            )}
            {upcoming.length > 0 && (
              <section className="my-section">
                <h2>시작 전 {upcoming.length}</h2>
                <MyChallengeList items={upcoming} />
              </section>
            )}
          </>
        )}
      </div>
    </div>
  )
}
