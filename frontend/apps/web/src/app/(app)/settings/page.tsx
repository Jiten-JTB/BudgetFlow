import { DeleteAccountForm } from "@/features/settings/delete-account-form";
import { ProfileForm } from "@/features/settings/profile-form";
import { serverApi } from "@/server/api";

export const metadata = { title: "Settings" };

export default async function SettingsPage() {
  const me = await serverApi().me.get();
  return (
    <div className="mx-auto flex max-w-2xl flex-col gap-8">
      <h1 className="text-3xl font-semibold tracking-tight">Settings</h1>
      <ProfileForm me={me} />
      <section aria-labelledby="delete-heading" className="flex flex-col gap-3 rounded-sheet bg-panel p-7 shadow-sheet">
        <h2 id="delete-heading" className="text-lg font-semibold">Delete your account</h2>
        <p className="text-mute">Signed up as {me.email}. Deleting removes everything Budget Flow keeps about you.</p>
        <div><DeleteAccountForm /></div>
      </section>
    </div>
  );
}
