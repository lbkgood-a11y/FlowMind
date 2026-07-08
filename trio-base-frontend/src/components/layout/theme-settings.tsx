"use client";

import { Check, Monitor, Moon, Palette, SlidersHorizontal, Sun } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import { useI18n } from "@/lib/i18n";
import {
  usePreferencesStore,
  type AccentPreset,
  type DensityMode,
  type RadiusPreset,
  type ThemeMode,
} from "@/stores/preferences-store";
import { cn } from "@/lib/utils";

const THEME_MODES: Array<{ value: ThemeMode; icon: typeof Sun }> = [
  { value: "light", icon: Sun },
  { value: "dark", icon: Moon },
  { value: "system", icon: Monitor },
];

const ACCENTS: AccentPreset[] = ["blue", "teal", "slate", "rose"];
const DENSITIES: DensityMode[] = ["comfortable", "compact"];
const RADII: RadiusPreset[] = ["soft", "medium", "sharp"];

function OptionButton({
  active,
  children,
  onClick,
}: {
  active: boolean;
  children: React.ReactNode;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={cn(
        "flex min-h-10 items-center justify-between rounded-lg border px-3 py-2 text-sm transition-colors",
        active
          ? "border-primary bg-primary/10 text-foreground"
          : "border-border bg-background text-muted-foreground hover:bg-accent/50 hover:text-foreground",
      )}
    >
      <span>{children}</span>
      {active ? <Check className="size-4 text-primary" /> : null}
    </button>
  );
}

