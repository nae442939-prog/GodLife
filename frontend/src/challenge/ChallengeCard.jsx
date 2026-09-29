import { Link } from 'react-router-dom'
import { dDayText, frequencyText, periodText, pointText } from './format.js'

/** 챌린지 목록 카드. 카테고리 색은 카테고리 id 로 정한다. (운동 코랄 · 공부 파랑 · 독서 민트) */
export function ChallengeCard({ challenge: c }) {
  return (
    <li className="challenge-card is-link">
      <Link to={`/challenges/${c.id}`} className="challenge-card-link">
        <div className="challenge-card-top">
          <span className={`challenge-category cat-${c.category.id}`}>{c.category.name}</span>
          <span className={`mode-badge ${c.mode === 'BET' ? 'is-bet' : ''}`}>{pointText(c)}</span>
          <span className="dday">{dDayText(c)}</span>
        </div>
        <h3>{c.title}</h3>
        <dl className="challenge-meta">
          <div>
            <dt>기간</dt>
            <dd>{periodText(c)}</dd>
          </div>
          <div>
            <dt>인증</dt>
            <dd>{frequencyText(c).replace(' 인증', '')}</dd>
          </div>
          <div>
            <dt>참가자</dt>
            <dd>
              {c.participantCount}/{c.maxParticipants}명
            </dd>
          </div>
        </dl>
      </Link>
    </li>
  )
}
