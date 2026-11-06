import { ArchiveItemButton, IncomeSourceForm } from "@/features/catalog/catalog-forms";
import { SOURCE_TYPES } from "@/features/catalog/source-types";
import { serverApi } from "@/server/api";

export const metadata = { title: "Income sources" };
const TYPE_LABEL = Object.fromEntries(SOURCE_TYPES);

export default async function IncomeSourcesPage() {
  const api = serverApi();
  const [sources, accounts] = await Promise.all([api.incomeSources.list(), api.accounts.list()]);
  const accountNames = Object.fromEntries(accounts.map((a) => [a.id, a.name]));

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-col gap-1">
        <h1 className="text-3xl font-semibold tracking-tight">Income sources</h1>
        <p className="text-mute">Name where your money comes from, and the overview shows income by source.</p>
      </div>
      <section aria-labelledby="sources-heading" className="rounded-sheet bg-panel p-6 shadow-sheet">
        <h2 id="sources-heading" className="mb-2 text-lg font-semibold">Your sources</h2>
        {sources.length === 0 ? <p className="text-mute">No income sources yet. Add your employer, clients or rental income below.</p> : (
          <ul>
            {sources.map((s) => (
              <li key={s.id} className="flex flex-wrap items-center justify-between gap-3 border-t border-rule py-3 first:border-t-0">
                <span>
                  <span className="font-medium">{s.name}</span>
                  <span className="ml-2 text-sm text-mute">
                    {TYPE_LABEL[s.type]}{s.defaultAccountId && accountNames[s.defaultAccountId] ? `, paid into ${accountNames[s.defaultAccountId]}` : ""}
                  </span>
                </span>
                <ArchiveItemButton kind="incomeSource" id={s.id} label={s.name} />
              </li>
            ))}
          </ul>
        )}
      </section>
      <section aria-labelledby="add-source-heading" className="rounded-sheet bg-panel p-6 shadow-sheet">
        <h2 id="add-source-heading" className="mb-4 text-lg font-semibold">Add an income source</h2>
        <IncomeSourceForm accounts={accounts.map(({ id, name }) => ({ id, name }))} />
      </section>
    </div>
  );
}
