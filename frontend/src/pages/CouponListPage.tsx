import { useState } from 'react'
import { useMemberCoupons, useIssueCoupon } from '../hooks/useMemberCoupons'
import { useCoupons } from '../hooks/useCoupons'
import { Coupon } from '../types/api'

function CouponListPage() {
  const [memberId, setMemberId] = useState(1)
  const { data: coupons = [], isLoading, error } = useCoupons()
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

  const getRemainingQuantity = (coupon: Coupon) => {
    if (coupon.totalQuantity === null) return '무제한'
    return (coupon.totalQuantity - coupon.issuedQuantity).toLocaleString()
  }

  if (isLoading) {
    return <div className="container"><p>로딩 중...</p></div>
  }

  if (error) {
    return <div className="container"><p className="error">쿠폰 목록을 불러오는데 실패했습니다.</p></div>
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

      {coupons.length === 0 ? (
        <p>등록된 쿠폰이 없습니다.</p>
      ) : (
        <div>
          {coupons.map((coupon) => {
            const issued = isIssued(coupon.id!)
            const remaining = getRemainingQuantity(coupon)
            return (
              <div key={coupon.id} className="card">
                <h3>{coupon.title}</h3>
                <p>할인 금액: {coupon.discountAmount.toLocaleString()}원</p>
                <p>최소 주문 금액: {coupon.minimumOrderPrice.toLocaleString()}원</p>
                <p>잔여 수량: {remaining}</p>
                <p style={{ fontSize: '0.9em', color: '#666' }}>
                  유효기간: {new Date(coupon.validStartedAt).toLocaleDateString()} ~ {new Date(coupon.validEndedAt).toLocaleDateString()}
                </p>
                <button
                  onClick={() => handleIssue(coupon.id!)}
                  disabled={issued || issueMutation.isPending}
                >
                  {issued ? '발급 완료' : '발급받기'}
                </button>
              </div>
            )
          })}
        </div>
      )}
    </div>
  )
}

export default CouponListPage

