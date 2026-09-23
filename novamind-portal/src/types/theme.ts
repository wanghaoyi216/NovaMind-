/**
 * UI theme and animation related types
 * Validates: Requirements 7.1, 7.2, 7.3, 7.4, 7.5, 7.6, 7.7, 7.8, 7.9
 */

/**
 * UI theme mode
 */
export enum ThemeMode {
  GLASSMORPHISM = 'glassmorphism',
  NEUMORPHISM = 'neumorphism',
  STANDARD = 'standard'
}

/**
 * UITheme interface for theme configuration
 * Requirement 7.1, 7.2, 7.3: UITheme interface with mode, primaryColor, accentColor, backgroundAsset, animations
 */
export interface UITheme {
  mode: ThemeMode
  primaryColor: string
  accentColor: string
  backgroundAsset: string
  animations: AnimationConfig
}

/**
 * AnimationConfig interface for animation settings
 * Requirement 7.4, 7.5, 7.6, 7.7, 7.8, 7.9: AnimationConfig interface for animation settings
 */
export interface AnimationConfig {
  // Glassmorphism animations
  glassmorphismBlur: number // blur amount in pixels
  glassmorphismOpacity: number // background opacity 0-1
  glassmorphismBorderOpacity: number // border opacity 0-1

  // Neumorphism animations
  neumorphismShadowDistance: number // shadow distance in pixels
  neumorphismShadowBlur: number // shadow blur in pixels
  neumorphismHoverEffect: boolean // enable hover inset shadow effect

  // Gradient animations
  gradientAnimationDuration: number // duration in milliseconds
  gradientAnimationEnabled: boolean // enable animated gradient background

  // Message bubble animations
  messageBubbleSlideInDuration: number // slide-in animation duration in ms
  messageBubbleSlideInDistance: number // slide-in distance in pixels

  // Micro-interaction animations
  buttonHoverScale: number // scale factor on hover (e.g., 1.05)
  buttonHoverDuration: number // hover animation duration in ms
  buttonLiftDistance: number // lift distance on hover in pixels
  buttonLiftDuration: number // lift animation duration in ms

  // Thought visualization animations
  thoughtPulseDuration: number // pulse animation duration in ms
  thoughtPulseOpacityMin: number // minimum opacity during pulse
  thoughtPulseOpacityMax: number // maximum opacity during pulse
  thoughtIconRotateDuration: number // icon rotation duration in ms

  // Loading animations
  loadingDotsBounceHeight: number // bounce height in pixels
  loadingDotsBounceDelay: number // delay between dots in ms
  loadingDotsAnimationDuration: number // total animation duration in ms

  // Transition settings
  transitionDuration: number // default transition duration in ms
  transitionEasing: string // default easing function (e.g., 'ease-in-out')
}

/**
 * Color palette for the theme
 */
export interface ColorPalette {
  primary: string
  secondary: string
  accent: string
  background: string
  surface: string
  text: string
  textSecondary: string
  border: string
  error: string
  warning: string
  success: string
  info: string
}

/**
 * Glassmorphism style configuration
 * Requirement 7.1: Glassmorphism styling with transparency, backdrop blur, and subtle borders
 */
export interface GlassmorphismStyle {
  backgroundColor: string // rgba with low alpha
  backdropFilter: string // blur amount
  borderColor: string // rgba white with low alpha
  borderRadius: string // border radius in pixels
  boxShadow: string // depth shadow
}

/**
 * Neumorphism style configuration
 * Requirement 7.2, 7.3: Neumorphism styling with soft shadows and hover effects
 */
export interface NeumorphismStyle {
  backgroundColor: string
  borderRadius: string
  boxShadow: string // dual shadow for embossed effect
  hoverBoxShadow: string // inset shadow for pressed effect
  transition: string
}

/**
 * Animation keyframe definition
 */
export interface KeyframeAnimation {
  name: string
  frames: Record<string, Record<string, string | number>>
  duration: number // in milliseconds
  easing: string
  iterationCount: string | number
  delay?: number
}

/**
 * Gradient configuration
 * Requirement 7.4: Animated gradient background
 */
export interface GradientConfig {
  colors: string[]
  angle: number // in degrees
  animationDuration: number // in milliseconds
  animationEnabled: boolean
}

/**
 * Transition configuration
 */
export interface TransitionConfig {
  property: string | string[]
  duration: number // in milliseconds
  easing: string
  delay?: number
}

/**
 * Shadow configuration
 */
export interface ShadowConfig {
  offsetX: number
  offsetY: number
  blur: number
  spread: number
  color: string
  opacity: number
}

/**
 * Border configuration
 */
export interface BorderConfig {
  width: number
  style: 'solid' | 'dashed' | 'dotted'
  color: string
  radius: number
}

/**
 * Spacing configuration
 */
export interface SpacingConfig {
  xs: number
  sm: number
  md: number
  lg: number
  xl: number
  xxl: number
}

/**
 * Typography configuration
 */
export interface TypographyConfig {
  fontFamily: string
  fontSize: {
    xs: number
    sm: number
    base: number
    lg: number
    xl: number
    xxl: number
  }
  fontWeight: {
    light: number
    normal: number
    semibold: number
    bold: number
  }
  lineHeight: {
    tight: number
    normal: number
    relaxed: number
  }
}

/**
 * Breakpoint configuration for responsive design
 */
export interface BreakpointConfig {
  xs: number
  sm: number
  md: number
  lg: number
  xl: number
  xxl: number
}

/**
 * Complete theme configuration
 */
export interface ThemeConfig {
  mode: ThemeMode
  colors: ColorPalette
  animations: AnimationConfig
  spacing: SpacingConfig
  typography: TypographyConfig
  breakpoints: BreakpointConfig
  glassmorphism?: GlassmorphismStyle
  neumorphism?: NeumorphismStyle
}

/**
 * Theme context for React components
 */
export interface ThemeContextType {
  theme: UITheme
  setTheme: (theme: UITheme) => void
  toggleMode: (mode: ThemeMode) => void
}
