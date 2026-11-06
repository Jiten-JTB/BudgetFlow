import { MobileBar } from "@/components/mobile-bar";
import { Rail } from "@/components/rail";
import { serverApi } from "@/server/api";

// Every signed-in page is per-user and per-request: never prerender at build time.
export const dynamic = "force-dynamic";

export default async function AppLayout({ children }: { children: React.ReactNode }) {
  const me = await serverApi().me.get();
  return (
    <div className="flex min-h-dvh flex-col lg:flex-row">
      <Rail displayName={me.displayName} />
      <MobileBar displayName={me.displayName} />
      <main id="main" className="min-w-0 flex-1 px-4 py-6 sm:px-6 lg:px-10 lg:py-8">
        <div className="mx-auto max-w-[1240px]">{children}</div>
      </main>
    </div>
  );
}
