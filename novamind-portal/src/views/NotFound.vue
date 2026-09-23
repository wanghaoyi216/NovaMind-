<script setup lang="ts">
import { computed, type Component } from 'vue'
import { useRoute } from 'vue-router'
import { ChatDotRound, Reading, User } from '@element-plus/icons-vue'

interface SuggestLink {
  to: string
  label: string
  desc: string
  icon: Component
}

const route = useRoute()

/** 404 会同时命中 /portal/* 与站点根路径，后者没有顶栏，需要不同的高度基准 */
const isPortal = computed(() => route.path.startsWith('/portal'))

const links: SuggestLink[] = [
  {
    to: '/portal/courses',
    label: '课程中心',
    desc: '按方向与难度筛选，找到下一步要学的内容',
    icon: Reading,
  },
  {
    to: '/portal/ai-chat',
    label: 'AI 助手',
    desc: '直接描述你的问题，让它给出讲解与练习',
    icon: ChatDotRound,
  },
  {
    to: '/portal/community',
    label: '学习社区',
    desc: '看看同学在讨论什么，或提出你的疑问',
    icon: User,
  },
]
</script>

<template>
  <div :class="['nf', { 'is-portal': isPortal }]">
    <div class="nf__inner nm-shell">
      <div class="nf__grid">
        <div class="nf__copy">
          <p class="nm-kicker nf__kicker">ERROR · 404</p>
          <h1 class="nf__code">
            404<span class="nf__dot" aria-hidden="true"></span>
          </h1>
          <h2 class="nm-h2 nf__title">这个页面没有找到</h2>
          <p class="nm-lede nf__lede">
            链接可能已经失效、内容被移动，或者地址里少了一个字符。你可以从下面任意一个入口继续，
            学习进度不会因此丢失。
          </p>
          <div class="nf__actions">
            <router-link class="nm-btn nm-btn--primary nm-btn--lg" to="/portal/home">
              返回首页
            </router-link>
            <router-link class="nm-btn nm-btn--lg" to="/portal/courses">浏览课程</router-link>
          </div>
        </div>

        <div class="nf__art nm-card nm-card--quiet" aria-hidden="true">
          <svg class="nf__svg" viewBox="0 0 260 200" fill="none">
            <ellipse
              cx="130"
              cy="100"
              rx="97"
              ry="44"
              stroke="currentColor"
              stroke-width="1.2"
              stroke-dasharray="6 8"
              opacity="0.55"
            />
            <ellipse
              cx="130"
              cy="100"
              rx="97"
              ry="44"
              stroke="currentColor"
              stroke-width="1.2"
              transform="rotate(-26 130 100)"
              opacity="0.3"
            />
            <g style="color: var(--nm-accent)">
              <circle cx="227" cy="100" r="4.2" fill="currentColor" />
              <circle cx="227" cy="100" r="9" stroke="currentColor" stroke-width="1" opacity="0.45" />
            </g>
            <g transform="translate(130 100) scale(3.1) translate(-16 -16)">
              <path
                d="M16 3.5c1.1 8.3 4.2 11.4 12.5 12.5C20.2 17.1 17.1 20.2 16 28.5 14.9 20.2 11.8 17.1 3.5 16 11.8 14.9 14.9 11.8 16 3.5Z"
                fill="currentColor"
                opacity="0.9"
              />
            </g>
            <g stroke="currentColor" stroke-width="1.2" stroke-linecap="round" opacity="0.4">
              <path d="M22 40h9M26.5 35.5v9" />
              <path d="M214 152h8M218 148v8" />
              <path d="M44 166h7M47.5 162.5v7" />
            </g>
          </svg>
          <p class="nf__art-caption nm-num">route not matched</p>
        </div>
      </div>

      <section class="nf__suggest">
        <hr class="nm-divider" />
        <p class="nm-kicker nf__suggest-kicker">或者从这里开始</p>
        <ul class="nf__links nm-grid nm-grid--3">
          <li v-for="link in links" :key="link.to">
            <router-link class="nf__link nm-card nm-card--hover" :to="link.to">
              <span class="nf__link-ico" aria-hidden="true">
                <el-icon :size="17"><component :is="link.icon" /></el-icon>
              </span>
              <span class="nf__link-copy">
                <strong>{{ link.label }}</strong>
                <span>{{ link.desc }}</span>
              </span>
            </router-link>
          </li>
        </ul>
      </section>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.nf {
  display: flex;
  align-items: center;
  min-height: 100vh;
  padding-block: clamp(40px, 6vw, 88px);
  background: var(--nm-paper);
}

.nf.is-portal {
  min-height: calc(100vh - 88px);
}

.nf__inner {
  width: 100%;
}

.nf__grid {
  display: grid;
  grid-template-columns: minmax(0, 1.1fr) minmax(0, 0.9fr);
  gap: clamp(28px, 5vw, 72px);
  align-items: center;
}

.nf__kicker {
  margin-bottom: 18px;
}

.nf__code {
  display: flex;
  align-items: flex-end;
  gap: 10px;
  font-size: clamp(4.5rem, 12vw, 8.5rem);
  font-weight: 800;
  line-height: 0.86;
  letter-spacing: -0.06em;
  color: var(--nm-ink);
}

.nf__dot {
  width: 0.11em;
  height: 0.11em;
  margin-bottom: 0.16em;
  border-radius: var(--nm-r-full);
  background: var(--nm-accent);
  flex: none;
}

.nf__title {
  margin: 20px 0 14px;
}

.nf__lede {
  margin-bottom: 28px;
}

.nf__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.nf__art {
  display: grid;
  justify-items: center;
  gap: 14px;
  padding: clamp(24px, 3.4vw, 42px);
  color: var(--nm-ink-4);
}

.nf__svg {
  width: 100%;
  max-width: 340px;
  height: auto;
}

.nf__art-caption {
  font-size: var(--nm-fs-xs);
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: var(--nm-ink-4);
}

.nf__suggest {
  margin-top: clamp(40px, 6vw, 80px);
}

.nf__suggest-kicker {
  margin: 22px 0 18px;
}

.nf__links {
  list-style: none;
  margin: 0;
  padding: 0;
}

.nf__link {
  display: flex;
  align-items: flex-start;
  gap: 14px;
  height: 100%;
  padding: 20px;
}

.nf__link-ico {
  width: 38px;
  height: 38px;
  flex: none;
  display: grid;
  place-items: center;
  border-radius: var(--nm-r-sm);
  background: var(--nm-accent-soft);
  color: var(--nm-accent);
}

.nf__link-copy {
  display: grid;
  gap: 4px;
}

.nf__link-copy strong {
  font-size: var(--nm-fs-body);
  font-weight: 700;
  color: var(--nm-ink);
}

.nf__link-copy span {
  font-size: var(--nm-fs-sm);
  line-height: 1.55;
  color: var(--nm-ink-3);
}

@media (max-width: 900px) {
  .nf__grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .nf__art {
    order: -1;
    padding: 20px;
  }

  .nf__svg {
    max-width: 240px;
  }
}

@media (max-width: 560px) {
  .nf__actions {
    flex-direction: column;
    align-items: stretch;
  }

  .nf__actions .nm-btn {
    width: 100%;
  }
}
</style>
