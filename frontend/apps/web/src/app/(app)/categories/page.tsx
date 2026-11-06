import type { Category } from "@budgetflow/api-client";
import { ArchiveItemButton, CategoryForm } from "@/features/catalog/catalog-forms";
import { serverApi } from "@/server/api";

export const metadata = { title: "Categories" };

export default async function CategoriesPage() {
  const categories = await serverApi().categories.list();
  const tops = categories.filter((c) => !c.parentId);

  return (
    <div className="flex flex-col gap-6">
      <h1 className="text-3xl font-semibold tracking-tight">Categories</h1>
      <div className="grid gap-6 lg:grid-cols-2">
        <CategoryColumn title="Spending" categories={categories} tops={tops.filter((c) => c.kind === "EXPENSE")} />
        <CategoryColumn title="Income" categories={categories} tops={tops.filter((c) => c.kind === "INCOME")} />
      </div>
      <section aria-labelledby="add-category-heading" className="rounded-sheet bg-panel p-6 shadow-sheet">
        <h2 id="add-category-heading" className="mb-4 text-lg font-semibold">Add a category</h2>
        <CategoryForm parents={tops.map(({ id, name, kind }) => ({ id, name, kind }))} />
      </section>
    </div>
  );
}

function CategoryColumn({ title, categories, tops }: { title: string; categories: Category[]; tops: Category[] }) {
  const id = `${title.toLowerCase()}-heading`;
  return (
    <section aria-labelledby={id} className="rounded-sheet bg-panel p-6 shadow-sheet">
      <h2 id={id} className="mb-2 text-lg font-semibold">{title}</h2>
      <ul>
        {tops.sort((a, b) => a.name.localeCompare(b.name)).map((c) => (
          <li key={c.id} className="border-t border-rule py-2 first:border-t-0">
            <div className="flex items-center justify-between gap-3">
              <span className="font-medium">{c.name}</span>
              <ArchiveItemButton kind="category" id={c.id} label={c.name} />
            </div>
            <ul className="ml-4">
              {categories.filter((s) => s.parentId === c.id).map((s) => (
                <li key={s.id} className="flex items-center justify-between gap-3 py-1 text-sm">
                  <span className="text-mute">{s.name}</span>
                  <ArchiveItemButton kind="category" id={s.id} label={s.name} />
                </li>
              ))}
            </ul>
          </li>
        ))}
      </ul>
    </section>
  );
}
