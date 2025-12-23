import { useState } from 'react'
import { useMemberCoupons, useIssueCoupon } from '../hooks/useMemberCoupons'

// 임시 쿠폰 목록 데이터 (실제로는 API에서 가져와야 함)
const MOCK_COUPONS = [
  { id: 1, title: '신규 가입 쿠폰', discountAmount: 5000, minimumOrderPrice: 10000 },
  { id: 2, title: '할인 쿠폰', discountAmount: 3000, minimumOrderPrice: 20000 },
  { id: 3, title: '특가 쿠폰', discountAmount: 10000, minimumOrderPrice: 50000 },
]

function CouponListPage() {
  const [memberId, setMemberId] = useState(1)
  const { data: myCoupons = [], refetch } = useMemberCoupons(memberId)
  const issueMutation = useIssueCoupon()
  const [message, setMessage] = useState<string>('')

  const handleIssue = async (couponId: number) => {
    try {
      await issueMutation.mutateAsync({ couponId, memberId })
      setMessage('쿠폰이 발급되었습니다!')
      refetch()
      setTimeout(() => setMessage(''), 3000)
    } catch (error: any) {
      setMessage(error.response?.data?.message || '쿠폰 발급에 실패했습니다.')
      setTimeout(() => setMessage(''), 3000)
    }
  }

  const isIssued = (couponId: number) => {
    return myCoupons.some((coupon) => coupon.couponId === couponId)
  }

  return (
    <div>
      <h1>쿠폰 목록</h1>
      
      <div style={{ marginBottom: '20px' }}>
        <label>Member ID: </label>
        <input
          type="number"
          className="input"
          value={memberId}
          onChange={(e) => setMemberId(Number(e.target.value))}
        />
      </div>

      {message && (
        <div className={message.includes('실패') ? 'error' : 'success'}>
          {message}
        </div>
      )}

      <div>
        {MOCK_COUPONS.map((coupon) => {
          const issued = isIssued(coupon.id)
          return (
            <div key={coupon.id} className="card">
              <h3>{coupon.title}</h3>
              <p>할인 금액: {coupon.discountAmount.toLocaleString()}원</p>
              <p>최소 주문 금액: {coupon.minimumOrderPrice.toLocaleString()}원</p>
              <button
                onClick={() => handleIssue(coupon.id)}
                disabled={issued || issueMutation.isPending}
              >
                {issued ? '발급 완료' : '발급받기'}
              </button>
            </div>
          )
        })}
      </div>
    </div>
  )
}

export default CouponListPage

