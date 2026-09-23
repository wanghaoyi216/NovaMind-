import { nextTick, onMounted, onUnmounted } from 'vue'

interface RevealObserverOptions {
  once?: boolean
  threshold?: number
  rootMargin?: string
  activeClass?: string
  hiddenClass?: string
}

export function useRevealObserver(selector: string, options: RevealObserverOptions = {}) {
  let observer: IntersectionObserver | null = null
  const {
    once = true,
    threshold = 0.12,
    rootMargin = '0px 0px -48px 0px',
    activeClass = 'is-visible',
    hiddenClass = '',
  } = options

  onMounted(async () => {
    await nextTick()

    observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            entry.target.classList.add(activeClass)
            if (hiddenClass) {
              entry.target.classList.remove(hiddenClass)
            }
            if (once) {
              observer?.unobserve(entry.target)
            }
            return
          }

          if (!once) {
            entry.target.classList.remove(activeClass)
            if (hiddenClass) {
              entry.target.classList.add(hiddenClass)
            }
          }
        })
      },
      {
        threshold,
        rootMargin,
      }
    )

    document.querySelectorAll(selector).forEach((element) => {
      // 首屏已在视口内的元素直接显示（跳过过渡），其余走滚动进场动画
      const rect = element.getBoundingClientRect()
      const inViewport =
        rect.top < window.innerHeight && rect.bottom > 0 && rect.left < window.innerWidth && rect.right > 0
      if (inViewport) {
        element.classList.add('no-anim')
        element.classList.add(activeClass)
        return
      }
      observer?.observe(element)
    })
  })

  onUnmounted(() => {
    observer?.disconnect()
    observer = null
  })
}
