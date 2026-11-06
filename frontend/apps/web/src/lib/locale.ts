import { headers } from "next/headers";

/** The reader's locale from the browser, so ₹1,50,000 and $150,000.00 each look right to their reader. */
export async function requestLocale(): Promise<string> {
  const accept = (await headers()).get("accept-language") ?? "";
  const first = accept.split(",")[0]?.split(";")[0]?.trim();
  try {
    return first ? Intl.getCanonicalLocales(first)[0] ?? "en-IN" : "en-IN";
  } catch {
    return "en-IN";
  }
}
