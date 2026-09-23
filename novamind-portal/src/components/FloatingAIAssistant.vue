<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const router = useRouter()
const route = useRoute()
const wave = ref(false)
// 气泡改为「点击悬浮球后弹出」：首次点击弹气泡，再次点击球或点气泡进入 AI 助手
const bubbleOpen = ref(false)
const message = ref('')

const messages = [
  '有什么想学的？',
  '我来帮你规划学习路线',
  '需要课程推荐吗？',
  '随时为你解答问题',
]

const visible = computed(() => {
  const path = route.path
  if (!path.startsWith('/portal')) {
    return false
  }
  return !['/portal/login', '/portal/register', '/portal/ai-chat'].includes(path)
})

let bubbleTimer: ReturnType<typeof setTimeout>
let waveTimer: ReturnType<typeof setInterval>
let bubbleIndex = 0

function openAssistant() {
  hideBubble()
  router.push({
    path: '/portal/ai-chat',
    query: { prompt: '你好，我想了解学习平台的功能和使用方法', autostart: '1' },
  })
}

function showBubble() {
  message.value = messages[bubbleIndex % messages.length]
  bubbleIndex += 1
  bubbleOpen.value = true
  clearTimeout(bubbleTimer)
  bubbleTimer = setTimeout(hideBubble, 4500)
}

function hideBubble() {
  bubbleOpen.value = false
  clearTimeout(bubbleTimer)
}

function handleBallClick() {
  if (!bubbleOpen.value) {
    showBubble()
    return
  }
  openAssistant()
}

function startWave() {
  // 球体轻微摆动提示可点击（不弹气泡，不遮挡内容）
  waveTimer = setInterval(() => {
    wave.value = true
    setTimeout(() => { wave.value = false }, 600)
  }, 8000)
}

onMounted(() => startWave())
onUnmounted(() => {
  clearInterval(waveTimer)
  clearTimeout(bubbleTimer)
})
</script>

<template>
  <div v-if="visible" class="ai-floating-wrapper">
    <transition name="msg-fade">
      <button
        v-if="bubbleOpen"
        type="button"
        class="ai-speech-bubble"
        title="打开 AI 助手"
        @click="openAssistant"
      >
        {{ message }}
      </button>
    </transition>
    <button type="button" class="ai-floating" @click="handleBallClick" aria-label="AI 助手">
      <span class="ai-orb" :class="{ wave }" aria-hidden="true">
        <!-- 星核 + 轨道标（内联 novamind-mark 图形，与导航一致；id 前缀 nm-orb 避免与 SystemIcon 冲突） -->
        <svg viewBox="0 0 64 64" width="30" height="30" fill="none" xmlns="http://www.w3.org/2000/svg">
          <defs>
            <linearGradient id="nm-orb-orbit" x1="0" y1="0" x2="64" y2="64" gradientUnits="userSpaceOnUse">
              <stop stop-color="#67e8f9" stop-opacity="0.9" />
              <stop offset="1" stop-color="#8b5cf6" stop-opacity="0.9" />
            </linearGradient>
            <radialGradient id="nm-orb-glow" cx="0.5" cy="0.5" r="0.5">
              <stop stop-color="#67e8f9" stop-opacity="0.45" />
              <stop offset="0.55" stop-color="#8b5cf6" stop-opacity="0.18" />
              <stop offset="1" stop-color="#8b5cf6" stop-opacity="0" />
            </radialGradient>
            <linearGradient id="nm-orb-body" x1="0" y1="0" x2="64" y2="64" gradientUnits="userSpaceOnUse">
              <stop stop-color="#d6f1ff" />
              <stop offset="1" stop-color="#e4d0ff" />
            </linearGradient>
            <filter id="nm-orb-b1" x="-30%" y="-30%" width="160%" height="160%">
              <feGaussianBlur stdDeviation="1" />
            </filter>
          </defs>
          <circle cx="32" cy="33" r="26" fill="url(#nm-orb-glow)" />
          <g transform="rotate(-16 32 33)">
            <ellipse cx="32" cy="33" rx="26" ry="9.4" stroke="url(#nm-orb-orbit)" stroke-width="1.6" stroke-opacity="0.55" />
            <path d="M58 33 A26 9.4 0 0 0 6 33" stroke="url(#nm-orb-orbit)" stroke-width="2" stroke-opacity="0.95" stroke-linecap="round" />
            <g transform="translate(57.2 32.2)">
              <circle r="4.6" fill="#9be7ff" opacity="0.3" />
              <circle r="2.2" fill="#ffffff" />
            </g>
          </g>
          <g transform="translate(32 33)">
            <path d="M0 -21 C1.4 -6 6 -1.4 21 0 C6 1.4 1.4 6 0 21 C-1.4 6 -6 1.4 -21 0 C-6 -1.4 -1.4 -6 0 -21 Z" fill="#67e8f9" opacity="0.28" filter="url(#nm-orb-b1)" transform="translate(-1.4 -0.6)" />
            <path d="M0 -21 C1.4 -6 6 -1.4 21 0 C6 1.4 1.4 6 0 21 C-1.4 6 -6 1.4 -21 0 C-6 -1.4 -1.4 -6 0 -21 Z" fill="#e879f9" opacity="0.2" filter="url(#nm-orb-b1)" transform="translate(1.4 1)" />
            <path d="M0 -21 C1.4 -6 6 -1.4 21 0 C6 1.4 1.4 6 0 21 C-1.4 6 -6 1.4 -21 0 C-6 -1.4 -1.4 -6 0 -21 Z" fill="url(#nm-orb-body)" filter="url(#nm-orb-b1)" />
            <path d="M0 -21 C1.4 -6 6 -1.4 21 0 C6 1.4 1.4 6 0 21 C-1.4 6 -6 1.4 -21 0 C-6 -1.4 -1.4 -6 0 -21 Z" fill="#ffffff" transform="scale(0.48)" />
            <g stroke="#ffffff" stroke-linecap="round" opacity="0.5">
              <line x1="-26" y1="0" x2="26" y2="0" stroke-width="1" opacity="0.35" />
              <line x1="0" y1="-13" x2="0" y2="13" stroke-width="1" opacity="0.22" />
            </g>
          </g>
          <path fill="#ffffff" opacity="0.9" d="M50.5 9.5 C50.8 11.6 51.5 12.3 53.6 12.6 C51.5 12.9 50.8 13.6 50.5 15.7 C50.2 13.6 49.5 12.9 47.4 12.6 C49.5 12.3 50.2 11.6 50.5 9.5 Z" />
          <circle cx="12.5" cy="13" r="1.1" fill="#ffffff" opacity="0.55" />
        </svg>
      </span>
      <span class="ai-label">AI 助手</span>
    </button>
  </div>
