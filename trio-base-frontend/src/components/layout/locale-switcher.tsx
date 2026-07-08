"use client";

import { Languages } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { useI18n } from "@/lib/i18n";
import type { AppLocale } from "@/stores/preferences-store";

const LOCALES: AppLocale[] = ["zh-CN", "en-US"];

export function LocaleSwitcher() {
  const { locale, setLocale, messages } = useI18n();

  return (
    <DropdownMenu>
      <DropdownMenuTrigger
        render={
          <Button variant="ghost" size="icon" aria-label={messages.common.language}>
            <Languages className="size-4" />
          </Button>
        }
      />
      <DropdownMenuContent align="end" className="w-36">
        {LOCALES.map((item) => (
          <DropdownMenuItem
            key={item}
            onClick={() => setLocale(item)}
            className={locale === item ? "bg-accent text-accent-foreground" : ""}
          >
            {item === "zh-CN" ? messages.common.zh : messages.common.en}
          </DropdownMenuItem>
        ))}
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
