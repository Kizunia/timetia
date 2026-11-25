"use client";

import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import type { ResourceKey } from "@/features/setup/api";
import { ResourceTable } from "@/features/setup/components/ResourceTable";
import { RESOURCES } from "@/features/setup/resources";
import { useSetupUi } from "@/features/setup/store";

/** The campus data the timetable is built from, one list at a time. */
export function SetupScreen() {
  const active = useSetupUi((s) => s.active);
  const setActive = useSetupUi((s) => s.setActive);
  return (
    <section aria-labelledby="campus-heading" className="grid grid-cols-1 gap-4">
      <h1 id="campus-heading" className="text-2xl font-semibold tracking-tight">
        Campus
      </h1>
      <Tabs value={active} onValueChange={(value) => setActive(value as ResourceKey)}>
        <div className="overflow-x-auto">
          <TabsList>
            {RESOURCES.map((resource) => (
              <TabsTrigger key={resource.key} value={resource.key}>
                {resource.title}
              </TabsTrigger>
            ))}
          </TabsList>
        </div>
        {RESOURCES.map((resource) => (
          <TabsContent key={resource.key} value={resource.key} className="mt-4">
            <ResourceTable resource={resource} />
          </TabsContent>
        ))}
      </Tabs>
    </section>
  );
}
