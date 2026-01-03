import { apiClient } from './client'
import { MemberCoupon, IssueCouponRequest, UseCouponRequest } from '../types/api'

export const getMemberCoupons = async (memberId: number): Promise<MemberCoupon[]> => {
  const response = await apiClient.get<MemberCoupon[]>('/member-coupons/by-member-id', {
    params: { memberId },
  })
  return response.data
}

/**
 * SSE 기반 비동기 쿠폰 발급
 * 
 * POST /stream/issue로 SSE 스트림 열기
 * - STATUS 이벤트: 접수 완료 알림
 * - RESULT 이벤트: 최종 결과 (성공/실패)
 * - ERROR 이벤트: 타임아웃 또는 오류
 */
export const issueCoupon = async (
  couponId: number, 
  memberId: number
): Promise<number> => {
  return new Promise(async (resolve, reject) => {
    const baseURL = (import.meta as any).env?.PROD ? 'api' : '/api'
    const url = `${baseURL}/member-coupons/stream/issue`
    
    try {
      // fetch로 SSE 스트림 열기 (POST + text/event-stream)
      const response = await fetch(url, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Accept': 'text/event-stream',
        },
        body: JSON.stringify({ couponId, memberId }),
      })

      if (!response.ok) {
        reject(new Error(`HTTP ${response.status}: 쿠폰 발급 요청 실패`))
        return
      }

      const reader = response.body?.getReader()
      const decoder = new TextDecoder()

      if (!reader) {
        reject(new Error('응답 스트림을 열 수 없습니다.'))
        return
      }

      // 타임아웃 설정 (30초)
      const timeout = setTimeout(() => {
        reader.cancel()
        reject(new Error('쿠폰 발급 시간이 초과되었습니다. 다시 시도해주세요.'))
      }, 30000)

      // SSE 스트림 읽기
      let buffer = ''

      while (true) {
        const { done, value } = await reader.read()
        
        if (done) {
          clearTimeout(timeout)
          break
        }

        buffer += decoder.decode(value, { stream: true })
        const lines = buffer.split('\n\n')
        buffer = lines.pop() || ''

        for (const line of lines) {
          const eventMatch = line.match(/event:\s*(\w+)/)
          const dataMatch = line.match(/data:\s*(.+)/)

          if (!eventMatch || !dataMatch) continue

          const eventType = eventMatch[1]
          const data = dataMatch[1]

          // STATUS 이벤트: 접수 완료 (무시하거나 로깅)
          if (eventType === 'STATUS') {
            console.log('쿠폰 발급 접수:', data)
          }

          // RESULT 이벤트: 최종 결과
          else if (eventType === 'RESULT') {
            clearTimeout(timeout)
            try {
              const result = JSON.parse(data)
              
              if (result.status === 'SUCCESS') {
                const memberCouponId = result.data?.couponId
                resolve(memberCouponId)
              } else {
                reject(new Error(result.message || '쿠폰 발급에 실패했습니다.'))
              }
            } catch (e) {
              reject(new Error('응답 파싱 실패'))
            }
            reader.cancel()
            return
          }

          // ERROR 이벤트: 오류
          else if (eventType === 'ERROR') {
            clearTimeout(timeout)
            reject(new Error(data || '쿠폰 발급 중 오류가 발생했습니다.'))
            reader.cancel()
            return
          }
        }
      }

    } catch (error) {
      reject(error)
    }
  })
}

export const useCoupon = async (memberCouponId: number, memberId: number): Promise<void> => {
  await apiClient.post(`/member-coupons/${memberCouponId}/use`, {
    memberCouponId,
    memberId,
  } as UseCouponRequest)
}

