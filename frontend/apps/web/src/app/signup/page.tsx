import Link from "next/link";
import { SignupForm } from "@/features/signup/signup-form";
import { signUp } from "./actions";

export const metadata = { title: "Create your account" };

export default function SignupPage() {
  return (
    <main className="grid min-h-dvh lg:grid-cols-[minmax(20rem,28rem)_1fr]">
      <aside className="on-cloth hidden flex-col justify-between border-r-4 border-thread bg-cloth p-10 text-cloth-ink lg:flex">
        <span className="text-xl font-bold tracking-tight">Budget Flow</span>
        <p className="max-w-xs text-2xl leading-snug font-semibold">
          See where your money comes from, where it goes, and what you kept.
        </p>
        <span aria-hidden />
      </aside>
      <div className="grid place-items-center bg-page px-4 py-10">
        <div className="w-full max-w-md rounded-sheet bg-panel p-8 shadow-sheet">
          <p className="mb-1 text-lg font-bold tracking-tight text-cloth lg:hidden">Budget Flow</p>
          <h1 className="mb-6 text-2xl font-semibold">Create your account</h1>
          <SignupForm action={signUp} />
          <p className="mt-6 text-sm text-mute">
            Already have an account? <Link href="/api/auth/login" className="text-indigo underline">Sign in</Link>
          </p>
        </div>
      </div>
    </main>
  );
}
