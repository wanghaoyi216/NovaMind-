import request from '@/utils/request'
import type { ApiResponse, LoginRequest, RegisterRequest, UserInfo } from '@/types'

export function login(data: LoginRequest) {
  return request.post<ApiResponse<string>>('/as/accounts/login', data)
}

export function adminLogin(data: LoginRequest) {
  return request.post<ApiResponse<string>>('/as/accounts/admin/login', data)
}

export function logout() {
  return request.post<ApiResponse<void>>('/as/accounts/logout')
}

export function getUserInfo() {
  return request.get<ApiResponse<UserInfo>>('/us/users/me')
}

export function refreshToken() {
  return request.get<ApiResponse<string>>('/as/accounts/refresh')
}

export function register(data: RegisterRequest) {
  return request.post<ApiResponse<void>>('/us/students/register', data)
}
