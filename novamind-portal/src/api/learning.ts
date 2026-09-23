import request from '@/utils/request'
import type { ApiResponse, PageResult } from '@/types'

export interface LearningLesson {
  id: number
  userId: number
  courseId: number
  courseName: string
  coverImg: string
  lastSectionId: number
  lastSectionName: string
  learnDate: string
  totalDuration: number
  studiedDuration: number
  finish: boolean
}

export function getMyLessons(params: Record<string, unknown>) {
  return request.get<ApiResponse<PageResult<LearningLesson>>>('/ls/lessons/page', { params })
}

export function getNowLearning() {
  return request.get<ApiResponse<LearningLesson>>('/ls/lessons/now')
}

export function getLessonCourseInfo(courseId: number) {
  return request.get<ApiResponse<any>>(`/ls/lessons/${courseId}`)
}

export function deleteLesson(courseId: number) {
  return request.delete<ApiResponse<void>>(`/ls/lessons/${courseId}`)
}

export function countLessonLearners(courseId: number) {
  return request.get<ApiResponse<number>>(`/ls/lessons/${courseId}/count`)
}

export function checkCourseEnrolled(courseId: number) {
  return request.get<ApiResponse<boolean>>(`/ls/lessons/${courseId}/valid`)
}

export function createLearningPlan(data: Record<string, unknown>) {
  return request.post<ApiResponse<void>>('/ls/lessons/plans', data)
}

export function getMyLearningPlans() {
  return request.get<ApiResponse<any>>('/ls/lessons/plans')
}
