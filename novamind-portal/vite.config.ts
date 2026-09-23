import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'
import { VitePWA } from 'vite-plugin-pwa'

export default defineConfig({
  plugins: [
    vue(),
    VitePWA({
      registerType: 'autoUpdate',
      // 自定义 Service Worker（注入式：vite-plugin-pwa 会在编译期把 src/pwa/service-worker.ts
      // 与 workbox runtimeCaching 一并打包进最终 sw.js）
      strategies: 'injectManifest',
      srcDir: 'src/pwa',
      filename: 'service-worker.ts',
      // PWA Manifest
      manifest: {
        name: 'NovaMind 智星云 - 智能学习平台',
        short_name: 'NovaMind',
        description: 'NovaMind 智星云，围绕用户真实学习任务打造的课程与 AI 协同平台。',
        theme_color: '#0e0b28',
        background_color: '#050410',
        display: 'standalone',
        orientation: 'portrait-primary',
        start_url: '/portal/home',
        scope: '/',
        lang: 'zh-CN',
        icons: [
          {
            src: '/pwa-icons/pwa-192x192.png',
            sizes: '192x192',
            type: 'image/png',
            purpose: 'any maskable',
          },
          {
            src: '/pwa-icons/pwa-512x512.png',
            sizes: '512x512',
            type: 'image/png',
            purpose: 'any maskable',
          },
        ],
      },
      // 开发模式下也启用 SW 方便测试离线场景
      devOptions: {
        enabled: true,
        type: 'module',
        navigateFallback: 'index.html',
      },
      injectRegister: 'auto',
      workbox: {
        // 我们使用 injectManifest 策略，runtimeCaching 通过自定义 SW 自管
        // 这里仅保留最低限度的清理策略
        cleanupOutdatedCaches: true,
        clientsClaim: true,
        skipWaiting: false,
        globPatterns: ['**/*.{js,css,html,ico,png,svg,woff,woff2,json}'],
        navigateFallback: '/index.html',
        navigateFallbackDenylist: [/^\/api/, /^\/ais/],
        // runtimeCaching 通过 injectManifest 自管
        runtimeCaching: [
          {
            urlPattern: /^\/api\/(?!learning\/progress).*$/,
            method: 'GET',
            handler: 'NetworkFirst',
            options: {
              cacheName: 'novamind-api-get',
              networkTimeoutSeconds: 6,
              expiration: {
                maxEntries: 200,
                maxAgeSeconds: 60 * 5, // 5 分钟
              },
              cacheableResponse: {
                statuses: [0, 200],
              },
            },
          },
          {
            urlPattern: /^\/ais\/.*$/,
            method: 'GET',
            handler: 'NetworkFirst',
            options: {
              cacheName: 'novamind-ais-get',
              networkTimeoutSeconds: 10,
              expiration: {
                maxEntries: 60,
                maxAgeSeconds: 60 * 5,
              },
              cacheableResponse: { statuses: [0, 200] },
            },
          },
          {
            urlPattern: /\.(?:png|jpg|jpeg|svg|gif|webp|ico)$/,
            handler: 'CacheFirst',
            options: {
              cacheName: 'novamind-images',
              expiration: {
                maxEntries: 300,
                maxAgeSeconds: 60 * 60 * 24 * 30, // 30 天
              },
              cacheableResponse: { statuses: [0, 200] },
            },
          },
          {
            urlPattern: /\.(?:woff2?|ttf|otf|eot)$/,
            handler: 'CacheFirst',
            options: {
              cacheName: 'novamind-fonts',
              expiration: {
                maxEntries: 30,
                maxAgeSeconds: 60 * 60 * 24 * 365, // 1 年
              },
              cacheableResponse: { statuses: [0, 200] },
            },
          },
          {
            urlPattern: /\.(?:js|css)$/,
            handler: 'CacheFirst',
            options: {
              cacheName: 'novamind-static-assets',
              expiration: {
                maxEntries: 200,
                maxAgeSeconds: 60 * 60 * 24 * 30,
              },
              cacheableResponse: { statuses: [0, 200] },
            },
          },
        ],
      },
    }),
  ],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src'),
    },
  },
  server: {
    port: 5173,
    host: '0.0.0.0',
    proxy: {
      '/ais': {
        target: 'http://localhost:10010',
        changeOrigin: true,
      },
      '/api': {
        target: 'http://localhost:10010',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, ''),
      },
    },
  },
  build: {
    chunkSizeWarningLimit: 1200,
    assetsInlineLimit: 4096,
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (id.includes('node_modules')) {
            if (id.includes('element-plus') || id.includes('@element-plus')) {
              return 'ui-element'
            }
            if (id.includes('vue') || id.includes('pinia') || id.includes('@vueuse')) {
              return 'vue-vendor'
            }
            if (id.includes('marked') || id.includes('highlight.js')) {
              return 'markdown-vendor'
            }
            if (id.includes('vis-network') || id.includes('vis-data')) {
              return 'graph-vendor'
            }
            if (id.includes('gsap')) {
              return 'motion-vendor'
            }
            if (id.includes('axios') || id.includes('dayjs') || id.includes('nprogress')) {
              return 'utils-vendor'
            }
            return 'common-vendor'
          }
        },
      },
    },
  },
})

