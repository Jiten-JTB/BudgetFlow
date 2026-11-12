import Link from "next/link";

/** The one-sheet layout of public pages (signed out, confirm email, account deleted): wordmark, title, content. */
export function PublicSheet({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <main className="grid min-h-dvh place-items-center bg-page px-4 py-10">
      <div className="w-full max-w-md rounded-sheet bg-panel p-8 shadow-sheet">
        <Link href="/" className="mb-6 inline-block text-lg font-bold tracking-tight text-cloth">Budget Flow</Link>
        <h1 className="mb-3 text-2xl font-semibold">{title}</h1>
        {children}
      </div>
    </main>
  );
}
