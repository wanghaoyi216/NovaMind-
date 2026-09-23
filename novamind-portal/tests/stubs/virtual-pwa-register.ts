/**
 * virtual:pwa-register 的测试桩
 * 在测试环境下 vite-plugin-pwa 的虚拟模块不会生成，单独提供 stub。
 */
export interface RegisterSWOptions {
  immediate?: boolean
  onNeedRefresh?: () => void
  onOfflineReady?: () => void
  onRegisteredSW?: (swScriptUrl: string, registration: ServiceWorkerRegistration | undefined) => void
  onRegisterError?: (error: any) => void
}

export function registerSW(options: RegisterSWOptions = {}) {
  // 测试中不真正注册 SW，只记录调用
  // eslint-disable-next-line no-console
  console.log('[test-stub] registerSW called', options)
  return () => {}
}
