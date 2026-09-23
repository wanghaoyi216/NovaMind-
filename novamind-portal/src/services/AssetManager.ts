/**
 * AssetManager Service
 * Manages dynamic asset loading, caching, and optimization
 * Validates: Requirements 8.1, 8.2, 8.3, 8.4, 8.9
 */

import {
  Asset,
  AssetType,
  OptimizationOptions,
  CacheEntry,
  AssetLoadResult,
  OptimizedAsset,
  IAssetManager,
  CacheStats,
  FallbackAsset,
  AssetMetadata
} from '../types/assets'

/**
 * AssetManager class
 * Implements in-memory asset caching with TTL tracking
 * Requirement 8.1: loadAsset with caching logic
 * Requirement 8.2: preloadAssets for critical assets
 * Requirement 8.3: getCachedAsset to retrieve from cache
 * Requirement 8.4: optimizeImage for image optimization
 * Requirement 8.9: Use Map for in-memory cache with TTL tracking
 */
export class AssetManager implements IAssetManager {
  private cache: Map<string, CacheEntry> = new Map()
  private cacheStats = {
    hits: 0,
    misses: 0,
    totalLoadTime: 0,
    loadCount: 0
  }

  // Fallback assets for missing resources
  private fallbackAssets: Map<AssetType, FallbackAsset> = new Map([
    [
      AssetType.IMAGE,
      {
        path: 'fallback/image.svg',
        type: AssetType.IMAGE,
        data: 'data:image/svg+xml,%3Csvg xmlns="http://www.w3.org/2000/svg" width="200" height="200"%3E%3Crect fill="%23f0f0f0" width="200" height="200"/%3E%3Ctext x="50%" y="50%" text-anchor="middle" dy=".3em" fill="%23999" font-size="14"%3EImage Not Found%3C/text%3E%3C/svg%3E',
        description: 'Fallback image placeholder'
      }
    ],
    [
      AssetType.VIDEO,
      {
        path: 'fallback/video.mp4',
        type: AssetType.VIDEO,
        data: '',
        description: 'Fallback video placeholder'
      }
    ],
    [
      AssetType.ICON,
      {
        path: 'fallback/icon.svg',
        type: AssetType.ICON,
        data: 'data:image/svg+xml,%3Csvg xmlns="http://www.w3.org/2000/svg" width="24" height="24"%3E%3Crect fill="%23ddd" width="24" height="24"/%3E%3C/svg%3E',
        description: 'Fallback icon placeholder'
      }
    ]
  ])

  /**
   * Load asset with caching logic
   * Requirement 8.1: Check cache first, load from filesystem if not cached
   * Requirement 8.2: Return fallback if asset cannot be loaded
   */
  async loadAsset(
    path: string,
    type: AssetType,
    options?: OptimizationOptions
  ): Promise<Asset> {
    const startTime = performance.now()

    // Check cache first
    const cachedAsset = this.getCachedAsset(path)
    if (cachedAsset) {
      this.cacheStats.hits++
      return cachedAsset
    }

    this.cacheStats.misses++

    try {
      // Load asset from filesystem
      const response = await fetch(path)
      if (!response.ok) {
        throw new Error(`Failed to load asset: ${response.statusText}`)
      }

      const blob = await response.blob()
      const asset: Asset = {
        path,
        type,
        data: blob,
        url: URL.createObjectURL(blob),
        cached: false,
        metadata: {
          name: path.split('/').pop() || 'unknown',
          size: blob.size,
          mimeType: blob.type,
          createdAt: Date.now()
        }
      }

      // Apply optimization if specified
      if (options) {
        const optimized = await this.optimizeImage(asset, options)
        this.cacheAsset(path, optimized, options.cacheTTL || 3600)
        return optimized
      }

      // Cache the asset
      this.cacheAsset(path, asset, options?.cacheTTL || 3600)

      const loadTime = performance.now() - startTime
      this.cacheStats.totalLoadTime += loadTime
      this.cacheStats.loadCount++

      return asset
    } catch (error) {
      console.error(`Error loading asset ${path}:`, error)
      return this.getFallbackAsset(type)
    }
  }

  /**
   * Preload critical assets
   * Requirement 8.2: Preload critical assets on startup
   */
  async preloadAssets(paths: string[]): Promise<void> {
    const preloadPromises = paths.map((path) => {
      // Determine asset type from path
      const type = this.detectAssetType(path)
      return this.loadAsset(path, type, {
        cache: true,
        cacheTTL: 3600,
        priority: 'high'
      }).catch((error) => {
        console.warn(`Failed to preload asset ${path}:`, error)
      })
    })

    await Promise.all(preloadPromises)
  }

