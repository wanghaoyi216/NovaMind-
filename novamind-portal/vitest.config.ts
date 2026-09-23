import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'
import { VitePWA } from 'vite-plugin-pwa'

export default defineConfig({
  plugins: [
    vue(),
    // 测试时不实际注入 PWA 插件
    // VitePWA({ registerType: 'autoUpdate' })
  ],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src'),
      'virtual:pwa-register': resolve(__dirname, 'tests/stubs/virtual-pwa-register.ts'),
    },
  },
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['./tests/setup.ts'],
    include: ['tests/**/*.test.ts'],
    exclude: ['node_modules/', 'dist/', 'src/services/**'],
    coverage: {
      provider: 'v8',
      reporter: ['text', 'json', 'html'],
      exclude: ['node_modules/', 'dist/'],
    },
  },
})
