"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";

export function NavLink({ href, children }: { href: string; children: React.ReactNode }) {
  const pathname = usePathname();
  const active = href === "/" ? pathname === "/" : pathname === href || pathname.startsWith(`${href}/`);
  return (
    <Link
      href={href}
      aria-current={active ? "page" : undefined}
      className={`flex items-center gap-3 rounded-md px-3 py-2 text-[0.9375rem] transition-colors ${
        active ? "bg-cloth-deep font-medium text-cloth-ink" : "text-cloth-mute hover:bg-cloth-deep/60 hover:text-cloth-ink"
      }`}
    >
      {children}
    </Link>
  );
}