  /**
   * Get cached asset
   * Requirement 8.3: Retrieve asset from cache if available and not expired
   */
  getCachedAsset(path: string): Asset | null {
    const entry = this.cache.get(path)

    if (!entry) {
      return null
    }

    // Check if cache entry has expired
    const now = Date.now()
    const age = (now - entry.timestamp) / 1000 // Convert to seconds
    if (age > entry.ttl) {
      this.cache.delete(path)
      return null
    }

    // Update hit count
    entry.hits++
    return entry.asset
  }

  /**
   * Optimize image
   * Requirement 8.4: Resize, compress, and convert to WebP with fallback
   */
  async optimizeImage(
    image: Asset,
    options: OptimizationOptions
  ): Promise<OptimizedAsset> {
    const startTime = performance.now()
    const originalSize = image.data instanceof Blob ? image.data.size : 0

    // Create canvas for image processing
    const canvas = document.createElement('canvas')
    const ctx = canvas.getContext('2d')
    if (!ctx) {
      throw new Error('Failed to get canvas context')
    }

    // Load image into canvas
    const img = new Image()
    const imageUrl =
      image.url || (image.data instanceof Blob ? URL.createObjectURL(image.data) : image.data)

    return new Promise((resolve, reject) => {
      img.onload = async () => {
        try {
          // Apply resize options
          let width = img.width
          let height = img.height

          if (options.resize) {
            const resized = this.calculateResizeDimensions(
              width,
              height,
              options.resize
            )
            width = resized.width
            height = resized.height
          }

          canvas.width = width
          canvas.height = height
          ctx.drawImage(img, 0, 0, width, height)

          // Convert to optimized format
          const quality = options.quality || 0.8
          const format = options.format || 'webp'

          let optimizedBlob: Blob
          try {
            optimizedBlob = await new Promise<Blob>((resolve) => {
              canvas.toBlob(
                (blob) => {
                  if (blob) resolve(blob)
                },
                `image/${format}`,
                quality
              )
            })
          } catch {
            // Fallback to original format if WebP not supported
            optimizedBlob = await new Promise<Blob>((resolve) => {
              canvas.toBlob(
                (blob) => {
                  if (blob) resolve(blob)
                },
                'image/jpeg',
                quality
              )
            })
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
            } as AssetMetadata
          }

          resolve(optimizedAsset)
        } catch (error) {
          reject(error)
        }
      }

      img.onerror = () => {
        reject(new Error('Failed to load image'))
      }

      img.src = imageUrl
    })
  }

  /**
   * Clear all cached assets
   */
  clearCache(): void {
    this.cache.forEach((entry) => {
      if (entry.asset.url) {
        URL.revokeObjectURL(entry.asset.url)
      }
    })
    this.cache.clear()
  }

  /**
   * Get cache statistics
   */
  getCacheStats(): CacheStats {
    let totalSize = 0
    this.cache.forEach((entry) => {
      if (entry.asset.data instanceof Blob) {
        totalSize += entry.asset.data.size
      }
    })

    const totalRequests = this.cacheStats.hits + this.cacheStats.misses
    const hitRate = totalRequests > 0 ? this.cacheStats.hits / totalRequests : 0
    const missRate = totalRequests > 0 ? this.cacheStats.misses / totalRequests : 0
    const averageLoadTime =
      this.cacheStats.loadCount > 0
        ? this.cacheStats.totalLoadTime / this.cacheStats.loadCount
        : 0

    return {
      totalEntries: this.cache.size,
      totalSize,
      hitRate,
      missRate,
      averageLoadTime
    }
  }

  // Private helper methods

  private cacheAsset(path: string, asset: Asset, ttl: number): void {
    const entry: CacheEntry = {
      asset,
      timestamp: Date.now(),
      ttl,
      hits: 0
    }
    this.cache.set(path, entry)
  }

  private getFallbackAsset(type: AssetType): Asset {
    const fallback = this.fallbackAssets.get(type)
    if (!fallback) {
      throw new Error(`No fallback asset for type ${type}`)
    }

    return {
      path: fallback.path,
      type,
      data: fallback.data,
      url: fallback.data,
      cached: false,
      metadata: {
        name: fallback.description,
        size: fallback.data.length,
        mimeType: 'image/svg+xml',
        createdAt: Date.now()
      }
    }
  }

  private detectAssetType(path: string): AssetType {
    const ext = path.split('.').pop()?.toLowerCase()

    switch (ext) {
      case 'jpg':
      case 'jpeg':
      case 'png':
      case 'gif':
      case 'webp':
      case 'svg':
        return AssetType.IMAGE
      case 'mp4':
      case 'webm':
      case 'mov':
      case 'avi':
        return AssetType.VIDEO
      case 'svg':
      case 'ico':
      case 'png':
        return AssetType.ICON
      default:
        return AssetType.IMAGE
    }
  }

  private calculateResizeDimensions(
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
}

// Export singleton instance
export const assetManager = new AssetManager()
