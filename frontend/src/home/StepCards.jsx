// 홈 '이렇게 진행돼요': 지그재그 단계의 그림 칸 (사용자 시안의 사진).
// 사진은 public/images/home/step1~4.jpg (가로 2:1, 1200×600) 에 있다. 3번의 '인증 완료!' 휴대폰도 사진에 합성돼 있다.

// 단계 순서대로 쓰는 사진
const PHOTOS = [
  '/images/home/step1.jpg',
  '/images/home/step2.jpg',
  '/images/home/step3.jpg',
  '/images/home/step4.jpg',
]

/**
 * 지그재그 단계 목록: 한 줄에 그림 칸 + 글, 짝수 번째는 좌우를 바꾼다 (구조는 예전 그대로, 그림 칸에 사진만 넣었다).
 * @param steps [{ title, body }] 4개. body 의 \n 은 문장 단위 줄바꿈이다.
 */
export function StepCards({ steps }) {
  return (
    <ol className="steps">
      {steps.map((step, i) => (
        <li key={step.title} className={`step${i % 2 === 1 ? ' is-reverse' : ''}`}>
          <div className="hs-art" aria-hidden="true">
            <img src={PHOTOS[i % PHOTOS.length]} alt="" loading="lazy" />
            <span className="hs-num">{i + 1}</span>
          </div>
          <div className="step-body">
            <h3>{step.title}</h3>
            <p>{step.body}</p>
          </div>
        </li>
      ))}
    </ol>
  )
}
