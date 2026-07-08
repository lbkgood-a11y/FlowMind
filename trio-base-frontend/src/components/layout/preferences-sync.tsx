"use client";

import { useEffect } from "react";
import { useTheme } from "next-themes";
import { usePreferencesStore } from "@/stores/preferences-store";

export function PreferencesSync() {
  const locale = usePreferencesStore((state) => state.locale);
  const themeMode = usePreferencesStore((state) => state.themeMode);
  const accentPreset = usePreferencesStore((state) => state.accentPreset);
  const densityMode = usePreferencesStore((state) => state.densityMode);
  const radiusPreset = usePreferencesStore((state) => state.radiusPreset);
  const { setTheme } = useTheme();

  useEffect(() => {
    setTheme(themeMode);
  }, [setTheme, themeMode]);

  useEffect(() => {
    const root = document.documentElement;
    root.lang = locale;
    root.dataset.locale = locale;
    root.dataset.accent = accentPreset;
    root.dataset.density = densityMode;
    root.dataset.radius = radiusPreset;
  }, [accentPreset, densityMode, locale, radiusPreset]);

  return null;
}
