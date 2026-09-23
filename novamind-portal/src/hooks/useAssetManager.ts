/**
 * useAssetManager Hook
 * React hook for managing asset loading and caching
 * Validates: Requirements 8.1, 8.2, 8.3, 8.4, 8.9, 8.10
 */

import { useEffect, useState, useCallback, useRef } from 'react'
import { Asset, AssetType, OptimizationOptions, CacheStats } from '../types/assets'
import { AssetManager } from '../services/AssetManager'
import { getCriticalAssets } from '../services/assetManifest'

/**
 * useAssetManager hook
 * Provides asset loading, caching, and optimization functionality
 */
export function useAssetManager() {
  const managerRef = useRef<AssetManager | null>(null)
  const [isInitialized, setIsInitialized] = useState(false)
  const [cacheStats, setCacheStats] = useState<CacheStats | null>(null)
  const [isLoading, setIsLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  // Initialize asset manager
  useEffect(() => {
    if (!managerRef.current) {
      managerRef.current = new AssetManager()
    }

    // Preload critical assets on initialization
    const preloadCriticalAssets = async () => {
      try {
        setIsLoading(true)
        const criticalAssets = getCriticalAssets()
        await managerRef.current!.preloadAssets(criticalAssets)
        setIsInitialized(true)
        setError(null)
      } catch (err) {
        console.error('Failed to preload critical assets:', err)
        setError(err instanceof Error ? err.message : 'Failed to preload assets')
        setIsInitialized(true)
      } finally {
        setIsLoading(false)
      }
    }

    preloadCriticalAssets()

    // Cleanup on unmount
    return () => {
      if (managerRef.current) {
        managerRef.current.clearCache()
      }
    }
  }, [])

  /**
   * Load an asset
   */
  const loadAsset = useCallback(
    async (path: string, type: AssetType, options?: OptimizationOptions): Promise<Asset> => {
      if (!managerRef.current) {
        throw new Error('AssetManager not initialized')
      }

      try {
        setIsLoading(true)
        setError(null)
        const asset = await managerRef.current.loadAsset(path, type, options)
        return asset
      } catch (err) {
        const errorMessage = err instanceof Error ? err.message : 'Failed to load asset'
        setError(errorMessage)
        throw err
      } finally {
        setIsLoading(false)
      }
    },
    []
  )

  /**
   * Get cached asset
   */
  const getCachedAsset = useCallback((path: string): Asset | null => {
    if (!managerRef.current) {
      return null
    }
    return managerRef.current.getCachedAsset(path)
  }, [])

  /**
   * Preload multiple assets
   */
  const preloadAssets = useCallback(
    async (paths: string[]): Promise<void> => {
      if (!managerRef.current) {
        throw new Error('AssetManager not initialized')
      }

      try {
        setIsLoading(true)
        setError(null)
        await managerRef.current.preloadAssets(paths)
      } catch (err) {
        const errorMessage = err instanceof Error ? err.message : 'Failed to preload assets'
        setError(errorMessage)
        throw err
      } finally {
        setIsLoading(false)
      }
    },
    []
  )

  /**
   * Optimize an image
   */
  const optimizeImage = useCallback(
    async (image: Asset, options: OptimizationOptions) => {
      if (!managerRef.current) {
        throw new Error('AssetManager not initialized')
      }

      try {
        setIsLoading(true)
        setError(null)
        const optimized = await managerRef.current.optimizeImage(image, options)
        return optimized
      } catch (err) {
        const errorMessage = err instanceof Error ? err.message : 'Failed to optimize image'
        setError(errorMessage)
        throw err
      } finally {
        setIsLoading(false)
      }
    },
    []
  )

  /**
   * Clear cache
   */
  const clearCache = useCallback((): void => {
    if (managerRef.current) {
      managerRef.current.clearCache()
      setCacheStats(null)
    }
  }, [])

  /**
   * Get cache statistics
   */
  const getStats = useCallback((): CacheStats | null => {
    if (!managerRef.current) {
      return null
    }
    const stats = managerRef.current.getCacheStats()
    setCacheStats(stats)
    return stats
  }, [])

  return {
    isInitialized,
    isLoading,
    error,
    cacheStats,
    loadAsset,
    getCachedAsset,
    preloadAssets,
    optimizeImage,
    clearCache,
    getStats
  }
}

/**
 * useAsset hook
 * Simplified hook for loading a single asset
 */
export function useAsset(path: string, type: AssetType, options?: OptimizationOptions) {
  const [asset, setAsset] = useState<Asset | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const { loadAsset, getCachedAsset } = useAssetManager()

  useEffect(() => {
    const loadAssetData = async () => {
      try {
        setIsLoading(true)
        setError(null)

        // Check cache first
        const cached = getCachedAsset(path)
        if (cached) {
          setAsset(cached)
          setIsLoading(false)
          return
        }

        // Load asset
        const loaded = await loadAsset(path, type, options)
        setAsset(loaded)
      } catch (err) {
        const errorMessage = err instanceof Error ? err.message : 'Failed to load asset'
        setError(errorMessage)
      } finally {
        setIsLoading(false)
      }
    }

    loadAssetData()
  }, [path, type, options, loadAsset, getCachedAsset])

  return { asset, isLoading, error }
}

/**
 * useLazyAsset hook
 * Lazy load an asset on demand
 */
export function useLazyAsset() {
  const [asset, setAsset] = useState<Asset | null>(null)
  const [isLoading, setIsLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const { loadAsset } = useAssetManager()

  const load = useCallback(
    async (path: string, type: AssetType, options?: OptimizationOptions) => {
      try {
        setIsLoading(true)
        setError(null)
        const loaded = await loadAsset(path, type, options)
        setAsset(loaded)
        return loaded
      } catch (err) {
        const errorMessage = err instanceof Error ? err.message : 'Failed to load asset'
        setError(errorMessage)
        throw err
      } finally {
        setIsLoading(false)
      }
    },
    [loadAsset]
  )

  return { asset, isLoading, error, load }
}
