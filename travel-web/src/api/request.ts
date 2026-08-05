import axios, { type AxiosResponse } from 'axios'
import { message } from 'ant-design-vue'
import type { ApiResult } from '@/types/chat'

// axios 实例：统一 baseURL、超时（LLM 调用较慢，放宽到 60s）
const instance = axios.create({
  baseURL: '/api',
  timeout: 60000,
})

// 响应拦截器：仅做网络/HTTP 错误兜底，返回完整 response（保持类型合法）
// 业务脱壳下沉到泛型 request 函数，避免拦截器返回类型与 AxiosResponse 冲突
instance.interceptors.response.use(
  (response) => response,
  (error) => {
    message.error(error.message || '网络错误，请稍后重试')
    return Promise.reject(error)
  },
)

/**
 * 泛型请求封装：脱去 ApiResult 外壳，直接返回业务 data。
 * 业务错误码非 0 时弹错并抛出异常。
 */
async function request<T>(call: Promise<AxiosResponse<ApiResult<T>>>): Promise<T> {
  const response = await call
  if (response.data.code !== 0) {
    message.error(response.data.message || '请求失败')
    throw new Error(response.data.message)
  }
  return response.data.data
}

export default instance
export { request }
