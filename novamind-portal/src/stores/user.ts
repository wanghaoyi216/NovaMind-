import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { UserInfo } from '@/types'

export const useUserStore = defineStore('user', () => {
  const token = ref<string>(localStorage.getItem('nova_token') || '')
  const userInfo = ref<UserInfo | null>(null)

  function setToken(t: string) {
    token.value = t
    localStorage.setItem('nova_token', t)
  }

  function setInfo(info: UserInfo) {
    userInfo.value = info
  }

  function logout() {
    token.value = ''
    userInfo.value = null
    localStorage.removeItem('nova_token')
  }

  return { token, userInfo, setToken, setInfo, logout }
})
