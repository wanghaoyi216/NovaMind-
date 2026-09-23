<script setup lang="ts">
import { computed } from 'vue'

interface Props {
  name: 'brand' | 'assistant' | 'course' | 'admin' | 'user'
  size?: number
}

interface OrbitCircle {
  cx: number
  cy: number
}

const props = withDefaults(defineProps<Props>(), {
  size: 20,
})

// 品牌字形 = 内联 novamind-mark（星核 + 椭圆轨道 + 彗星）：
// 星体/十字光/辅星走 currentColor 以对接容器配色（如页头紫色芯片上的白、侧栏文本色），
// 轨道与光晕保留品牌青→紫渐变。其余图标保持原 currentColor 填充。
const isBrand = computed(() => props.name === 'brand')

const svg = computed(() => {
  switch (props.name) {
    case 'brand':
      return { viewBox: '0 0 64 64', path: '', circles: [] as OrbitCircle[] }
    case 'assistant':
      return {
        viewBox: '0 0 24 24',
        path: 'M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 18c-4.41 0-8-3.59-8-8s3.59-8 8-8 8 3.59 8 8-3.59 8-8 8zm-1-13h2v6h-2zm0 8h2v2h-2z',
        circles: [] as OrbitCircle[],
      }
    case 'course':
      return {
        viewBox: '0 0 24 24',
        path: 'M4 6H2v14c0 1.1.9 2 2 2h14v-2H4V6zm16-4H8c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zm0 14H8V4h12v12zM10 9h8v2h-8zm0 3h4v2h-4zm0-6h8v2h-8z',
        circles: [] as OrbitCircle[],
      }
    case 'admin':
      return {
        viewBox: '0 0 24 24',
        path: 'M12 1L3 5v6c0 5.55 3.84 10.74 9 12 5.16-1.26 9-6.45 9-12V5l-9-4zm0 10.99h7c-.53 4.12-3.28 7.79-7 8.94V12H5V6.3l7-3.11v8.8z',
        circles: [] as OrbitCircle[],
      }
    case 'user':
      return {
        viewBox: '0 0 24 24',
        path: 'M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z',
        circles: [] as OrbitCircle[],
      }
    default:
      return { viewBox: '0 0 24 24', path: '', circles: [] as OrbitCircle[] }
  }
})

const dimension = computed(() => `${props.size}px`)
</script>

<template>
  <svg
    class="system-icon"
    :width="dimension"
    :height="dimension"
    :viewBox="svg.viewBox"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
  >
    <template v-if="isBrand">
      <defs>
        <linearGradient id="nm-mark-orbit" x1="0" y1="0" x2="64" y2="64" gradientUnits="userSpaceOnUse">
          <stop stop-color="#67e8f9" stop-opacity="0.9" />
          <stop offset="1" stop-color="#8b5cf6" stop-opacity="0.9" />
        </linearGradient>
        <radialGradient id="nm-mark-glow" cx="0.5" cy="0.5" r="0.5">
          <stop stop-color="#67e8f9" stop-opacity="0.45" />
          <stop offset="0.55" stop-color="#8b5cf6" stop-opacity="0.18" />
          <stop offset="1" stop-color="#8b5cf6" stop-opacity="0" />
        </radialGradient>
        <linearGradient id="nm-mark-body" x1="0" y1="0" x2="64" y2="64" gradientUnits="userSpaceOnUse">
          <stop stop-color="currentColor" stop-opacity="0.82" />
          <stop offset="1" stop-color="currentColor" />
        </linearGradient>
        <filter id="nm-mark-b1" x="-30%" y="-30%" width="160%" height="160%">
          <feGaussianBlur stdDeviation="1" />
        </filter>
      </defs>

      <!-- 星核光晕 -->
      <circle cx="32" cy="33" r="26" fill="url(#nm-mark-glow)" />
      <!-- 主轨道（呼应应用图标的 -16° 椭圆轨道） -->
      <g transform="rotate(-16 32 33)">
        <ellipse cx="32" cy="33" rx="26" ry="9.4" stroke="url(#nm-mark-orbit)" stroke-width="1.6" stroke-opacity="0.55" />
        <path d="M58 33 A26 9.4 0 0 0 6 33" stroke="url(#nm-mark-orbit)" stroke-width="2" stroke-opacity="0.95" stroke-linecap="round" />
        <!-- 轨道彗星 -->
        <g transform="translate(57.2 32.2)">
          <circle r="4.6" fill="#9be7ff" opacity="0.3" />
          <circle r="2.2" fill="currentColor" />
        </g>
      </g>
      <!-- 四芒星核（与 novamind-icon 同构：星体+内核+十字光） -->
      <g transform="translate(32 33)">
        <path
          d="M0 -21 C1.4 -6 6 -1.4 21 0 C6 1.4 1.4 6 0 21 C-1.4 6 -6 1.4 -21 0 C-6 -1.4 -1.4 -6 0 -21 Z"
          fill="#67e8f9"
          opacity="0.28"
          filter="url(#nm-mark-b1)"
          transform="translate(-1.4 -0.6)"
        />
        <path
          d="M0 -21 C1.4 -6 6 -1.4 21 0 C6 1.4 1.4 6 0 21 C-1.4 6 -6 1.4 -21 0 C-6 -1.4 -1.4 -6 0 -21 Z"
          fill="#e879f9"
          opacity="0.2"
          filter="url(#nm-mark-b1)"
          transform="translate(1.4 1)"
        />
        <path
          d="M0 -21 C1.4 -6 6 -1.4 21 0 C6 1.4 1.4 6 0 21 C-1.4 6 -6 1.4 -21 0 C-6 -1.4 -1.4 -6 0 -21 Z"
          fill="url(#nm-mark-body)"
          filter="url(#nm-mark-b1)"
        />
        <path
          d="M0 -21 C1.4 -6 6 -1.4 21 0 C6 1.4 1.4 6 0 21 C-1.4 6 -6 1.4 -21 0 C-6 -1.4 -1.4 -6 0 -21 Z"
          fill="currentColor"
          transform="scale(0.48)"
          opacity="0.92"
        />
        <g stroke="currentColor" stroke-linecap="round" opacity="0.5">
          <line x1="-26" y1="0" x2="26" y2="0" stroke-width="1" opacity="0.35" />
          <line x1="0" y1="-13" x2="0" y2="13" stroke-width="1" opacity="0.22" />
        </g>
      </g>
      <!-- 辅星 -->
      <path
        fill="currentColor"
        opacity="0.9"
        d="M50.5 9.5 C50.8 11.6 51.5 12.3 53.6 12.6 C51.5 12.9 50.8 13.6 50.5 15.7 C50.2 13.6 49.5 12.9 47.4 12.6 C49.5 12.3 50.2 11.6 50.5 9.5 Z"
      />
      <circle cx="12.5" cy="13" r="1.1" fill="currentColor" opacity="0.55" />
    </template>

    <template v-else>
      <path v-if="svg.path" :d="svg.path" fill="currentColor" />
      <circle
        v-for="(c, i) in svg.circles"
        :key="i"
        :cx="c.cx"
        :cy="c.cy"
        r="1.2"
        fill="#e0e7ff"
        opacity="0.5"
      />
    </template>
  </svg>
</template>

<style scoped>
.system-icon {
  display: inline-block;
  flex-shrink: 0;
}
</style>
