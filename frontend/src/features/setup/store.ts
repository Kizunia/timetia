"use client";

import { create } from "zustand";

import type { ResourceKey } from "@/features/setup/api";

interface SetupUi {
  active: ResourceKey;
  setActive: (active: ResourceKey) => void;
}

/** Which campus list is open, kept while the person moves around the administration area. */
export const useSetupUi = create<SetupUi>((set) => ({
  active: "departments",
  setActive: (active) => set({ active }),
}));
