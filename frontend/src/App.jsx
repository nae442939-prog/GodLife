import { Navigate, Route, Routes } from 'react-router-dom'
import { Layout } from './components/Layout.jsx'
import { RequireAuth } from './components/RequireAuth.jsx'
import { FindIdPage } from './pages/FindIdPage.jsx'
import { FindPasswordPage } from './pages/FindPasswordPage.jsx'
import { ChallengeChatPage } from './pages/ChallengeChatPage.jsx'
import { ChallengeVerifyPage } from './pages/ChallengeVerifyPage.jsx'
import { ChallengeCreatePage } from './pages/ChallengeCreatePage.jsx'
import { ChallengeDetailPage } from './pages/ChallengeDetailPage.jsx'
import { ChallengeInvitePage } from './pages/ChallengeInvitePage.jsx'
import { ChallengeMinePage } from './pages/ChallengeMinePage.jsx'
import { WalletPage } from './pages/WalletPage.jsx'
import { DiaryPage } from './pages/DiaryPage.jsx'
import { AdminCollusionPage } from './pages/AdminCollusionPage.jsx'
import { AdminCommunityPage } from './pages/AdminCommunityPage.jsx'
import { AdminInquiriesPage } from './pages/AdminInquiriesPage.jsx'
import { AdminReviewsPage } from './pages/AdminReviewsPage.jsx'
import { AdminShopPage } from './pages/AdminShopPage.jsx'
import { BadgesPage } from './pages/BadgesPage.jsx'
import { RankingPage } from './pages/RankingPage.jsx'
import { RecordPage } from './pages/RecordPage.jsx'
import { ProfilePage } from './pages/ProfilePage.jsx'
import { MessagesPage } from './pages/MessagesPage.jsx'
import { MessageRoomPage } from './pages/MessageRoomPage.jsx'
import { ChallengeListPage } from './pages/ChallengeListPage.jsx'
import { CommunityPage } from './pages/CommunityPage.jsx'
import { CommunityPostPage } from './pages/CommunityPostPage.jsx'
import { CommunityWritePage } from './pages/CommunityWritePage.jsx'
import { HomePage } from './pages/HomePage.jsx'
import { LoginPage } from './pages/LoginPage.jsx'
import { MyPage } from './pages/MyPage.jsx'
import { PaymentReturnPage } from './pages/PaymentReturnPage.jsx'
import { SettingsPage } from './pages/SettingsPage.jsx'
import { ShopCartPage } from './pages/ShopCartPage.jsx'
import { ShopCheckoutPage } from './pages/ShopCheckoutPage.jsx'
import { ShopOrderPage } from './pages/ShopOrderPage.jsx'
import { ShopOrdersPage } from './pages/ShopOrdersPage.jsx'
import { ShopPage } from './pages/ShopPage.jsx'
import { ShopProductPage } from './pages/ShopProductPage.jsx'
import { ShopWishlistPage } from './pages/ShopWishlistPage.jsx'
import { TierPage } from './pages/TierPage.jsx'
import { SignupPage } from './pages/SignupPage.jsx'
import { VerifyPhonePage } from './pages/VerifyPhonePage.jsx'

export default function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        {/* 누구나 볼 수 있는 화면 */}
        <Route path="/" element={<HomePage />} />
        <Route path="/login" element={<LoginPage />} />
        <Route path="/signup" element={<SignupPage />} />
        <Route path="/find-id" element={<FindIdPage />} />
        <Route path="/find-password" element={<FindPasswordPage />} />
        <Route path="/challenges" element={<ChallengeListPage />} />
        <Route path="/rankings" element={<RankingPage />} />
        <Route path="/users/:id" element={<ProfilePage />} />
        <Route path="/challenges/:id" element={<ChallengeDetailPage />} />
        <Route path="/challenges/join/:code" element={<ChallengeInvitePage />} />
        <Route path="/shop" element={<ShopPage />} />
        <Route path="/shop/products/:id" element={<ShopProductPage />} />
        <Route path="/community" element={<CommunityPage />} />
        <Route path="/community/:id" element={<CommunityPostPage />} />

        {/* 로그인해야 볼 수 있는 화면 */}
        <Route element={<RequireAuth />}>
          <Route path="/challenges/new" element={<ChallengeCreatePage />} />
          <Route path="/challenges/mine" element={<ChallengeMinePage />} />
          <Route path="/shop/cart" element={<ShopCartPage />} />
          <Route path="/shop/checkout" element={<ShopCheckoutPage />} />
          <Route path="/shop/orders" element={<ShopOrdersPage />} />
          <Route path="/shop/orders/:id" element={<ShopOrderPage />} />
          <Route path="/shop/wishlist" element={<ShopWishlistPage />} />
          <Route path="/community/new" element={<CommunityWritePage />} />
          <Route path="/community/:id/edit" element={<CommunityWritePage />} />
          <Route path="/records" element={<DiaryPage />} />
          <Route path="/records/calendar" element={<RecordPage />} />
          <Route path="/wallet" element={<WalletPage />} />
          <Route path="/wallet/charge/success" element={<PaymentReturnPage step="success" />} />
          <Route path="/wallet/charge/fail" element={<PaymentReturnPage step="fail" />} />
          <Route path="/messages" element={<MessagesPage />} />
          <Route path="/messages/:userId" element={<MessageRoomPage />} />
          <Route path="/challenges/:id/chat" element={<ChallengeChatPage />} />
          <Route path="/me" element={<MyPage />} />
          <Route path="/me/badges" element={<BadgesPage />} />
          <Route path="/me/tier" element={<TierPage />} />
          <Route path="/settings" element={<SettingsPage />} />
          <Route path="/admin/inquiries" element={<AdminInquiriesPage />} />
          <Route path="/admin/reviews" element={<AdminReviewsPage />} />
          <Route path="/admin/collusion" element={<AdminCollusionPage />} />
          <Route path="/admin/community" element={<AdminCommunityPage />} />
          <Route path="/admin/shop" element={<AdminShopPage />} />
          <Route path="/verify-phone" element={<VerifyPhonePage />} />
        </Route>

        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>

      {/* 인증 화면은 헤더·푸터 없이 전체 화면 (영상통화처럼) */}
      <Route element={<RequireAuth />}>
        <Route path="/challenges/:id/verify" element={<ChallengeVerifyPage />} />
      </Route>
    </Routes>
  )
}
