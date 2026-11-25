"use client";

import { useTheme } from "next-themes";
import { useEffect } from "react";

import type { Me } from "@/lib/api/client";

/** Applies the colour scheme the user chose on their profile (it is stored with the profile, so it follows them). */
export function ThemeSync({ theme }: { theme: Me["theme"] }) {
  const { setTheme } = useTheme();
  useEffect(() => {
    setTheme(theme.toLowerCase());
  }, [theme, setTheme]);
  return null;
}
