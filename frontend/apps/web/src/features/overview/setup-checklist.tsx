import { Circle, CircleCheck } from "lucide-react";
import Link from "next/link";
import type { SetupStep } from "./setup-steps";

/**
 * First-run guide on the overview: a quiet sheet, not a wizard. Each step ticks itself from real data and links to
 * the screen where it is done; finished steps say "Done" (strike-through already means "cancelled" in this app).
 */
export function SetupChecklist({ steps, hideAction }: {
  steps: SetupStep[];
  hideAction: (formData: FormData) => void | Promise<void>;
}) {
  const doneCount = steps.filter((s) => s.done).length;
  return (
    <section aria-labelledby="setup-heading" className="rounded-sheet bg-panel p-6 shadow-sheet">
      <div className="flex flex-wrap items-start justify-between gap-x-6 gap-y-2">
        <div>
          <h2 id="setup-heading" className="text-lg font-semibold">Set up your books</h2>
          <p className="mt-1 text-mute">
            {doneCount} of {steps.length} done. A few minutes; the optional steps can wait.
          </p>
        </div>
        <form action={hideAction}>
          <button type="submit" className="-mx-3 rounded-md px-3 py-1.5 text-sm font-medium text-indigo hover:bg-rule">
            Hide these steps
          </button>
        </form>
      </div>
      <ol className="mt-4">
        {steps.map((s) => (
          <li key={s.id} className="flex gap-3 border-t border-rule py-3">
            {s.done
              ? <CircleCheck aria-hidden size={20} strokeWidth={1.75} className="mt-0.5 shrink-0 text-indigo" />
              : <Circle aria-hidden size={20} strokeWidth={1.75} className="mt-0.5 shrink-0 text-mute" />}
            <div className="min-w-0">
              <p className="flex flex-wrap items-baseline gap-x-2">
                {s.done ? (
                  <>
                    <span className="font-medium text-mute">{s.title}</span>
                    <span className="text-sm font-medium text-indigo">Done</span>
                  </>
                ) : (
                  <>
                    <Link href={s.href} className="font-medium text-indigo underline">
                      {s.title}
                    </Link>
                    {s.optional && <span className="text-sm text-mute">Optional</span>}
                  </>
                )}
              </p>
              {!s.done && <p className="mt-0.5 text-sm text-mute">{s.detail}</p>}
            </div>
          </li>
        ))}
      </ol>
    </section>
  );
}
