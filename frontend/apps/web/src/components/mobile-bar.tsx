import { Menu, Plus } from "lucide-react";
import Link from "next/link";
import { NavLink } from "./nav-link";
import { NAV } from "./rail";

/** Below desktop width the spine folds into a top bar; the menu is a native disclosure (works without JS). */
export function MobileBar({ displayName }: { displayName: string }) {
  return (
    <header className="on-cloth sticky top-0 z-10 border-b-4 border-thread bg-cloth text-cloth-ink lg:hidden">
      <div className="flex h-14 items-center gap-2 px-4">
        <Link href="/" className="mr-auto text-lg font-bold tracking-tight text-cloth-ink">Budget Flow</Link>
        <Link href="/transactions/new" className="flex items-center gap-1.5 rounded-md bg-thread px-3 py-2 text-sm font-semibold text-indigo">
          <Plus aria-hidden size={16} strokeWidth={2.25} />
          Add
        </Link>
        <details>
          <summary className="flex cursor-pointer list-none items-center gap-1.5 rounded-md px-2 py-2 text-sm hover:bg-cloth-deep [&::-webkit-details-marker]:hidden">
            <Menu aria-hidden size={18} strokeWidth={1.75} />
            Menu
          </summary>
          {/* Positioned against the sticky header, so the panel spans the full width under the bar. */}
          <nav aria-label="Main" className="absolute inset-x-0 top-full flex flex-col gap-1 border-b-4 border-thread bg-cloth px-3 pb-4 pt-2 shadow-sheet">
            {NAV.map(({ href, label, icon: Icon }) => (
              <NavLink key={href} href={href}>
                <Icon aria-hidden size={18} strokeWidth={1.75} />
                {label}
              </NavLink>
            ))}
            <form action="/api/auth/logout" method="post" className="mt-2 flex items-center justify-between border-t border-cloth-deep px-3 pt-3">
              <span className="text-sm text-cloth-mute">{displayName}</span>
              <button type="submit" className="text-sm text-cloth-mute hover:text-cloth-ink">Sign out</button>
            </form>
          </nav>
        </details>
      </div>
    </header>
  );
}
