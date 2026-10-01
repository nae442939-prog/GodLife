import { useEffect, useRef, useState } from 'react'
import { Link, useLocation, useNavigate, useSearchParams } from 'react-router-dom'
import { recordApi } from '../api/client.js'
import { addDays, parseDate, toIsoDate } from '../challenge/format.js'

const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토']
const DIARY_MAX = 500
const MOODS = [
  { value: 'GREAT', emoji: '🤩', label: '최고예요' },
  { value: 'GOOD', emoji: '🙂', label: '좋아요' },
  { value: 'OKAY', emoji: '😐', label: '보통이에요' },
  { value: 'SAD', emoji: '😔', label: '아쉬워요' },
  { value: 'HARD', emoji: '😣', label: '힘들었어요' },
]
const MOOD_EMOJI = Object.fromEntries(MOODS.map((m) => [m.value, m.emoji]))
const RESULT_TEXT = { DONE: '성공', FAIL: '실패', PENDING: '오늘 아직', REST: '쉬는 날' }

/**
 * 갓생기록 (/records): 종이 다이어리 한 장 (사용자 시안). 날짜는 주소(?date=)에 두고, 없으면 오늘.
 * 최근 7일 기분 줄 → 이날 쓴 일기 목록 → 그날 함께한 챌린지 태그 → 기분 → 줄노트 글 → 사진 한 장 → [기록 저장하기].
 * 일기는 하루에 여러 개 쓸 수 있다: 목록에서 고르면(?entry=) 그 일기를 수정하고, 고르지 않으면 새 일기를 쓴다.
 * 저장하면 쓰던 내용을 비우고 새 일기 화면으로 돌아간다 (저장한 일기는 목록에 남는다).
 * 지난 날짜도 쓰고 고칠 수 있다. 일기는 나만 본다. 날짜별 성공·실패는 [캘린더 보기]에 있다.
 */
export function DiaryPage() {
  const [params] = useSearchParams()
  const todayIso = toIsoDate(new Date())
  const picked = params.get('date') ?? ''
  const valid = /^\d{4}-\d{2}-\d{2}$/.test(picked) && toIsoDate(parseDate(picked)) === picked && picked <= todayIso
  const date = valid ? picked : todayIso
  const entryId = Number(params.get('entry')) || null
  const location = useLocation()
  // 날짜나 고른 일기가 바뀌거나 방금 저장했으면(at) 새로 만들어 다시 불러온다 → 쓰던 글이 다른 일기로 넘어가지 않는다
  const key = `${date}|${entryId ?? 'new'}|${location.state?.at ?? ''}`
  return <DiarySheet key={key} date={date} entryId={entryId} today={todayIso} />
}

