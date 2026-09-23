<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'

const progress = ref(0)

function onScroll() {
  const doc = document.documentElement
  const max = doc.scrollHeight - window.innerHeight
  progress.value = max > 0 ? Math.min(1, window.scrollY / max) : 0
}

let raf = 0

function schedule() {
  cancelAnimationFrame(raf)
  raf = requestAnimationFrame(onScroll)
}

onMounted(() => {
  window.addEventListener('scroll', schedule, { passive: true })
  window.addEventListener('resize', schedule, { passive: true })
  onScroll()
})

onBeforeUnmount(() => {
  cancelAnimationFrame(raf)
  window.removeEventListener('scroll', schedule)
  window.removeEventListener('resize', schedule)
})
</script>

<template>
  <div class="nm-progress" aria-hidden="true">
    <i :style="{ transform: `scaleX(${progress})` }"></i>
  </div>
</template>

<style scoped lang="scss">
.nm-progress {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  height: 2px;
  z-index: 9600;
  pointer-events: none;
  background: transparent;

  i {
    display: block;
    height: 100%;
    transform-origin: 0 50%;
    transform: scaleX(0);
    background: var(--nm-accent);
  }
}
</style>
