export interface UserInfo {
  id: number
  username: string
  phone: string
  avatar: string
  userType: number
  token: string
}

export interface CourseSimpleInfo {
  id: number
  name: string
  coverImg: string
  teacherName: string
  price: number
  sales: number
  categoryId: number
  categoryName: string
}

export interface CourseDetailInfo extends CourseSimpleInfo {
  introduction: string
  catalogs: CatalogItem[]
  teachers: TeacherInfo[]
}

export interface CatalogItem {
  id: number
  courseId: number
  name: string
  orderNum: number
  children?: SectionItem[]
}

export interface SectionItem {
  id: number
  catalogId: number
  name: string
  orderNum: number
  mediaId: number
  duration: number
  free: boolean
  videoType: number
}

export interface TeacherInfo {
  id: number
  name: string
  intro: string
  title: string
  avatar: string
  courseId: number
}

export interface CategoryItem {
  id: number
  pid: number
  name: string
  icon: string
  level: number
  children?: CategoryItem[]
}

export interface RecommendCourse extends CourseSimpleInfo {
  description: string
}

export interface InterestItem {
  id: number
  name: string
}

/**
 * 登录表单。字段名必须与后端 `com.novamind.api.dto.user.LoginFormDTO` 一致：
 * `type` 1=密码登录 2=验证码登录；账号走 `cellPhone`（手机号）或 `username`（用户名），
 * 后端没有 `phone` 字段——传错会被当成 null 并触发下游调用异常。
 */
export interface LoginRequest {
  type: number
  password: string
  cellPhone?: string
  username?: string
  rememberMe?: boolean
}

/**
 * 注册表单。字段名必须与后端 `com.novamind.user.domain.dto.StudentFormDTO` 一致。
 */
export interface RegisterRequest {
  cellPhone: string
  password: string
  code: string
}

export interface PageResult<T> {
  list: T[]
  total: number
  pageNo: number
  pageSize: number
}

export interface PageQuery {
  pageNo: number
  pageSize: number
}

export interface ApiResponse<T> {
  code: number
  msg: string
  data: T
}

// Export all chat types
export * from './chat'

// Export all theme types
export * from './theme'

// Export all asset types
export * from './assets'
