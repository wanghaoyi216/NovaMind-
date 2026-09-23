<script setup lang="ts">
import { onMounted, ref } from 'vue'

const show = ref(false)
const leaving = ref(false)
const dots = ref<{ x: string; y: string; d: string }[]>([])

let t1 = 0
let t2 = 0

onMounted(() => {
  if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) return
  if (sessionStorage.getItem('nm-preloaded')) return
  sessionStorage.setItem('nm-preloaded', '1')
  show.value = true
  dots.value = Array.from({ length: 18 }, () => ({
    x: `${Math.round(Math.random() * 100)}%`,
    y: `${Math.round(Math.random() * 100)}%`,
    d: `${(Math.random() * 0.2).toFixed(2)}s`,
  }))
  // 品牌时刻保持轻量：不锁滚动、不阻塞交互，总计约 0.9s
  t1 = window.setTimeout(() => (leaving.value = true), 620)
  t2 = window.setTimeout(() => {
    show.value = false
  }, 1180)
})

function dispose() {
  window.clearTimeout(t1)
  window.clearTimeout(t2)
}

onMounted(() => window.addEventListener('pagehide', dispose, { once: true }))
</script>

<template>
  <div v-if="show" :class="['nm-preload', { leaving }]" aria-hidden="true">
    <div class="nm-preload-stars">
      <span v-for="(p, i) in dots" :key="i" :style="{ '--x': p.x, '--y': p.y, '--d': p.d }"></span>
    </div>
    <div class="nm-preload-core">
      <svg viewBox="0 0 64 64" width="88" height="88" fill="none">
        <defs>
          <linearGradient id="nmpl-a" x1="0" y1="0" x2="64" y2="64">
            <stop stop-color="#d6f1ff" />
            <stop offset="1" stop-color="#e4d0ff" />
          </linearGradient>
        </defs>
        <g transform="rotate(-16 32 33)">
          <ellipse cx="32" cy="33" rx="26" ry="9.4" stroke="#67e8f9" stroke-opacity="0.4" />
          <path d="M58 33 A26 9.4 0 0 0 6 33" stroke="url(#nmpl-a)" stroke-width="1.6" />
        </g>
        <path
          d="M32 12 C33.4 26.8 38.2 31.6 53 33 C38.2 34.4 33.4 39.2 32 54 C30.6 39.2 25.8 34.4 11 33 C25.8 31.6 30.6 26.8 32 12 Z"
          fill="url(#nmpl-a)"
        />
        <path
          d="M32 12 C33.4 26.8 38.2 31.6 53 33 C38.2 34.4 33.4 39.2 32 54 C30.6 39.2 25.8 34.4 11 33 C25.8 31.6 30.6 26.8 32 12 Z"
          fill="#ffffff"
          transform="scale(0.42) translate(44 45)"
        />
      </svg>
      <span class="nm-preload-name">NovaMind 智星云</span>
    </div>
  </div>
</template>

<style scoped lang="scss">
.nm-preload {
  position: fixed;
  inset: 0;
  z-index: 9999;
  display: grid;
  place-items: center;
  background: var(--nm-invert-bg);
  transition: opacity 0.4s var(--nm-ease), clip-path 0.5s var(--nm-ease);
  clip-path: circle(140% at 50% 50%);

  &.leaving {
    opacity: 0;
    clip-path: circle(0% at 50% 50%);
  }
}

.nm-preload-stars span {
  position: absolute;
  left: var(--x);
  top: var(--y);
  width: 3px;
  height: 3px;
  border-radius: 50%;
  background: #d6f1ff;
  box-shadow: 0 0 8px rgba(103, 232, 249, 0.85);
  transition: left 0.6s var(--nm-ease) var(--d), top 0.6s var(--nm-ease) var(--d),
    opacity 0.3s ease 0.45s;
}

.leaving .nm-preload-stars span {
  left: calc(50% - 1.5px);
  top: calc(50% - 1.5px);
  opacity: 0;
}

.nm-preload-core {
  display: grid;
  justify-items: center;
  gap: 14px;
  animation: nm-core-in 0.5s var(--nm-ease-spring) 0.1s both;
}

.nm-preload-name {
  font-family: var(--nm-mono);
  font-size: 13px;
  letter-spacing: 0.38em;
  padding-left: 0.38em;
  color: var(--nm-invert-fg-2);
}

@keyframes nm-core-in {
  from {
    opacity: 0;
    transform: scale(0.55);
    filter: blur(6px);
  }
  to {
    opacity: 1;
    transform: scale(1);
    filter: blur(0);
  }
}

@media (prefers-reduced-motion: reduce) {
  .nm-preload {
    display: none;
  }
}
</style>
