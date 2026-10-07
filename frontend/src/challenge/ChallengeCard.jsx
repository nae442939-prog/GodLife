import { Link } from 'react-router-dom'
import { CalendarIcon, CategoryIcon, PersonIcon } from './icons.jsx'
import { pointText } from './format.js'

// 참가자 동그라미는 최대 2개 + 나머지 수. (목록에는 참가자 정보를 싣지 않아 모양만 보여 준다)
const SHOWN_DOTS = 2

/** 챌린지 목록 카드. 카드 전체가 상세로 가는 링크이고, '참여하기'도 상세에서 참여한다. */
export function ChallengeCard({ challenge: c }) {
  const dots = Math.min(c.participantCount, SHOWN_DOTS)
  const rest = c.participantCount - dots

  return (
    <li className={`cl-card cat-${c.category.id}`}>
      <Link to={`/challenges/${c.id}`} className="cl-card-link">
        <div className="cl-card-top">
          <span className="cl-icon-tile">
            <CategoryIcon id={c.category.id} size={21} strokeWidth={1.8} />
          </span>
          <span className={`cl-point ${c.mode === 'BET' ? 'is-bet' : ''}`}>{pointText(c)}</span>
        </div>
        <p className="cl-category">
          {c.category.name}
          {c.subType && ` · ${c.subType.name}`}
        </p>
        <h3 className="cl-title">{c.title}</h3>
        <p className="cl-desc">{c.description}</p>
        <div className="cl-stats">
          <span>
            <CalendarIcon />
            {c.totalDays}일
          </span>
          <span>
            <PersonIcon />
            <span className="num">{c.participantCount}</span>명
          </span>
        </div>
        <div className="cl-card-foot">
          {c.participantCount === 0 ? (
            <span className="cl-first">첫 참가자를 기다려요</span>
          ) : (
            <span className="cl-dots" aria-label={`참가자 ${c.participantCount}명`}>
              {Array.from({ length: dots }, (_, i) => (
                <span key={i} className={`cl-dot d${i}`} />
              ))}
              {rest > 0 && <span className="cl-dot is-more">+{rest}</span>}
            </span>
          )}
          <span className="cl-join">참여하기</span>
        </div>
      </Link>
    </li>
  )
}