</template>

<style lang="scss" scoped>


.ai-floating-wrapper {
  position: fixed;
  right: 20px;
  bottom: calc(20px + env(safe-area-inset-bottom, 0px));
  z-index: 1200;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.ai-speech-bubble {
  background: var(--nm-surface);
  border: 1px solid var(--nm-line);
  border-radius: var(--nm-r-lg);
  padding: 9px 15px;
  font-size: var(--nm-fs-sm);
  font-weight: 550;
  color: var(--nm-ink);
  box-shadow: var(--nm-sh-3);
  margin-bottom: 8px;
  white-space: nowrap;
  position: relative;
  cursor: pointer;
  transition: border-color var(--nm-dur-fast) var(--nm-ease), box-shadow var(--nm-dur-fast) var(--nm-ease);

  &:hover {
    border-color: var(--nm-accent-line);
    box-shadow: var(--nm-sh-4);
  }

  &::after {
    content: '';
    position: absolute;
    bottom: -5px;
    left: 50%;
    width: 9px;
    height: 9px;
    background: var(--nm-surface);
    border-right: 1px solid var(--nm-line);
    border-bottom: 1px solid var(--nm-line);
    border-radius: 0 0 3px 0;
    transform: translateX(-50%) rotate(45deg);
  }
}

.ai-floating {
  width: 60px;
  height: 66px;
  border: 1px solid var(--nm-line);
  border-radius: var(--nm-r-lg);
  background: var(--nm-invert-bg);
  box-shadow: var(--nm-sh-3);
  display: inline-flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 3px;
  cursor: pointer;
  transition: transform var(--nm-dur) var(--nm-ease-spring), box-shadow var(--nm-dur) var(--nm-ease);

  &:hover {
    transform: translateY(-3px) scale(1.04);
    box-shadow: var(--nm-sh-4);
  }

  &:active {
    transform: translateY(0) scale(0.97);
  }
}

.ai-orb {
  display: inline-flex;
  color: var(--nm-invert-fg);
  transition: transform var(--nm-dur) var(--nm-ease);

  svg {
    width: 28px;
    height: 28px;
  }

  &.wave {
    transform: rotate(-12deg) scale(1.12);
  }
}

.ai-label {
  font-family: var(--nm-mono);
  font-size: 9.5px;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: var(--nm-invert-fg-2);
  font-weight: 600;
}

/* 以动画实现进入态，避免转场类残留导致气泡不可见 */
.msg-fade-enter-active {
  animation: msg-in 0.24s var(--nm-ease) both;
}

.msg-fade-leave-active {
  animation: msg-in 0.18s var(--nm-ease) reverse both;
}

@keyframes msg-in {
  from {
    opacity: 0;
    transform: translateY(8px);
  }
  to {
    opacity: 1;
    transform: none;
  }
}

@media (max-width: 768px) {
  .ai-floating-wrapper {
    right: 12px;
    bottom: calc(12px + env(safe-area-inset-bottom, 0px));
  }

  .ai-floating {
    width: 48px;
    height: 52px;
    border-radius: var(--nm-r);
    gap: 2px;
  }

  .ai-orb svg {
    width: 21px;
    height: 21px;
  }

  .ai-label {
    display: none;
  }

  .ai-speech-bubble {
    font-size: var(--nm-fs-xs);
    padding: 7px 12px;
    white-space: normal;
    max-width: 190px;
    text-align: left;
  }
}
</style>
