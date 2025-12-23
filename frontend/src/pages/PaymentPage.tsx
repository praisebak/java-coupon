import { useState } from 'react'
import { useMemberCoupons, useUseCoupon } from '../hooks/useMemberCoupons'

function PaymentPage() {
  const [memberId, setMemberId] = useState(1)
  const [selectedCouponId, setSelectedCouponId] = useState<number | null>(null)
  const [orderAmount, setOrderAmount] = useState(50000)
  const { data: coupons = [], isLoading } = useMemberCoupons(memberId)
  const useCouponMutation = useUseCoupon()
  const [message, setMessage] = useState<string>('')

  const availableCoupons = coupons.filter((coupon) => !coupon.usedAt)

  const handleUseCoupon = async () => {
    if (!selectedCouponId) {
      setMessage('쿠폰을 선택해주세요.')
      return
    }

    try {
      await useCouponMutation.mutateAsync({
        memberCouponId: selectedCouponId,
        memberId,
      })
      setMessage('쿠폰이 사용되었습니다!')
      setSelectedCouponId(null)
      setTimeout(() => setMessage(''), 3000)
    } catch (error: any) {
      setMessage(error.response?.data?.message || '쿠폰 사용에 실패했습니다.')
      setTimeout(() => setMessage(''), 3000)
    }
  }

  if (isLoading) return <div>로딩 중...</div>

  return (
    <div>
      <h1>결제</h1>

      <div style={{ marginBottom: '20px' }}>
        <label>Member ID: </label>
        <input
          type="number"
          className="input"
          value={memberId}
          onChange={(e) => setMemberId(Number(e.target.value))}
        />
      </div>

      <div className="card" style={{ marginBottom: '20px' }}>
        <h2>주문 정보</h2>
        <div style={{ marginBottom: '10px' }}>
          <label>주문 금액: </label>
          <input
            type="number"
            className="input"
            value={orderAmount}
            onChange={(e) => setOrderAmount(Number(e.target.value))}
            style={{ width: '150px' }}
          />
          <span>원</span>
        </div>
        <p style={{ fontSize: '18px', fontWeight: 'bold' }}>
          총 금액: {orderAmount.toLocaleString()}원
        </p>
      </div>

      <div className="card" style={{ marginBottom: '20px' }}>
        <h2>쿠폰 선택</h2>
        
        {message && (
          <div className={message.includes('실패') ? 'error' : 'success'} style={{ marginBottom: '10px' }}>
            {message}
          </div>
        )}

        {availableCoupons.length === 0 ? (
          <p>사용 가능한 쿠폰이 없습니다.</p>
        ) : (
          <>
            <select
              className="input"
              value={selectedCouponId || ''}
              onChange={(e) => setSelectedCouponId(Number(e.target.value) || null)}
              style={{ marginBottom: '10px', width: '300px' }}
            >
              <option value="">쿠폰을 선택하세요</option>
              {availableCoupons.map((coupon) => (
                <option key={coupon.id} value={coupon.id}>
                  쿠폰 ID: {coupon.couponId} (MemberCoupon ID: {coupon.id})
                </option>
              ))}
            </select>
            <br />
            <button
              onClick={handleUseCoupon}
              disabled={!selectedCouponId || useCouponMutation.isPending}
            >
              {useCouponMutation.isPending ? '처리 중...' : '쿠폰 사용'}
            </button>
          </>
        )}
      </div>
    </div>
  )
}

export default PaymentPage

