/** Income source types as the API names them, with their labels. Plain data: importable from Server and Client Components alike. */
export const SOURCE_TYPES = [
  ["SALARY", "Salary"], ["BUSINESS", "Business"], ["FREELANCE", "Freelance"], ["INVESTMENT", "Investments"],
  ["RENTAL", "Rental"], ["PENSION", "Pension"], ["GIFT", "Gifts"], ["OTHER", "Other"],
] as const;
