import axios from 'axios'

// 개발: /api (vite proxy 사용)
// 프로덕션: api (상대 경로, /coupon/api로 변환됨)
const baseURL = (import.meta as any).env?.PROD ? 'api' : '/api'

export const apiClient = axios.create({
  baseURL,
  headers: {
    'Content-Type': 'application/json',
  },
})

apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    console.error('API Error:', error.response?.data || error.message)
    return Promise.reject(error)
  }
)

