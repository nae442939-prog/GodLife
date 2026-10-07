import { Link, NavLink } from 'react-router-dom'
import { useAuth } from '../auth/useAuth.js'
import { MegaMenu, MyPoints, MyRankSummary, TodayChallenges, TodayDiary } from './MegaMenu.jsx'
import { NotificationBell } from './NotificationBell.jsx'
import { UserMenu } from './UserMenu.jsx'

// 챌린지 메가 메뉴 항목. 나중에 항목을 더하려면 여기에 한 줄 추가한다.
const CHALLENGE_LINKS = [
  { label: '챌린지 둘러보기', to: '/challenges' },
  { label: '내 챌린지', to: '/challenges/mine' },
]

// 랭킹 메가 메뉴 항목 (랭킹 화면의 탭으로 바로 간다)
const RANKING_LINKS = [
  { label: '전체 랭킹', to: '/rankings?tab=users' },
  { label: '친구 랭킹', to: '/rankings?tab=friends' },
  { label: '내 챌린지 랭킹', to: '/rankings?tab=challenges' },
  { label: '시즌 랭킹', to: '/rankings?tab=season' },
]

// 갓생기록 메가 메뉴 항목 (일기장 · 캘린더)
const RECORD_LINKS = [
  { label: '갓생기록', to: '/records' },
  { label: '갓생기록 캘린더', to: '/records/calendar' },
]

// 포인트 상점 메가 메뉴 항목
const SHOP_LINKS = [
  { label: '상점 둘러보기', to: '/shop' },
  { label: '장바구니', to: '/shop/cart' },
  { label: '찜한 상품', to: '/shop/wishlist' },
  { label: '주문 내역', to: '/shop/orders' },
]

// 메가 메뉴 종류별 링크와 오른쪽 내용(aside)
const MEGA = {
  challenge: { links: CHALLENGE_LINKS, aside: (open) => <TodayChallenges open={open} /> },
  record: { links: RECORD_LINKS, aside: (open) => <TodayDiary open={open} /> },
  ranking: { links: RANKING_LINKS, aside: (open) => <MyRankSummary open={open} /> },
  shop: { links: SHOP_LINKS, aside: (open) => <MyPoints open={open} /> },
}

// 아직 만들지 않은 메뉴는 링크 대신 "준비 중"으로 표시한다. 만들어지면 to 만 채우면 된다.
const MENU = [
  { label: '챌린지', to: '/challenges', mega: 'challenge' },
  { label: '커뮤니티', to: '/community' },
  { label: '랭킹', to: '/rankings', mega: 'ranking' },
  { label: '포인트 상점', to: '/shop', mega: 'shop' },
  { label: '갓생기록', to: '/records', mega: 'record' },
]

export function Header() {
  const { status } = useAuth()

  return (
    <header className="site-header">
      <div className="container header-inner">
        <Link to="/" className="logo" aria-label="갓생살기 홈">
          <span className="logo-mark" aria-hidden="true" />
          갓생살기
        </Link>

        <nav className="menu" aria-label="주 메뉴">
          {MENU.map((item) =>
            !item.to ? (
              <span key={item.label} className="menu-soon" title="준비 중입니다">
                {item.label}
                <small>준비 중</small>
              </span>
            ) : item.mega ? (
              <MegaMenu
                key={item.label}
                label={item.label}
                to={item.to}
                links={MEGA[item.mega].links}
                aside={MEGA[item.mega].aside}
              />
            ) : (
              <NavLink key={item.label} to={item.to}>
                {item.label}
              </NavLink>
            ),
          )}
        </nav>

        <div className="header-auth">
          {status === 'loading' ? null : status === 'authed' ? (
            <>
              <NotificationBell />
              <UserMenu />
            </>
          ) : (
            // 회원가입은 로그인 화면 아래 링크로 들어간다.
            <Link to="/login" className="btn btn-ghost btn-sm">
              로그인
            </Link>
          )}
        </div>
      </div>
    </header>
  )
}
