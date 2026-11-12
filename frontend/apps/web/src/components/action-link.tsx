import Link from "next/link";

const sizes = {
  md: "px-4 py-2 font-medium",
  sm: "px-3 py-1.5 text-sm font-medium",
};

/**
 * A link that starts a task, drawn as a secondary button: white, ruled border, indigo text. It stays an <a> because it
 * navigates; the indigo filled button is kept for each form's one submit.
 */
export function ActionLink({ href, size = "md", children }: { href: string; size?: keyof typeof sizes; children: React.ReactNode }) {
  return (
    <Link href={href}
      className={`inline-flex w-fit items-center rounded-md border border-rule-strong bg-panel text-indigo hover:border-indigo hover:bg-page ${sizes[size]}`}>
      {children}
    </Link>
  );
}

/** Empty sheet or list: say what is missing, then offer the one action that fills it. */
export function EmptyState({ text, action, href }: { text: React.ReactNode; action: string; href: string }) {
  return (
    <div className="flex flex-col items-start gap-3">
      <p className="text-mute">{text}</p>
      <ActionLink href={href}>{action}</ActionLink>
    </div>
  );
}
