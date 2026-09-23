/**
 * Asset management related types
 * Validates: Requirements 8.1, 8.2, 8.3, 8.4, 8.5, 8.6, 8.7, 8.8, 8.9, 8.10
 */

/**
 * Asset type enum
 * Requirement 8.2: AssetType enum (IMAGE, VIDEO, ICON)
 */
export enum AssetType {
  IMAGE = 'IMAGE',
  VIDEO = 'VIDEO',
  ICON = 'ICON',
  AUDIO = 'AUDIO',
  DOCUMENT = 'DOCUMENT'
}

/**
 * Asset interface
 * Requirement 8.1: Asset interface with path, type, data, metadata
 */
export interface Asset {
  path: string
  type: AssetType
  data?: Blob | ArrayBuffer | string
  metadata?: AssetMetadata
  url?: string
  cached?: boolean
  cacheExpiry?: number
}

/**
 * Asset metadata
 */
export interface AssetMetadata {
  name: string
  size: number
  mimeType: string
  width?: number
  height?: number
  duration?: number
  createdAt?: number
  modifiedAt?: number
  tags?: string[]
  description?: string
}

/**
 * Optimization options for asset processing
 * Requirement 8.3: OptimizationOptions interface with resize, compress, format, enableLazyLoad
 */
export interface OptimizationOptions {
  // Image optimization
  resize?: ResizeOptions
  compress?: CompressionOptions
  format?: ImageFormat
  quality?: number // 0-100

  // Lazy loading
  enableLazyLoad?: boolean
  placeholderType?: 'blur' | 'color' | 'gradient'
  placeholderColor?: string

  // General options
  cache?: boolean
  cacheTTL?: number // in seconds
  priority?: 'high' | 'normal' | 'low'
}

/**
 * Resize options for images
 * Requirement 8.4: Resize options for image optimization
 */
export interface ResizeOptions {
  width?: number
  height?: number
  fit?: 'cover' | 'contain' | 'fill' | 'inside' | 'outside'
  position?: string // e.g., 'center', 'top', 'bottom'
  withoutEnlargement?: boolean
}

/**
 * Compression options
 * Requirement 8.5: Compression options for asset optimization
 */
export interface CompressionOptions {
  enabled: boolean
  level?: number // 0-9, higher = more compression
  progressive?: boolean // for JPEG
  interlaced?: boolean // for PNG
}

/**
 * Image format options
 * Requirement 8.6: Format conversion options (WebP with fallback)
 */
export enum ImageFormat {
  JPEG = 'jpeg',
  PNG = 'png',
  WEBP = 'webp',
  AVIF = 'avif',
  GIF = 'gif',
  SVG = 'svg'
}

/**
 * Video optimization options
 * Requirement 8.7: Video optimization settings
 */
export interface VideoOptimizationOptions {
  codec?: string // 'h264', 'vp9', 'av1'
  bitrate?: string // e.g., '1000k', '2M'
  resolution?: string // e.g., '1920x1080', '1280x720'
  fps?: number
  autoplay?: boolean
  loop?: boolean
  muted?: boolean
  controls?: boolean
  preload?: 'none' | 'metadata' | 'auto'
}

/**
 * Icon optimization options
 * Requirement 8.8: Icon optimization settings
 */
export interface IconOptimizationOptions {
  size?: number // icon size in pixels
  color?: string
  strokeWidth?: number
  format?: 'svg' | 'png' | 'webp'
}

/**
 * Lazy loading configuration
 * Requirement 8.9: Lazy loading metadata and placeholder generation
 */
export interface LazyLoadConfig {
  enabled: boolean
  threshold?: number // intersection observer threshold
  rootMargin?: string // e.g., '50px'
  placeholder?: string // base64 encoded placeholder
  placeholderType?: 'blur' | 'color' | 'gradient'
}

/**
 * Asset cache entry
 */
export interface CacheEntry {
  asset: Asset
  timestamp: number
  ttl: number // time to live in seconds
  hits: number
}

/**
 * Asset manifest for preloading
 * Requirement 8.10: Asset manifest for critical assets
 */
export interface AssetManifest {
  backgrounds: AssetEntry[]
  logos: AssetEntry[]
  videos: AssetEntry[]
  icons: AssetEntry[]
  critical: string[] // paths of critical assets to preload
}

/**
 * Asset entry in manifest
 */
export interface AssetEntry {
  path: string
  type: AssetType
  critical?: boolean
  preload?: boolean
  optimization?: OptimizationOptions
}

/**
 * Asset loading result
 */
export interface AssetLoadResult {
  success: boolean
  asset?: Asset
  error?: string
  cached?: boolean
  loadTime?: number
}

/**
 * Asset optimization result
 */
export interface OptimizedAsset extends Asset {
  originalSize: number
  optimizedSize: number
  compressionRatio: number
  optimizationTime: number
}

/**
 * Placeholder configuration
 */
export interface PlaceholderConfig {
  type: 'blur' | 'color' | 'gradient'
  color?: string
  blurRadius?: number
  gradient?: {
    colors: string[]
    angle: number
  }
}

/**
 * Asset manager interface
 */
export interface IAssetManager {
  loadAsset(path: string, type: AssetType, options?: OptimizationOptions): Promise<Asset>
  preloadAssets(paths: string[]): Promise<void>
  getCachedAsset(path: string): Asset | null
  optimizeImage(image: Asset, options: OptimizationOptions): Promise<OptimizedAsset>
  optimizeVideo(video: Asset, options: VideoOptimizationOptions): Promise<OptimizedAsset>
  optimizeIcon(icon: Asset, options: IconOptimizationOptions): Promise<OptimizedAsset>
  clearCache(): void
  getCacheStats(): CacheStats
}

/**
 * Cache statistics
 */
export interface CacheStats {
  totalEntries: number
  totalSize: number
  hitRate: number
  missRate: number
  averageLoadTime: number
}

/**
 * Asset loading error
 */
export interface AssetLoadError {
  path: string
  type: AssetType
  error: string
  timestamp: number
}

/**
 * Fallback asset configuration
 */
export interface FallbackAsset {
  path: string
  type: AssetType
  data: string // base64 encoded or data URL
  description: string
}

/**
 * Image format support detection
 */
export interface FormatSupport {
  webp: boolean
  avif: boolean
  heic: boolean
  jpeg: boolean
  png: boolean
}

/**
 * Responsive image configuration
 */
export interface ResponsiveImageConfig {
  src: string
  srcSet: string // e.g., "image-320w.jpg 320w, image-640w.jpg 640w"
  sizes: string // e.g., "(max-width: 600px) 100vw, 50vw"
  alt: string
  title?: string
  loading?: 'lazy' | 'eager'
}

/**
 * Asset batch operation result
 */
export interface BatchOperationResult {
  successful: Asset[]
  failed: AssetLoadError[]
  totalTime: number
}

/**
 * Asset transformation options
 */
export interface TransformationOptions {
  rotate?: number // degrees
  flip?: 'horizontal' | 'vertical' | 'both'
  grayscale?: boolean
  sepia?: boolean
  brightness?: number // -100 to 100
  contrast?: number // -100 to 100
  saturation?: number // -100 to 100
}
