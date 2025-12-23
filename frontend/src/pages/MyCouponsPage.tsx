import { useState } from 'react'
import { useMemberCoupons } from '../hooks/useMemberCoupons'

function MyCouponsPage() {
  const [memberId, setMemberId] = useState(1)
  const [filter, setFilter] = useState<'all' | 'available' | 'used'>('all')
  const { data: coupons = [], isLoading, error } = useMemberCoupons(memberId)

  const filteredCoupons = coupons.filter((coupon) => {
    if (filter === 'available') return !coupon.usedAt
    if (filter === 'used') return coupon.usedAt !== null
    return true
  })

  const formatDate = (dateString: string | null) => {
    if (!dateString) return '-'
    return new Date(dateString).toLocaleDateString('ko-KR')
  }

  if (isLoading) return <div>로딩 중...</div>
  if (error) return <div className="error">쿠폰을 불러오는데 실패했습니다.</div>

  return (
    <div>
      <h1>내 쿠폰</h1>
      
      <div style={{ marginBottom: '20px' }}>
        <label>Member ID: </label>
        <input
          type="number"
          className="input"
          value={memberId}
          onChange={(e) => setMemberId(Number(e.target.value))}
        />
      </div>

      <div style={{ marginBottom: '20px' }}>
        <button
          onClick={() => setFilter('all')}
          style={{ marginRight: '10px', backgroundColor: filter === 'all' ? '#0056b3' : '#007bff' }}
        >
          전체
        </button>
        <button
          onClick={() => setFilter('available')}
          style={{ marginRight: '10px', backgroundColor: filter === 'available' ? '#0056b3' : '#007bff' }}
        >
          사용 가능
        </button>
        <button
          onClick={() => setFilter('used')}
          style={{ backgroundColor: filter === 'used' ? '#0056b3' : '#007bff' }}
        >
          사용 완료
        </button>
      </div>

      {filteredCoupons.length === 0 ? (
        <div className="card">보유한 쿠폰이 없습니다.</div>
      ) : (
        <div>
          {filteredCoupons.map((coupon) => (
            <div key={coupon.id} className="card">
              <h3>쿠폰 ID: {coupon.couponId}</h3>
              <p>Member Coupon ID: {coupon.id}</p>
              <p>상태: {coupon.usedAt ? '사용 완료' : '사용 가능'}</p>
              <p>발급일: {formatDate(coupon.createdAt)}</p>
              {coupon.usedAt && <p>사용일: {formatDate(coupon.usedAt)}</p>}
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

export default MyCouponsPage

