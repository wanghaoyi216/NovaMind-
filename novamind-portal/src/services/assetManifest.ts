/**
 * Asset Manifest
 * Defines available assets and critical assets for preloading
 * Validates: Requirements 8.10, 15.1, 15.2, 15.3, 15.4, 15.5, 15.8
 */

import { AssetManifest, AssetEntry, AssetType } from '../types/assets'

/**
 * Asset manifest definition
 * Requirement 8.10: Define assetManifest with backgrounds, logos, videos, icons
 * Requirement 15.1: Map assets from docker/tj-admin/assets directory
 * Requirement 15.2: Identify critical assets for preloading
 */
export const assetManifest: AssetManifest = {
  // Background images
  backgrounds: [
    {
      path: '/assets/backgrounds/admin-hero.jpg',
      type: AssetType.IMAGE,
      critical: true,
      preload: true,
      optimization: {
        resize: {
          width: 1920,
          height: 1080,
          fit: 'cover'
        },
        compress: {
          enabled: true,
          level: 7,
          progressive: true
        },
        format: 'webp',
        quality: 0.85,
        cache: true,
        cacheTTL: 3600,
        priority: 'high'
      }
    },
    {
      path: '/assets/backgrounds/dashboard-analytics.jpg',
      type: AssetType.IMAGE,
      critical: false,
      preload: false,
      optimization: {
        resize: {
          width: 1920,
          height: 1080,
          fit: 'cover'
        },
        compress: {
          enabled: true,
          level: 7,
          progressive: true
        },
        format: 'webp',
        quality: 0.85,
        cache: true,
        cacheTTL: 3600,
        priority: 'normal'
      }
    }
  ],

  // Logo images
  logos: [
    {
      path: '/assets/logos/brand-logo.png',
      type: AssetType.IMAGE,
      critical: true,
      preload: true,
      optimization: {
        resize: {
          width: 200,
          height: 200,
          fit: 'contain'
        },
        compress: {
          enabled: true,
          level: 5,
          interlaced: true
        },
        format: 'webp',
        quality: 0.9,
        cache: true,
        cacheTTL: 7200,
        priority: 'high'
      }
    },
    {
      path: '/assets/logos/newlogo.a09a871a.png',
      type: AssetType.IMAGE,
      critical: true,
      preload: true,
      optimization: {
        resize: {
          width: 200,
          height: 200,
          fit: 'contain'
        },
        compress: {
          enabled: true,
          level: 5,
          interlaced: true
        },
        format: 'webp',
        quality: 0.9,
        cache: true,
        cacheTTL: 7200,
        priority: 'high'
      }
    },
    {
      path: '/assets/logos/logo.5619bd6c.png',
      type: AssetType.IMAGE,
      critical: false,
      preload: false,
      optimization: {
        resize: {
          width: 150,
          height: 150,
          fit: 'contain'
        },
        compress: {
          enabled: true,
          level: 5,
          interlaced: true
        },
        format: 'webp',
        quality: 0.9,
        cache: true,
        cacheTTL: 7200,
        priority: 'normal'
      }
    }
  ],

  // Video assets
  videos: [
    {
      path: '/assets/videos/login.3e91c4a8.mp4',
      type: AssetType.VIDEO,
      critical: false,
      preload: false,
      optimization: {
        cache: true,
        cacheTTL: 3600,
        priority: 'normal'
      }
    }
  ],

  // Icon assets
  icons: [
    {
      path: '/assets/icons/system-icon.svg',
      type: AssetType.ICON,
      critical: false,
      preload: false,
      optimization: {
        resize: {
          width: 24,
          height: 24,
          fit: 'contain'
        },
        cache: true,
        cacheTTL: 7200,
        priority: 'normal'
      }
    },
    {
      path: '/assets/icons/certificate.svg',
      type: AssetType.ICON,
      critical: false,
      preload: false,
      optimization: {
        resize: {
          width: 24,
          height: 24,
          fit: 'contain'
        },
        cache: true,
        cacheTTL: 7200,
        priority: 'normal'
      }
    }
  ],

  // Critical assets to preload on startup
  critical: [
    '/assets/logos/brand-logo.png',
    '/assets/logos/newlogo.a09a871a.png',
    '/assets/backgrounds/admin-hero.jpg'
  ]
}

/**
 * Get critical assets for preloading
 * Requirement 15.2: Identify critical assets for preloading
 */
export function getCriticalAssets(): string[] {
  return assetManifest.critical
}

/**
 * Get all assets of a specific type
 */
export function getAssetsByType(type: AssetType): AssetEntry[] {
  const allAssets: AssetEntry[] = [
    ...assetManifest.backgrounds,
    ...assetManifest.logos,
    ...assetManifest.videos,
    ...assetManifest.icons
  ]

  return allAssets.filter((asset) => asset.type === type)
}

/**
 * Get preloadable assets
 */
export function getPreloadableAssets(): AssetEntry[] {
  const allAssets: AssetEntry[] = [
    ...assetManifest.backgrounds,
    ...assetManifest.logos,
    ...assetManifest.videos,
    ...assetManifest.icons
  ]

  return allAssets.filter((asset) => asset.preload)
}

/**
 * Get asset by path
 */
export function getAssetByPath(path: string): AssetEntry | undefined {
  const allAssets: AssetEntry[] = [
    ...assetManifest.backgrounds,
    ...assetManifest.logos,
    ...assetManifest.videos,
    ...assetManifest.icons
  ]

  return allAssets.find((asset) => asset.path === path)
}

/**
 * Get all assets
 */
export function getAllAssets(): AssetEntry[] {
  return [
    ...assetManifest.backgrounds,
    ...assetManifest.logos,
    ...assetManifest.videos,
    ...assetManifest.icons
  ]
}
