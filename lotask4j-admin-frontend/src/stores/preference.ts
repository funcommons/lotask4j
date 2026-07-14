// src/stores/preference.ts
import { defineStore } from 'pinia'
import type { Brand, Locale, Preference, Theme } from '@/types/preference'
import { getItem, setItem } from '@/utils/storage'

const STORAGE_KEY = 'preference'

const DEFAULT_PREFERENCE: Preference = {
  theme: 'light',
  brand: 'ldx2',
  locale: 'zh-CN',
  sidebarCollapsed: false,
  sidebarWidth: 240,
  showAppearance: false,
}

export const usePreferenceStore = defineStore('preference', {
  state: (): Preference => getItem<Preference>(STORAGE_KEY, DEFAULT_PREFERENCE),
  getters: {
    isDark: (s) => s.theme === 'dark' || s.theme === 'orange-black',
  },
  actions: {
    setTheme(theme: Theme) {
      this.theme = theme
      this.applyToRoot()
      this.persist()
    },
    setBrand(brand: Brand) {
      this.brand = brand
      this.applyToRoot()
      this.persist()
    },
    setLocale(locale: Locale) {
      this.locale = locale
      this.persist()
    },
    toggleSidebar() {
      this.sidebarCollapsed = !this.sidebarCollapsed
      this.persist()
    },
    setSidebarWidth(width: number) {
      this.sidebarWidth = width
      this.persist()
    },
    setSidebarCollapsed(collapsed: boolean) {
      this.sidebarCollapsed = collapsed
      this.persist()
    },
    setShowAppearance(v: boolean) {
      this.showAppearance = v
      this.persist()
    },
    applyToRoot() {
      const html = document.documentElement
      html.setAttribute('data-theme', this.theme)
      html.setAttribute('data-brand', this.brand)
      html.style.setProperty('--app-sidebar-width', `${this.sidebarWidth}px`)
    },
    persist() {
      setItem(STORAGE_KEY, {
        theme: this.theme,
        brand: this.brand,
        locale: this.locale,
        sidebarCollapsed: this.sidebarCollapsed,
        sidebarWidth: this.sidebarWidth,
        // showAppearance is intentionally NOT persisted: it is a UI-only
        // ephemeral flag and persisting it would re-open the appearance
        // drawer on every page refresh.
      })
    },
    init() {
      this.applyToRoot()
    },
  },
})
