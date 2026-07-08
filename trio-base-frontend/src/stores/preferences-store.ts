import { create } from "zustand";
import { persist } from "zustand/middleware";

export type AppLocale = "zh-CN" | "en-US";
export type ThemeMode = "light" | "dark" | "system";
export type AccentPreset = "blue" | "teal" | "slate" | "rose";
export type DensityMode = "comfortable" | "compact";
export type RadiusPreset = "sharp" | "medium" | "soft";

interface PreferencesState {
  locale: AppLocale;
  themeMode: ThemeMode;
  accentPreset: AccentPreset;
  densityMode: DensityMode;
  radiusPreset: RadiusPreset;
  setLocale: (locale: AppLocale) => void;
  setThemeMode: (themeMode: ThemeMode) => void;
  setAccentPreset: (accentPreset: AccentPreset) => void;
  setDensityMode: (densityMode: DensityMode) => void;
  setRadiusPreset: (radiusPreset: RadiusPreset) => void;
}

export const usePreferencesStore = create<PreferencesState>()(
  persist(
    (set) => ({
      locale: "zh-CN",
      themeMode: "system",
      accentPreset: "blue",
      densityMode: "comfortable",
      radiusPreset: "medium",
      setLocale: (locale) => set({ locale }),
      setThemeMode: (themeMode) => set({ themeMode }),
      setAccentPreset: (accentPreset) => set({ accentPreset }),
      setDensityMode: (densityMode) => set({ densityMode }),
      setRadiusPreset: (radiusPreset) => set({ radiusPreset }),
    }),
    {
      name: "triobase-preferences",
    },
  ),
);
