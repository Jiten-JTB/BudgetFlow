import { ArrowLeftRight, BookOpen, HandCoins, Landmark, LogOut, Plus, Settings, Tags, Target } from "lucide-react";
import Link from "next/link";
import { NavLink } from "./nav-link";

export const NAV = [
  { href: "/", label: "Overview", icon: BookOpen },
  { href: "/transactions", label: "Transactions", icon: ArrowLeftRight },
  { href: "/accounts", label: "Accounts", icon: Landmark },
  { href: "/budgets", label: "Budgets", icon: Target },
  { href: "/income-sources", label: "Income sources", icon: HandCoins },
  { href: "/categories", label: "Categories", icon: Tags },
  { href: "/settings", label: "Settings", icon: Settings },
];

/** The ledger's spine: vermilion bookcloth with a turmeric thread along its edge. */
export function Rail({ displayName }: { displayName: string }) {
  return (
    // The cloth column runs the full page height; the rail inside stays pinned while the page scrolls.
    <div className="on-cloth hidden w-60 shrink-0 border-r-4 border-thread bg-cloth lg:block">
    <aside className="sticky top-0 flex h-dvh flex-col px-4 py-6 text-cloth-ink">
      <Link href="/" className="mb-8 px-3 text-xl font-bold tracking-tight text-cloth-ink">Budget Flow</Link>
      <nav aria-label="Main" className="flex flex-col gap-1">
        {NAV.map(({ href, label, icon: Icon }) => (
          <NavLink key={href} href={href}>
            <Icon aria-hidden size={18} strokeWidth={1.75} />
            {label}
          </NavLink>
        ))}
      </nav>
      <div className="mt-auto flex flex-col gap-4">
        <Link
          href="/transactions/new"
          className="flex items-center justify-center gap-2 rounded-md bg-thread px-4 py-3 font-semibold text-indigo shadow-[0_2px_6px_-2px_rgb(0_0_0/0.35)] transition-colors hover:bg-[#ebb33c]"
        >
          <Plus aria-hidden size={18} strokeWidth={2.25} />
          Add transaction
        </Link>
        <form action="/api/auth/logout" method="post" className="flex items-center justify-between gap-2 px-1">
          <span className="truncate text-sm text-cloth-mute">{displayName}</span>
          <button type="submit" className="flex items-center gap-1.5 rounded-md px-2 py-1 text-sm text-cloth-mute hover:text-cloth-ink">
            <LogOut aria-hidden size={16} strokeWidth={1.75} />
            Sign out
          </button>
        </form>
      </div>
    </aside>
    </div>
  );
}
