import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import App from './App.vue'
import router from './router'
import './styles/design-system.scss'
import { applyInitialTheme } from './utils/theme'
import { registerSW } from 'virtual:pwa-register'

// 在挂载前落定主题，避免首帧出现浅色闪屏 / 主题与样式不一致
applyInitialTheme()

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)
app.use(router)
app.use(ElementPlus, {
  size: 'default',
})

// 注册 PWA Service Worker（autoUpdate 模式：有新版就提示）
if ('serviceWorker' in navigator) {
  registerSW({
    immediate: true,
    onRegisteredSW(swScriptUrl: string, registration: ServiceWorkerRegistration | undefined) {
      // eslint-disable-next-line no-console
      console.log('[PWA] SW registered:', swScriptUrl, registration)
    },
    onRegisterError(err: unknown) {
      // eslint-disable-next-line no-console
      console.warn('[PWA] SW register failed:', err)
    },
  })
}

app.mount('#app')
