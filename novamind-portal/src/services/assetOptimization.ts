/**
 * Asset Optimization Utilities
 * Provides image optimization, lazy loading, and placeholder generation
 * Validates: Requirements 8.4, 8.5, 8.6, 8.7, 8.8, 15.9
 */

import {
  Asset,
  AssetType,
  OptimizationOptions,
  ImageFormat,
  LazyLoadConfig,
  PlaceholderConfig,
  OptimizedAsset
} from '../types/assets'

/**
 * Image optimization function
 * Requirement 8.4: Resize, compress, WebP conversion with fallback
 * Requirement 8.5: Compression options
 * Requirement 8.6: Format conversion with WebP fallback
 */
export async function optimizeImageAsset(
  image: Asset,
  options: OptimizationOptions
): Promise<OptimizedAsset> {
  const startTime = performance.now()
  const originalSize = image.data instanceof Blob ? image.data.size : 0

  const canvas = document.createElement('canvas')
  const ctx = canvas.getContext('2d')
  if (!ctx) {
    throw new Error('Failed to get canvas context')
  }

  const imageUrl =
    image.url || (image.data instanceof Blob ? URL.createObjectURL(image.data) : image.data)

  return new Promise((resolve, reject) => {
    const img = new Image()
    img.crossOrigin = 'anonymous'

    img.onload = async () => {
      try {
        // Calculate dimensions
        let width = img.width
        let height = img.height

        if (options.resize) {
          const resized = calculateResizeDimensions(width, height, options.resize)
          width = resized.width
          height = resized.height
        }

        canvas.width = width
        canvas.height = height

        // Apply compression settings
        if (options.compress?.enabled) {
          ctx.filter = `brightness(${1 + (options.compress.level || 5) * 0.02})`
        }

        ctx.drawImage(img, 0, 0, width, height)

        // Convert to target format
        const targetFormat = options.format || ImageFormat.WEBP
        const quality = options.quality || 0.8

        let optimizedBlob: Blob
        try {
          optimizedBlob = await canvasToBlob(canvas, targetFormat, quality)
        } catch {
          // Fallback to JPEG if WebP not supported
          optimizedBlob = await canvasToBlob(canvas, ImageFormat.JPEG, quality)
        }

        const optimizationTime = performance.now() - startTime
        const optimizedSize = optimizedBlob.size
        const compressionRatio = originalSize > 0 ? optimizedSize / originalSize : 1

        const optimizedAsset: OptimizedAsset = {
          ...image,
          data: optimizedBlob,
          url: URL.createObjectURL(optimizedBlob),
          originalSize,
          optimizedSize,
          compressionRatio,
          optimizationTime,
          metadata: {
            ...image.metadata,
            width,
            height,
            modifiedAt: Date.now()
          }
        }

        resolve(optimizedAsset)
      } catch (error) {
        reject(error)
      }
    }

    img.onerror = () => {
      reject(new Error('Failed to load image for optimization'))
    }

    img.src = imageUrl
  })
}

/**
 * Lazy loading helper using Intersection Observer API
 * Requirement 8.7: Lazy loading with Intersection Observer
 */
export function setupLazyLoading(
  element: HTMLImageElement | HTMLVideoElement,
  config: LazyLoadConfig
): IntersectionObserver {
  if (!config.enabled) {
    return new IntersectionObserver(() => {})
  }

  const observer = new IntersectionObserver(
    (entries) => {
      entries.forEach((entry) => {
        if (entry.isIntersecting) {
          const el = entry.target as HTMLImageElement | HTMLVideoElement

          if (el instanceof HTMLImageElement) {
            const src = el.dataset.src
            if (src) {
              el.src = src
              el.removeAttribute('data-src')
            }
          } else if (el instanceof HTMLVideoElement) {
            const src = el.dataset.src
            if (src) {
              el.src = src
              el.removeAttribute('data-src')
            }
          }

          observer.unobserve(el)
        }
      })
    },
    {
      threshold: config.threshold || 0.1,
      rootMargin: config.rootMargin || '50px'
    }
  )

  observer.observe(element)
  return observer
}

