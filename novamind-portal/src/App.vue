<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import NovaHeader from '@/components/NovaHeader.vue'
import NovaFooter from '@/components/NovaFooter.vue'
import FloatingAIAssistant from '@/components/FloatingAIAssistant.vue'
import OfflineBanner from '@/components/OfflineBanner.vue'
import StarPreloader from '@/components/nova/StarPreloader.vue'
import NovaScrollProgress from '@/components/nova/NovaScrollProgress.vue'

const route = useRoute()
const isPortalRoute = computed(() => route.path.startsWith('/portal'))
const showFooter = computed(() => isPortalRoute.value && route.meta.hideFooter !== true)
const showAssistant = computed(() => route.path !== '/portal/ai-chat')
</script>

<template>
  <div class="app-layout">
    <StarPreloader />
    <NovaScrollProgress />
    <OfflineBanner />
    <a class="skip-link" href="#main-content">跳到主要内容</a>
    <NovaHeader v-if="isPortalRoute" />
    <main id="main-content" :class="['app-main', { portal: isPortalRoute }]">
      <router-view />
    </main>
    <NovaFooter v-if="showFooter" />
    <FloatingAIAssistant v-if="showAssistant" />
  </div>
</template>

<style lang="scss" scoped>
.app-layout {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.app-main {
  flex: 1;
}

/* 固定顶栏高度补偿：桌面 76px / 移动 64px */
.app-main.portal {
  padding-top: 88px;
}

.skip-link {
  position: absolute;
  left: -9999px;
  top: 0;
  z-index: 2000;
  padding: 10px 18px;
  border-radius: var(--nm-r);
  background: var(--nm-ink);
  color: var(--nm-paper);
  font-weight: 600;
}

.skip-link:focus {
  left: 16px;
  top: 16px;
}

@media (max-width: 768px) {
  .app-main.portal {
    padding-top: 76px;
    /* 为悬浮 AI 助手留出底部空间 */
    padding-bottom: 96px;
  }
}
</style>