export function ThemeSettings() {
  const themeMode = usePreferencesStore((state) => state.themeMode);
  const accentPreset = usePreferencesStore((state) => state.accentPreset);
  const densityMode = usePreferencesStore((state) => state.densityMode);
  const radiusPreset = usePreferencesStore((state) => state.radiusPreset);
  const locale = usePreferencesStore((state) => state.locale);
  const setThemeMode = usePreferencesStore((state) => state.setThemeMode);
  const setAccentPreset = usePreferencesStore((state) => state.setAccentPreset);
  const setDensityMode = usePreferencesStore((state) => state.setDensityMode);
  const setRadiusPreset = usePreferencesStore((state) => state.setRadiusPreset);
  const setLocale = usePreferencesStore((state) => state.setLocale);
  const { messages } = useI18n();

  return (
    <Dialog>
      <DialogTrigger
        render={
          <Button variant="ghost" size="icon" aria-label={messages.common.themeSettings}>
            <SlidersHorizontal className="size-4" />
          </Button>
        }
      />
      <DialogContent className="sm:max-w-2xl">
        <DialogHeader>
          <DialogTitle>{messages.topbar.settingsTitle}</DialogTitle>
          <DialogDescription>{messages.topbar.settingsDescription}</DialogDescription>
        </DialogHeader>

        <div className="grid gap-6 lg:grid-cols-[1.15fr_0.85fr]">
          <div className="space-y-5">
            <section className="space-y-3">
              <h3 className="text-sm font-medium">{messages.topbar.modeTitle}</h3>
              <div className="grid gap-2 sm:grid-cols-3">
                {THEME_MODES.map(({ value, icon: Icon }) => (
                  <OptionButton
                    key={value}
                    active={themeMode === value}
                    onClick={() => setThemeMode(value)}
                  >
                    <span className="flex items-center gap-2">
                      <Icon className="size-4" />
                      {{
                        light: messages.common.light,
                        dark: messages.common.dark,
                        system: messages.common.system,
                      }[value]}
                    </span>
                  </OptionButton>
                ))}
              </div>
            </section>

            <section className="space-y-3">
              <h3 className="text-sm font-medium">{messages.topbar.accentTitle}</h3>
              <div className="grid gap-2 sm:grid-cols-2">
                {ACCENTS.map((accent) => (
                  <OptionButton
                    key={accent}
                    active={accentPreset === accent}
                    onClick={() => setAccentPreset(accent)}
                  >
                    <span className="flex items-center gap-2">
                      <span
                        className={cn(
                          "size-3 rounded-full",
                          accent === "blue" && "bg-blue-500",
                          accent === "teal" && "bg-teal-500",
                          accent === "slate" && "bg-slate-500",
                          accent === "rose" && "bg-rose-500",
                        )}
                      />
                      {messages.accents[accent]}
                    </span>
                  </OptionButton>
                ))}
              </div>
            </section>

            <section className="space-y-3">
              <h3 className="text-sm font-medium">{messages.topbar.densityTitle}</h3>
              <div className="grid gap-2 sm:grid-cols-2">
                {DENSITIES.map((density) => (
                  <OptionButton
                    key={density}
                    active={densityMode === density}
                    onClick={() => setDensityMode(density)}
                  >
                    {density === "comfortable" ? messages.common.comfortable : messages.common.compact}
                  </OptionButton>
                ))}
              </div>
            </section>

            <section className="space-y-3">
              <h3 className="text-sm font-medium">{messages.topbar.radiusTitle}</h3>
              <div className="grid gap-2 sm:grid-cols-3">
                {RADII.map((radius) => (
                  <OptionButton
                    key={radius}
                    active={radiusPreset === radius}
                    onClick={() => setRadiusPreset(radius)}
                  >
                    {{
                      soft: messages.common.soft,
                      medium: messages.common.medium,
                      sharp: messages.common.sharp,
                    }[radius]}
                  </OptionButton>
                ))}
              </div>
            </section>

            <section className="space-y-3">
              <h3 className="text-sm font-medium">{messages.topbar.languageTitle}</h3>
              <div className="grid gap-2 sm:grid-cols-2">
                <OptionButton active={locale === "zh-CN"} onClick={() => setLocale("zh-CN")}>
                  {messages.common.zh}
                </OptionButton>
                <OptionButton active={locale === "en-US"} onClick={() => setLocale("en-US")}>
                  {messages.common.en}
                </OptionButton>
              </div>
            </section>
          </div>

          <aside className="rounded-xl border bg-muted/20 p-4">
            <div className="mb-4 flex items-center gap-2 text-sm font-medium">
              <Palette className="size-4 text-muted-foreground" />
              {messages.topbar.settingsPreviewTitle}
            </div>
            <div className="space-y-4">
              <div className="rounded-xl border bg-card p-4 shadow-sm">
                <div className="mb-3 flex items-center justify-between">
                  <div>
                    <p className="text-sm font-medium">{messages.common.appearance}</p>
                    <p className="text-xs text-muted-foreground">{messages.topbar.settingsPreviewBody}</p>
                  </div>
                  <span className="rounded-full bg-primary/10 px-2.5 py-1 text-xs font-medium text-primary">
                    {messages.accents[accentPreset]}
                  </span>
                </div>
                <div className="grid gap-3">
                  <div className="rounded-lg bg-muted px-3 py-2 text-sm text-muted-foreground">
                    {messages.topbar.modeTitle}: {{
                      light: messages.common.light,
                      dark: messages.common.dark,
                      system: messages.common.system,
                    }[themeMode]}
                  </div>
                  <div className="flex gap-2">
                    <span className="inline-flex rounded-full border px-2.5 py-1 text-xs">
                      {densityMode === "comfortable" ? messages.common.comfortable : messages.common.compact}
                    </span>
                    <span className="inline-flex rounded-full border px-2.5 py-1 text-xs">
                      {{
                        soft: messages.common.soft,
                        medium: messages.common.medium,
                        sharp: messages.common.sharp,
                      }[radiusPreset]}
                    </span>
                  </div>
                </div>
              </div>
              <div className="rounded-xl border bg-background p-4">
                <div className="mb-3 text-sm font-medium">{messages.topbar.languageTitle}</div>
                <div className="space-y-2 text-sm text-muted-foreground">
                  <p>{locale === "zh-CN" ? messages.common.zh : messages.common.en}</p>
                  <p>{messages.common.brand}</p>
                </div>
              </div>
            </div>
          </aside>
        </div>
      </DialogContent>
    </Dialog>
  );
}
