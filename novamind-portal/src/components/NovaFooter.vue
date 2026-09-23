<script setup lang="ts">
import { ArrowUp } from '@element-plus/icons-vue'

const year = new Date().getFullYear()

const columns = [
  {
    title: '学习中心',
    links: [
      { label: '课程中心', to: '/portal/courses' },
      { label: '知识图谱', to: '/portal/knowledge-graph' },
      { label: 'AI 助手', to: '/portal/ai-chat' },
      { label: '学习社区', to: '/portal/community' },
    ],
  },
  {
    title: '学员中心',
    links: [
      { label: '我的课程', to: '/portal/my/lessons' },
      { label: '我的订单', to: '/portal/my/orders' },
      { label: '购物车', to: '/portal/cart' },
      { label: '个人中心', to: '/portal/my/profile' },
    ],
  },
  {
    title: '管理运营',
    links: [
      { label: '运营总览', to: '/admin' },
      { label: '课程管理', to: '/admin/courses' },
      { label: '用户管理', to: '/admin/users' },
      { label: '数据报表', to: '/admin/reports' },
    ],
  },
]

function scrollToTop() {
  window.scrollTo({ top: 0, behavior: 'smooth' })
}
</script>

<template>
  <footer class="nm-footer">
    <div class="nm-shell">
      <div class="nm-footer__top">
        <div class="nm-footer__brand">
          <span class="nm-footer__status">
            <i class="nm-footer__dot" aria-hidden="true"></i>
            微服务体系运行中
          </span>
          <h2 class="nm-h2">NovaMind 智星云</h2>
          <p class="nm-footer__desc">
            从选课、学习到 AI 智能辅导的一站式学习平台，帮助开发者持续提升工程能力与认知深度。
          </p>
          <div class="nm-footer__cta">
            <router-link to="/portal/courses" class="nm-btn nm-btn--primary nm-btn--sm">浏览课程</router-link>
            <router-link to="/portal/ai-chat" class="nm-btn nm-btn--sm">问问 AI 助手</router-link>
          </div>
        </div>

        <nav class="nm-footer__grid" aria-label="页脚导航">
          <div v-for="col in columns" :key="col.title" class="nm-footer__col">
            <h3>{{ col.title }}</h3>
            <router-link v-for="link in col.links" :key="link.to" :to="link.to" class="nm-footer__link">
              {{ link.label }}
            </router-link>
          </div>
        </nav>
      </div>

      <div class="nm-footer__bottom">
        <p class="nm-footer__legal">
          <span>&copy; {{ year }} NovaMind 智星云</span>
          <span class="nm-footer__stack">Spring Cloud 2023 · Spring AI Alibaba · Vue 3.5</span>
        </p>
        <button type="button" class="nm-btn nm-btn--sm nm-footer__top-btn" @click="scrollToTop">
          <el-icon :size="14"><ArrowUp /></el-icon>
          回到顶部
        </button>
      </div>
    </div>
  </footer>
</template>

<style lang="scss" scoped>
.nm-footer {
  margin-top: clamp(24px, 3vw, 40px);
  padding-block: clamp(40px, 5vw, 64px) 28px;
  border-top: 1px solid var(--nm-line);
  background: var(--nm-surface-2);
}

.nm-footer__top {
  display: grid;
  grid-template-columns: minmax(0, 1.1fr) minmax(0, 1.3fr);
  gap: clamp(28px, 4vw, 64px);
}

.nm-footer__status {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  margin-bottom: 16px;
  padding: 4px 11px;
  border: 1px solid var(--nm-success-soft);
  border-radius: var(--nm-r-full);
  background: var(--nm-success-soft);
  color: var(--nm-success);
  font-size: 11.5px;
  font-weight: 650;
  letter-spacing: 0.02em;
}

.nm-footer__dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--nm-success);
  animation: pulseGlow 2.4s var(--nm-ease) infinite;
}

.nm-footer__desc {
  margin-top: 12px;
  max-width: 46ch;
  color: var(--nm-ink-3);
  line-height: 1.7;
}

.nm-footer__cta {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 22px;
}

.nm-footer__grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 24px;
}

.nm-footer__col h3 {
  margin-bottom: 14px;
  font-family: var(--nm-mono);
  font-size: var(--nm-fs-xs);
  font-weight: 600;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: var(--nm-ink-4);
}

.nm-footer__link {
  display: block;
  padding: 5px 0;
  color: var(--nm-ink-2);
  font-size: var(--nm-fs-sm);
  font-weight: 550;
}

.nm-footer__link:hover {
  color: var(--nm-accent);
  transform: translateX(2px);
}

.nm-footer__bottom {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  margin-top: clamp(28px, 4vw, 48px);
  padding-top: 20px;
  border-top: 1px solid var(--nm-line);
}

.nm-footer__legal {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 14px;
  color: var(--nm-ink-3);
  font-size: var(--nm-fs-xs);
}

.nm-footer__stack {
  font-family: var(--nm-mono);
  color: var(--nm-ink-4);
}

.nm-footer__top-btn {
  color: var(--nm-ink-2);
}

@media (max-width: 1000px) {
  .nm-footer__top {
    grid-template-columns: minmax(0, 1fr);
  }
}

@media (max-width: 640px) {
  .nm-footer__grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 18px;
  }
}
</style>