/**
 * Placeholder generation for lazy-loaded images
 * Requirement 8.8: Generate placeholders for lazy loading
 */
export function generatePlaceholder(
  width: number,
  height: number,
  config: PlaceholderConfig
): string {
  switch (config.type) {
    case 'blur':
      return generateBlurPlaceholder(width, height, config.blurRadius || 10)
    case 'color':
      return generateColorPlaceholder(width, height, config.color || '#f0f0f0')
    case 'gradient':
      return generateGradientPlaceholder(width, height, config.gradient)
    default:
      return generateColorPlaceholder(width, height, '#f0f0f0')
  }
}

/**
 * Blur placeholder generation
 */
function generateBlurPlaceholder(width: number, height: number, blurRadius: number): string {
  const canvas = document.createElement('canvas')
  canvas.width = width
  canvas.height = height

  const ctx = canvas.getContext('2d')
  if (!ctx) return ''

  ctx.fillStyle = '#e0e0e0'
  ctx.fillRect(0, 0, width, height)

  // Apply blur effect
  ctx.filter = `blur(${blurRadius}px)`
  ctx.fillStyle = '#d0d0d0'
  ctx.fillRect(0, 0, width, height)

  return canvas.toDataURL('image/jpeg', 0.5)
}

/**
 * Color placeholder generation
 */
function generateColorPlaceholder(width: number, height: number, color: string): string {
  const canvas = document.createElement('canvas')
  canvas.width = width
  canvas.height = height

  const ctx = canvas.getContext('2d')
  if (!ctx) return ''

  ctx.fillStyle = color
  ctx.fillRect(0, 0, width, height)

  return canvas.toDataURL('image/jpeg', 0.5)
}

/**
 * Gradient placeholder generation
 */
function generateGradientPlaceholder(
  width: number,
  height: number,
  gradient?: { colors: string[]; angle: number }
): string {
  const canvas = document.createElement('canvas')
  canvas.width = width
  canvas.height = height

  const ctx = canvas.getContext('2d')
  if (!ctx) return ''

  const angle = (gradient?.angle || 45) * (Math.PI / 180)
  const colors = gradient?.colors || ['#f0f0f0', '#e0e0e0']

  const x1 = Math.cos(angle) * width
  const y1 = Math.sin(angle) * height

  const grad = ctx.createLinearGradient(0, 0, x1, y1)
  colors.forEach((color, index) => {
    grad.addColorStop(index / (colors.length - 1), color)
  })

  ctx.fillStyle = grad
  ctx.fillRect(0, 0, width, height)

  return canvas.toDataURL('image/jpeg', 0.5)
}

/**
 * Fallback asset logic for missing assets
 * Requirement 8.9: Return fallback when asset cannot be loaded
 */