function DiarySheet({ date, entryId, today }) {
  const navigate = useNavigate()
  const location = useLocation()
  const [day, setDay] = useState(null)
  const [loadError, setLoadError] = useState('')
  const [text, setText] = useState('')
  const [mood, setMood] = useState(null)
  const [tags, setTags] = useState([])
  // 사진: 저장된 것 / 새로 고른 파일(file) / 빼기로 한 것(removed). 저장할 때 한 번에 반영한다
  const [photo, setPhoto] = useState({ url: null, file: null, removed: false })
  const [busy, setBusy] = useState(false)
  // 저장하고 이 화면으로 넘어온 경우 '저장했어요'를 이어서 보여 준다
  const [notice, setNotice] = useState({ kind: 'ok', text: location.state?.notice ?? '' })
  const fileRef = useRef(null)

  // 고치는 중인 일기 (목록에서 고른 것). 없으면 새 일기를 쓰는 중
  const editing = day?.diaries.find((d) => d.id === entryId) ?? null

  useEffect(() => {
    let cancelled = false
    recordApi
      .day(date)
      .then((r) => {
        if (cancelled) return
        const entry = r.diaries.find((d) => d.id === entryId)
        setDay(r)
        setText(entry?.content ?? '')
        setMood(entry?.mood ?? null)
        // 새 일기는 그날 인증에 성공한 챌린지를 미리 골라 둔다
        setTags(entry ? entry.tags : r.items.filter((it) => it.result === 'DONE').map((it) => it.challengeId))
      })
      .catch((err) => !cancelled && setLoadError(err.message))
    return () => {
      cancelled = true
    }
  }, [date, entryId])

  // 저장된 사진은 본인만 받을 수 있어 토큰을 붙여 받아 보여 준다
  const savedPhotoId = editing?.photo ? editing.id : null
  useEffect(() => {
    if (!savedPhotoId) return
    let cancelled = false
    let url = null
    recordApi
      .diaryPhotoBlob(savedPhotoId)
      .then((blob) => {
        if (cancelled) return
        url = URL.createObjectURL(blob)
        setPhoto((p) => (p.file || p.removed ? p : { ...p, url }))
      })
      .catch(() => {})
    return () => {
      cancelled = true
      if (url) URL.revokeObjectURL(url)
    }
  }, [savedPhotoId])

  function pickPhoto(e) {
    const file = e.target.files?.[0]
    e.target.value = ''
    if (!file) return
    setPhoto({ url: URL.createObjectURL(file), file, removed: false })
    setNotice({ kind: 'ok', text: '' })
  }

  /** 저장·삭제 뒤: 쓰던 내용을 비우고 새 일기 화면으로 돌아간다 (저장한 일기는 위 목록에 보인다). 안내 문구를 넘긴다 */
  function reset(message) {
    navigate(`/records?date=${date}`, {
      replace: true,
      // 같은 주소로 다시 올 때도 화면을 새로 불러오게 시각을 같이 넘긴다
      state: { notice: message, at: Date.now() },
    })
  }

  async function save(e) {
    e.preventDefault()
    const body = { content: text.trim(), mood, challengeIds: tags }
    if (!editing && !body.content && !mood && !photo.file) {
      setNotice({ kind: 'error', text: '글이나 기분, 사진 중 하나는 남겨 주세요.' })
      return
    }
    setBusy(true)
    setNotice({ kind: 'ok', text: '' })
    try {
      if (editing) {
        // 사진을 먼저 반영해야 글 없이 사진만 남기는 일기도 저장된다
        if (photo.file) await recordApi.saveDiaryPhoto(editing.id, photo.file)
        else if (photo.removed && editing.photo) await recordApi.deleteDiaryPhoto(editing.id)
        const keepsPhoto = Boolean(photo.file) || (editing.photo && !photo.removed)
        const empty = !body.content && !mood && !keepsPhoto
        // 사진을 빼면서 글·기분도 없던 일기는 서버가 이미 지웠다
        const alreadyGone = empty && photo.removed && editing.photo && !editing.content && !editing.mood
        if (!alreadyGone) await recordApi.updateDiary(editing.id, body)
        reset(empty ? '남긴 내용이 없어 일기를 지웠어요.' : '저장했어요 ✓')
      } else if (photo.file) {
        const { id } = await recordApi.createDiaryWithPhoto(date, photo.file)
        await recordApi.updateDiary(id, body)
        reset('저장했어요 ✓')
      } else {
        await recordApi.createDiary(date, body)
        reset('저장했어요 ✓')
      }
    } catch (err) {
      setNotice({ kind: 'error', text: err.message })
      setBusy(false)
    }
  }

  async function remove() {
    setBusy(true)
    setNotice({ kind: 'ok', text: '' })
    try {
      await recordApi.deleteDiary(editing.id)
      reset('일기를 지웠어요.')
    } catch (err) {
      setNotice({ kind: 'error', text: err.message })
      setBusy(false)
    }
  }

  const isToday = date === today
  const hasPhoto = Boolean(photo.url) && !photo.removed

  return (
    <div className="dwp-page">
      <form className="dwp-sheet" onSubmit={save}>
        <span className="dwp-tape" aria-hidden="true" />
        <span className="dwp-tape dwp-tape--2" aria-hidden="true" />
        <span className="dwp-pin" aria-hidden="true">
          <svg width="18" height="18" viewBox="0 0 24 24">
            <path d="M12 2a5 5 0 0 0-1 9.9V17l1 4 1-4v-5.1A5 5 0 0 0 12 2z" fill="#b7a67c" />
          </svg>
        </span>

        <div className="dwp-topRow">
          <div>
            <div className="dwp-eyebrow">
              {isToday ? '오늘 하루, 짧게라도 남겨보세요' : '지난 하루도 다시 꺼내 볼 수 있어요'}
            </div>
            <h1 className="dwp-title">갓생기록 ✎</h1>
          </div>
          <div className="dwp-topRight">
            <div className="dwp-dateNav">
              <Link to={`/records?date=${addDays(date, -1)}`} className="dwp-dateArrow" aria-label="전날">
                <svg width="13" height="13" viewBox="0 0 24 24" aria-hidden="true">
                  <path d="M15 5l-7 7 7 7" stroke="#63573c" strokeWidth="2.2" fill="none" strokeLinecap="round" strokeLinejoin="round" />
                </svg>
              </Link>
              <span className="dwp-dateLabel">{dateLabel(date)}</span>
              {isToday ? (
                <span className="dwp-dateArrow is-disabled" aria-hidden="true">
                  <NextArrow />
                </span>
              ) : (
                <Link to={`/records?date=${addDays(date, 1)}`} className="dwp-dateArrow" aria-label="다음 날">
                  <NextArrow />
                </Link>
              )}
            </div>
            <Link to={`/records/calendar?month=${date.slice(0, 7)}&date=${date}`} className="dwp-calBtn">
              <svg width="13" height="13" viewBox="0 0 24 24" aria-hidden="true">
                <rect x="3" y="5" width="18" height="16" rx="2" stroke="currentColor" strokeWidth="1.8" fill="none" />
                <path d="M3 10h18M8 3v4M16 3v4" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
              </svg>
              캘린더 보기
            </Link>
          </div>
        </div>

        {loadError ? (
          <p className="dwp-notice is-error">{loadError}</p>
        ) : !day ? (
          <p className="dwp-loading">불러오는 중…</p>
        ) : (
          <>
            <div className="dwp-strip" aria-label="최근 7일">
              {day.week.map((w) => {
                const current = w.date === date
                return (
                  <Link
                    key={w.date}
                    to={`/records?date=${w.date}`}
                    className="dwp-stripDay"
                    aria-current={current ? 'date' : undefined}
                    aria-label={`${stripLabel(w.date)}${w.written ? ', 기록 있음' : ', 기록 없음'}`}
                  >
                    <span
                      className={`dwp-stripDot${w.written ? ' dwp-stripDot--filled' : ''}${current ? ' dwp-stripDot--today' : ''}`}
                    >
                      {w.written ? (MOOD_EMOJI[w.mood] ?? '✓') : current ? '✎' : '·'}
                    </span>
                    <span className={`dwp-stripLabel${current ? ' dwp-stripLabel--today' : ''}`}>
                      {stripLabel(w.date)}
                    </span>
                  </Link>
                )
              })}
            </div>

            {day.diaries.length > 0 && (
              <>
                <h2 className="dwp-sectionTitle">
                  📒 {isToday ? '오늘' : '이날'} 쓴 일기 {day.diaries.length}개
                </h2>
                <p className="dwp-sectionHint">수정할 일기를 누르거나, 새 일기를 하나 더 써 보세요</p>
                <div className="dwp-entryRow">
                  {day.diaries.map((d) => (
                    <Link
                      key={d.id}
                      to={`/records?date=${date}&entry=${d.id}`}
                      className={`dwp-entry${d.id === editing?.id ? ' dwp-entry--active' : ''}`}
                      aria-current={d.id === editing?.id ? 'true' : undefined}
                    >
                      <span className="dwp-entryMood" aria-hidden="true">
                        {MOOD_EMOJI[d.mood] ?? '✎'}
                      </span>
                      <span className="dwp-entryText">
                        {d.content || (d.photo ? '사진 일기' : '기분만 남겼어요')}
                      </span>
                      <small>{d.createdAt.slice(11, 16)}</small>
                    </Link>
                  ))}
                  <Link
                    to={`/records?date=${date}`}
                    className={`dwp-entry dwp-entry--new${editing ? '' : ' dwp-entry--active'}`}
                    aria-current={editing ? undefined : 'true'}
                  >
                    + 새 일기 쓰기
                  </Link>
                </div>
              </>
            )}

            <h2 className="dwp-sectionTitle">🏷️ {isToday ? '오늘' : '이날'} 함께한 챌린지</h2>
            <p className="dwp-sectionHint">
              {day.items.length > 0
                ? '일기에 태그하고 싶은 챌린지를 눌러서 선택해보세요'
                : '이날은 진행한 챌린지가 없어요. 새 챌린지를 찾아볼까요?'}
            </p>
            <div className="dwp-chipRow">
              {day.items.map((it) => (
                <label key={it.challengeId} className="dwp-chip">
                  <input
                    type="checkbox"
                    className="dwp-chipInput"
                    checked={tags.includes(it.challengeId)}
                    onChange={(e) =>
                      setTags((cur) =>
                        e.target.checked ? [...cur, it.challengeId] : cur.filter((id) => id !== it.challengeId),
                      )
                    }
                  />
                  <span className="dwp-chipMark" aria-hidden="true" />
                  {it.title}
                  <small>{RESULT_TEXT[it.result]}</small>
                </label>
              ))}
              <Link to="/challenges" className="dwp-addChip">
                + 챌린지 추가
              </Link>
            </div>

            <h2 className="dwp-sectionTitle">{isToday ? '오늘' : '이날'} 기분은 어땠나요?</h2>
            <div className="dwp-moodRow" role="group" aria-label="기분">
              {MOODS.map((m) => (
                <button
                  key={m.value}
                  type="button"
                  className={`dwp-moodBtn${mood === m.value ? ' dwp-moodBtn--active' : ''}`}
                  aria-pressed={mood === m.value}
                  onClick={() => setMood(mood === m.value ? null : m.value)}
                >
                  <span className="dwp-moodEmoji" aria-hidden="true">
                    {m.emoji}
                  </span>
                  <span className="dwp-moodText">{m.label}</span>
                </button>
              ))}
            </div>

            <div className="dwp-writeWrap">
              <div className="dwp-ruled">
                <span className="dwp-marginLine" aria-hidden="true" />
                <textarea
                  className="dwp-textarea"
                  aria-label={editing ? '일기 수정' : '새 일기'}
                  value={text}
                  maxLength={DIARY_MAX}
                  placeholder="오늘 하루는 어땠나요? 갓생 살며 느낀 걸 자유롭게 적어보세요."
                  onChange={(e) => setText(e.target.value)}
                />
              </div>
              {hasPhoto && (
                <div className="dwp-photo">
                  <img src={photo.url} alt="일기에 붙인 사진" />
                  <button
                    type="button"
                    className="dwp-photoRemove"
                    onClick={() => setPhoto({ url: null, file: null, removed: true })}
                    aria-label="사진 빼기"
                  >
                    ✕
                  </button>
                </div>
              )}
              <div className="dwp-writeFooter">
                <button type="button" className="dwp-photoBtn" onClick={() => fileRef.current?.click()}>
                  <svg width="13" height="13" viewBox="0 0 24 24" aria-hidden="true">
                    <rect x="3" y="5" width="18" height="14" rx="2" stroke="#574b33" strokeWidth="1.6" fill="none" />
                    <circle cx="9" cy="10.5" r="1.6" fill="#574b33" />
                    <path d="M4 17l5-5 4 4 3-3 4 4" stroke="#574b33" strokeWidth="1.6" fill="none" strokeLinecap="round" strokeLinejoin="round" />
                  </svg>
                  {hasPhoto ? '사진 바꾸기' : '사진 추가'}
                </button>
                <input ref={fileRef} type="file" accept="image/jpeg,image/png" hidden onChange={pickPhoto} />
                <span className="dwp-charCount">
                  {text.length}자 / {DIARY_MAX}자
                </span>
              </div>
            </div>

            <button type="submit" className="dwp-saveBtn" disabled={busy}>
              <svg width="15" height="15" viewBox="0 0 24 24" aria-hidden="true">
                <path d="M12 19l7-7 3 3-7 7-3-3z" stroke="#fbf4e3" strokeWidth="1.8" fill="none" strokeLinejoin="round" />
                <path d="M18 13l-1.5-6L4 2l4.5 12.5L18 13z" stroke="#fbf4e3" strokeWidth="1.8" fill="none" strokeLinejoin="round" />
                <path d="M2 22l3-1" stroke="#fbf4e3" strokeWidth="1.8" strokeLinecap="round" />
              </svg>
              {busy ? '저장하는 중…' : editing ? '수정한 내용 저장하기' : '기록 저장하기'}
            </button>

            <div className="dwp-bottomRow">
              <span className="dwp-private">🔒 일기는 나만 볼 수 있어요</span>
              {notice.text && (
                <span className={`dwp-notice${notice.kind === 'error' ? ' is-error' : ''}`} role="status">
                  {notice.text}
                </span>
              )}
              {editing && (
                <button type="button" className="dwp-removeBtn" onClick={remove} disabled={busy}>
                  이 일기 지우기
                </button>
              )}
            </div>
          </>
        )}
      </form>
    </div>
  )
}

function NextArrow() {
  return (
    <svg width="13" height="13" viewBox="0 0 24 24" aria-hidden="true">
      <path d="M9 5l7 7-7 7" stroke="#63573c" strokeWidth="2.2" fill="none" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  )
}

/** '2026.10.1 (목)' */
function dateLabel(iso) {
  const d = parseDate(iso)
  return `${d.getFullYear()}.${d.getMonth() + 1}.${d.getDate()} (${WEEKDAYS[d.getDay()]})`
}

/** '10.1 목' */
function stripLabel(iso) {
  const d = parseDate(iso)
  return `${d.getMonth() + 1}.${d.getDate()} ${WEEKDAYS[d.getDay()]}`
}
