import { BrowserRouter, Routes, Route, Link } from 'react-router-dom'
import CouponListPage from './pages/CouponListPage'
import MyCouponsPage from './pages/MyCouponsPage'
import PaymentPage from './pages/PaymentPage'

const basename = (import.meta as any).env?.PROD ? '/coupon' : '/'

function App() {
  return (
    <BrowserRouter basename={basename}>
      <div>
        <nav className="nav">
          <Link to="/">쿠폰 목록</Link>
          <Link to="/my-coupons">내 쿠폰</Link>
          <Link to="/payment">결제</Link>
        </nav>
        
        <div className="container">
          <Routes>
            <Route path="/" element={<CouponListPage />} />
            <Route path="/my-coupons" element={<MyCouponsPage />} />
            <Route path="/payment" element={<PaymentPage />} />
          </Routes>
        </div>
      </div>
    </BrowserRouter>
  )
}

export default App

