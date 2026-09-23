import { describe, it, expect } from 'vitest'
import { AxiosError } from 'axios'
import { friendlyErrorMessage } from '@/utils/errorMessage'

function axiosError(opts: { status?: number; code?: string; message?: string } = {}) {
  const err = new AxiosError(opts.message ?? 'Request failed with status code 500', opts.code)
  if (opts.status !== undefined) {
    // 只关心 response.status，其余字段用最小可满足值
    ;(err as unknown as { response: unknown }).response = {
      status: opts.status,
      data: null,
      statusText: '',
      headers: {},
      config: {},
    }
  }
  return err
}

describe('friendlyErrorMessage', () => {
  it('不把 axios 的原始英文消息直接暴露给用户', () => {
    const msg = friendlyErrorMessage(axiosError({ status: 500 }))
    expect(msg).toBe('服务暂时不可用，请稍后重试')
    expect(msg).not.toContain('Request failed')
    expect(msg).not.toContain('status code')
  })

  it('区分网络不可达（后端未启动）与超时', () => {
    expect(friendlyErrorMessage(new AxiosError('Network Error', 'ERR_NETWORK'))).toBe(
      '无法连接到服务，请确认后端已启动'
    )
    expect(friendlyErrorMessage(axiosError({ code: 'ECONNABORTED', message: 'timeout of 30000ms exceeded' }))).toBe(
      '请求超时，请检查网络后重试'
    )
  })

  it('按 HTTP 状态给出对应说明', () => {
    expect(friendlyErrorMessage(axiosError({ status: 401 }))).toBe('登录状态已失效，请重新登录')
    expect(friendlyErrorMessage(axiosError({ status: 403 }))).toBe('当前账号没有访问权限')
    expect(friendlyErrorMessage(axiosError({ status: 404 }))).toBe('请求的数据不存在')
    expect(friendlyErrorMessage(axiosError({ status: 400 }))).toBe('请求参数有误，请调整后重试')
  })

  it('保留后端返回的中文业务错误', () => {
    expect(friendlyErrorMessage(new Error('课程已下架'))).toBe('课程已下架')
  })

  it('未知错误回退到调用方提供的兜底文案', () => {
    expect(friendlyErrorMessage(null)).toBe('请求失败，请稍后重试')
    expect(friendlyErrorMessage(undefined, '课程数据加载失败')).toBe('课程数据加载失败')
    expect(friendlyErrorMessage(new Error('boom'), '课程数据加载失败')).toBe('课程数据加载失败')
  })
})
