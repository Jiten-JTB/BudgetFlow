/** The browser's locale for formatting in client components; en-IN during server rendering. */
export function navigatorLocale(): string {
  return typeof navigator === "undefined" ? "en-IN" : navigator.language || "en-IN";
}