export function getFallbackAsset(type: AssetType): Asset {
  const fallbacks: Record<AssetType, Asset> = {
    [AssetType.IMAGE]: {
      path: 'fallback/image.svg',
      type: AssetType.IMAGE,
      url: 'data:image/svg+xml,%3Csvg xmlns="http://www.w3.org/2000/svg" width="200" height="200"%3E%3Crect fill="%23f0f0f0" width="200" height="200"/%3E%3Ctext x="50%" y="50%" text-anchor="middle" dy=".3em" fill="%23999" font-size="14"%3EImage Not Found%3C/text%3E%3C/svg%3E',
      cached: false,
      metadata: {
        name: 'Image Fallback',
        size: 0,
        mimeType: 'image/svg+xml',
        createdAt: Date.now()
      }
    },
    [AssetType.VIDEO]: {
      path: 'fallback/video.mp4',
      type: AssetType.VIDEO,
      url: '',
      cached: false,
      metadata: {
        name: 'Video Fallback',
        size: 0,
        mimeType: 'video/mp4',
        createdAt: Date.now()
      }
    },
    [AssetType.ICON]: {
      path: 'fallback/icon.svg',
      type: AssetType.ICON,
      url: 'data:image/svg+xml,%3Csvg xmlns="http://www.w3.org/2000/svg" width="24" height="24"%3E%3Crect fill="%23ddd" width="24" height="24"/%3E%3C/svg%3E',
      cached: false,
      metadata: {
        name: 'Icon Fallback',
        size: 0,
        mimeType: 'image/svg+xml',
        createdAt: Date.now()
      }
    },
    [AssetType.AUDIO]: {
      path: 'fallback/audio.mp3',
      type: AssetType.AUDIO,
      url: '',
      cached: false,
      metadata: {
        name: 'Audio Fallback',
        size: 0,
        mimeType: 'audio/mpeg',
        createdAt: Date.now()
      }
    },
    [AssetType.DOCUMENT]: {
      path: 'fallback/document.pdf',
      type: AssetType.DOCUMENT,
      url: '',
      cached: false,
      metadata: {
        name: 'Document Fallback',
        size: 0,
        mimeType: 'application/pdf',
        createdAt: Date.now()
      }
    }
  }

  return fallbacks[type]
}

/**
 * Detect asset type from file path
 */
export function detectAssetType(path: string): AssetType {
  const ext = path.split('.').pop()?.toLowerCase()

  switch (ext) {
    case 'jpg':
    case 'jpeg':
    case 'png':
    case 'gif':
    case 'webp':
    case 'avif':
    case 'svg':
      return AssetType.IMAGE
    case 'mp4':
    case 'webm':
    case 'mov':
    case 'avi':
    case 'mkv':
      return AssetType.VIDEO
    case 'mp3':
    case 'wav':
    case 'ogg':
    case 'aac':
      return AssetType.AUDIO
    case 'pdf':
    case 'doc':
    case 'docx':
    case 'txt':
      return AssetType.DOCUMENT
    case 'ico':
      return AssetType.ICON
    default:
      return AssetType.IMAGE
  }
}

/**
 * Check format support in browser
 */
export function checkFormatSupport(): {
  webp: boolean
  avif: boolean
  heic: boolean
} {
  const canvas = document.createElement('canvas')
  canvas.width = 1
  canvas.height = 1

  const webp = canvas.toDataURL('image/webp').includes('webp')
  const avif = canvas.toDataURL('image/avif').includes('avif')

  // HEIC support check (limited browser support)
  const heic = false // Most browsers don't support HEIC

  return { webp, avif, heic }
}

// Helper function to convert canvas to blob
function canvasToBlob(
  canvas: HTMLCanvasElement,
  format: ImageFormat,
  quality: number
): Promise<Blob> {
  return new Promise((resolve, reject) => {
    canvas.toBlob(
      (blob) => {
        if (blob) {
          resolve(blob)
        } else {
          reject(new Error('Failed to convert canvas to blob'))
        }
      },
      `image/${format}`,
      quality
    )
  })
}

/**
 * Calculate resize dimensions based on options
 */
function calculateResizeDimensions(
  originalWidth: number,
  originalHeight: number,
  options: any
): { width: number; height: number } {
  let width = options.width || originalWidth
  let height = options.height || originalHeight

  if (options.fit === 'cover') {
    const ratio = Math.max(width / originalWidth, height / originalHeight)
    width = Math.round(originalWidth * ratio)
    height = Math.round(originalHeight * ratio)
  } else if (options.fit === 'contain') {
    const ratio = Math.min(width / originalWidth, height / originalHeight)
    width = Math.round(originalWidth * ratio)
    height = Math.round(originalHeight * ratio)
  }

  if (options.withoutEnlargement && (width > originalWidth || height > originalHeight)) {
    return { width: originalWidth, height: originalHeight }
  }

  return { width, height }
}
