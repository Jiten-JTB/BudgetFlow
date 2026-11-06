import { ProfileForm } from "@/features/settings/profile-form";
import { serverApi } from "@/server/api";

export const metadata = { title: "Settings" };

export default async function SettingsPage() {
  const me = await serverApi().me.get();
  return (
    <div className="mx-auto flex max-w-2xl flex-col gap-6">
      <h1 className="text-3xl font-semibold tracking-tight">Settings</h1>
      <ProfileForm me={me} />
    </div>
  );
}
