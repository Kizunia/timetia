import { UserDetailScreen } from "@/features/identity/UserDetailScreen";

export default async function UserPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return <UserDetailScreen id={id} />;
}
