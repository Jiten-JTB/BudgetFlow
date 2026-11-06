import type { Transaction } from "@budgetflow/api-client";
import { buildOverview } from "@/features/overview/build-overview";
import { OverviewView } from "@/features/overview/overview-view";
import { requestLocale } from "@/lib/locale";
import { currentMonth, monthLabel, monthRange, parseMonth } from "@/lib/month";
import { serverApi } from "@/server/api";

export const metadata = { title: "Overview" };

const PAGE_SIZE = 200;
const MAX_PAGES = 10; // 2,000 transactions a month; monthly reports move to reporting-service later

export default async function OverviewPage({ searchParams }: { searchParams: Promise<{ month?: string }> }) {
  const api = serverApi();
  const [me, locale] = await Promise.all([api.me.get(), requestLocale()]);
  const thisMonth = currentMonth(me.timezone);
  const month = parseMonth((await searchParams).month) ?? thisMonth;
  const { from, to } = monthRange(month);

  const [transactions, accounts, categories, incomeSources, budgets] = await Promise.all([
    monthTransactions(api, from, to),
    api.accounts.list({ includeArchived: true }), // archived names still label old transactions
    api.categories.list({ includeArchived: true }),
    api.incomeSources.list({ includeArchived: true }),
    api.budgets.statuses({ date: month === thisMonth ? undefined : to }),
  ]);

  const overview = buildOverview({ baseCurrency: me.baseCurrency, transactions, categories, incomeSources, accounts, budgets });
  return (
    <OverviewView
      overview={overview}
      month={month}
      monthTitle={monthLabel(month, locale)}
      locale={locale}
      isCurrentMonth={month >= thisMonth}
      accountNames={Object.fromEntries(accounts.map((a) => [a.id, a.name]))}
      categoryNames={Object.fromEntries(categories.map((c) => [c.id, c.name]))}
    />
  );
}

async function monthTransactions(api: ReturnType<typeof serverApi>, from: string, to: string): Promise<Transaction[]> {
  const all: Transaction[] = [];
  let cursor: string | undefined;
  for (let page = 0; page < MAX_PAGES; page++) {
    const result = await api.transactions.list({ from, to, limit: PAGE_SIZE, cursor });
    all.push(...result.items);
    if (!result.nextCursor) break;
    cursor = result.nextCursor;
  }
  return all;
}
