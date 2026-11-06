import { OpenAccountForm } from "@/features/accounts/account-forms";
import { serverApi } from "@/server/api";

export const metadata = { title: "Open an account" };

export default async function NewAccountPage() {
  const me = await serverApi().me.get();
  return (
    <div className="mx-auto flex max-w-2xl flex-col gap-6">
      <h1 className="text-3xl font-semibold tracking-tight">Open an account</h1>
      <OpenAccountForm baseCurrency={me.baseCurrency} />
    </div>
  );
}
