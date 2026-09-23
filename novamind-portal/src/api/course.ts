import request from '@/utils/request'
import type { ApiResponse, CourseDetailInfo, CourseSimpleInfo, RecommendCourse, CategoryItem, PageResult } from '@/types'

export function getCourseBaseInfo(id: number) {
  return request.get<ApiResponse<CourseDetailInfo>>(`/cs/courses/baseInfo/${id}`)
}

export function getCourseCatalogs(courseId: number) {
  return request.get<ApiResponse<any>>(`/cs/courses/catas/${courseId}`)
}

export function getCourseTeachers(courseId: number) {
  return request.get<ApiResponse<any>>(`/cs/courses/teachers/${courseId}`)
}

export function getCourseCatalogsWithProgress(id: number) {
  return request.get<ApiResponse<any>>(`/cs/courses/${id}/catalogs`)
}

export function searchPortalCourses(params: Record<string, unknown>) {
  return request.get<ApiResponse<PageResult<CourseSimpleInfo>>>('/ss/courses/portal', { params })
}

export function getCategoryList() {
  return request.get<ApiResponse<CategoryItem[]>>('/cs/categorys/list')
}

export function getAllCategories() {
  return request.get<ApiResponse<CategoryItem[]>>('/cs/categorys/all')
}

export function getRecommendBest() {
  return request.get<ApiResponse<RecommendCourse[]>>('/ss/recommend/best')
}

export function getRecommendNew() {
  return request.get<ApiResponse<RecommendCourse[]>>('/ss/recommend/new')
}

export function getRecommendFree() {
  return request.get<ApiResponse<RecommendCourse[]>>('/ss/recommend/free')
}

export function getCoursesByInterest(id: number) {
  return request.get<ApiResponse<RecommendCourse[]>>(`/ss/interests/${id}/courses`)
}

export function upShelf(courseId: number) {
  return request.post<ApiResponse<void>>('/cs/courses/upShelf', { courseId })
}

export function downShelf(courseId: number) {
  return request.post<ApiResponse<void>>('/cs/courses/downShelf', { courseId })
}

export function checkBeforeUpShelf(id: number) {
  return request.get<ApiResponse<void>>(`/cs/courses/checkBeforeUpShelf/${id}`)
}
