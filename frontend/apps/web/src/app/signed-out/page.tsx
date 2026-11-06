import Link from "next/link";

export const metadata = { title: "Signed out" };

export default async function SignedOut({ searchParams }: { searchParams: Promise<{ reason?: string }> }) {
  const failed = (await searchParams).reason === "sign-in-failed";
  return (
    <main className="grid min-h-dvh place-items-center bg-page px-6">
      <div className="w-full max-w-sm rounded-sheet bg-panel p-8 shadow-sheet">
        <h1 className="text-2xl font-semibold">{failed ? "Sign-in didn’t finish" : "You’re signed out"}</h1>
        <p className="mt-2 text-mute">
          {failed ? "The sign-in link expired or was already used. Start again to continue." : "Sign in again to see your accounts, or create an account if you’re new."}
        </p>
        <Link href="/api/auth/login" className="mt-6 inline-flex w-full justify-center rounded-md bg-indigo px-4 py-3 font-semibold text-white hover:bg-[#1d2549]">
          Sign in
        </Link>
        <Link href="/signup" className="mt-3 inline-flex w-full justify-center rounded-md px-4 py-3 font-medium text-indigo hover:bg-rule">
          Create an account
        </Link>
      </div>
    </main>
  );
}
