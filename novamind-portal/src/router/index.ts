import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import NProgress from 'nprogress'
import 'nprogress/nprogress.css'

NProgress.configure({ showSpinner: false, trickleSpeed: 200 })

const portalChildren: RouteRecordRaw[] = [
  {
    path: 'home',
    name: 'PortalHome',
    component: () => import('@/views/Home.vue'),
    meta: { title: '首页' },
  },
  {
    path: 'courses',
    name: 'PortalCourseList',
    component: () => import('@/views/CourseList.vue'),
    meta: { title: '课程中心' },
  },
  {
    path: 'course/:id',
    name: 'PortalCourseDetail',
    component: () => import('@/views/CourseDetail.vue'),
    meta: { title: '课程详情' },
  },
  {
    path: 'consumer',
    name: 'PortalConsumerHub',
    component: () => import('@/views/ConsumerHub.vue'),
    meta: { title: '学习导航' },
  },
  {
    path: 'cart',
    name: 'PortalCart',
    component: () => import('@/views/Cart.vue'),
    meta: { title: '购物车', requiresAuth: true },
  },
  {
    path: 'knowledge-graph',
    name: 'PortalKnowledgeGraph',
    component: () => import('@/views/KnowledgeGraph.vue'),
    meta: { title: '知识图谱' },
  },
  {
    path: 'community',
    name: 'PortalCommunity',
    component: () => import('@/views/CommunityHub.vue'),
    meta: { title: '学习社区' },
  },
  {
    path: 'ai-chat',
    name: 'PortalAIChat',
    component: () => import('@/views/AIChat.vue'),
    meta: { title: 'AI 助手', hideFooter: true },
  },
  {
    path: 'login',
    name: 'PortalLogin',
    component: () => import('@/views/Login.vue'),
    meta: { title: '登录', guest: true },
  },
  {
    path: 'register',
    name: 'PortalRegister',
    component: () => import('@/views/Register.vue'),
    meta: { title: '注册', guest: true },
  },
  {
    path: 'pay/:orderId',
    name: 'PortalPayResult',
    component: () => import('@/views/PayResult.vue'),
    meta: { title: '支付结果', requiresAuth: true },
  },
]

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    redirect: '/portal/home',
  },
  {
    path: '/portal',
    children: [
      {
        path: '',
        redirect: '/portal/home',
      },
      ...portalChildren,
      {
        path: 'my',
        component: () => import('@/layouts/MyLayout.vue'),
        meta: { requiresAuth: true },
        children: [
          {
            path: '',
            redirect: '/portal/my/lessons',
          },
          {
            path: 'lessons',
            name: 'PortalMyLessons',
            component: () => import('@/views/my/Lessons.vue'),
            meta: { title: '我的课程' },
          },
          {
            path: 'orders',
            name: 'PortalMyOrders',
            component: () => import('@/views/my/Orders.vue'),
            meta: { title: '我的订单' },
          },
          {
            path: 'coupons',
            name: 'PortalMyCoupons',
            component: () => import('@/views/my/Coupons.vue'),
            meta: { title: '我的优惠券' },
          },
          {
            path: 'messages',
            name: 'PortalMyMessages',
            component: () => import('@/views/my/Messages.vue'),
            meta: { title: '消息中心' },
          },
          {
            path: 'profile',
            name: 'PortalMyProfile',
            component: () => import('@/views/my/Profile.vue'),
            meta: { title: '个人中心' },
          },
        ],
      },
    ],
  },
  {
    path: '/admin',
    component: () => import('@/layouts/AdminLayout.vue'),
    meta: { title: '管理后台', requiresAuth: true },
    children: [
      {
        path: '',
        name: 'AdminConsole',
        component: () => import('@/views/admin/Overview.vue'),
        meta: { title: '总览' },
      },
      {
        path: 'courses',
        name: 'AdminCourses',
        component: () => import('@/views/admin/CourseManage.vue'),
        meta: { title: '课程管理' },
      },
      {
        path: 'users',
        name: 'AdminUsers',
        component: () => import('@/views/admin/UserManage.vue'),
        meta: { title: '用户管理' },
      },
      {
        path: 'ai',
        name: 'AdminAI',
        component: () => import('@/views/admin/AIManage.vue'),
        meta: { title: 'AI 管理' },
      },
      {
        path: 'reports',
        name: 'AdminReports',
        component: () => import('@/views/admin/DataReports.vue'),
        meta: { title: '数据报表' },
      },
    ],
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/NotFound.vue'),
    meta: { title: '页面未找到' },
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 }),
})

router.beforeEach((to, _from, next) => {
  NProgress.start()
  document.title = `${to.meta.title || ''} - NovaMind`
  const token = localStorage.getItem('nova_token')
  if (to.meta.requiresAuth && !token) {
    next({ path: '/portal/login', query: { redirect: to.fullPath } })
  } else {
    next()
  }
})

router.afterEach(() => {
  NProgress.done()
})

export default router
