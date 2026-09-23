import request from '@/utils/request'
import type { ApiResponse, PageResult } from '@/types'

export interface CartItem {
  id: number
  courseId: number
  courseName: string
  coverImg: string
  price: number
  teacherName: string
}

export function addToCart(courseId: number) {
  return request.post<ApiResponse<void>>('/ts/carts', { courseId })
}

export function getCart() {
  return request.get<ApiResponse<CartItem[]>>('/ts/carts')
}

export function removeFromCart(id: number) {
  return request.delete<ApiResponse<void>>(`/ts/carts/${id}`)
}

export function batchRemoveFromCart(ids: number[]) {
  return request.delete<ApiResponse<void>>('/ts/carts', { data: { ids } })
}

export interface OrderItem {
  id: number
  orderId: string
  courseName: string
  coverImg: string
  price: number
  status: number
  payTime: string
}

export function getOrders(params: Record<string, unknown>) {
  return request.get<ApiResponse<PageResult<OrderItem>>>('/ts/orders/page', { params })
}

export function getOrderDetail(id: number) {
  return request.get<ApiResponse<any>>(`/ts/orders/${id}`)
}

export function getOrderStatus(id: number) {
  return request.get<ApiResponse<number>>(`/ts/orders/${id}/status`)
}

export function prePlaceOrder(orderIds?: number[]) {
  return request.get<ApiResponse<any>>('/ts/orders/prePlaceOrder', { params: { orderIds } })
}

export function placeOrder(data: Record<string, unknown>) {
  return request.post<ApiResponse<string>>('/ts/orders/placeOrder', data)
}

export function enrollFreeCourse(courseId: number) {
  return request.post<ApiResponse<void>>(`/ts/orders/freeCourse/${courseId}`)
}

export function cancelOrder(id: number) {
  return request.put<ApiResponse<void>>(`/ts/orders/${id}/cancel`)
}

export function deleteOrder(id: number) {
  return request.delete<ApiResponse<void>>(`/ts/orders/${id}`)
}

export function payOrder(data: Record<string, unknown>) {
  return request.post<ApiResponse<string>>('/ts/pay/order', data)
}

export function getPayChannels() {
  return request.get<ApiResponse<any[]>>('/ts/pay/channels')
}
