import { RoleEditor } from "@/features/access/RoleEditor";

export default async function RolePage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return <RoleEditor roleId={id} />;
}
