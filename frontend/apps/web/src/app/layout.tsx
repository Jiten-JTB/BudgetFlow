import type { Metadata } from "next";
import { Mukta } from "next/font/google";
import "./globals.css";

// Self-hosted at build time by next/font; no request reaches Google at runtime.
const mukta = Mukta({ subsets: ["latin", "devanagari"], weight: ["400", "500", "600", "700"], variable: "--font-mukta" });

export const metadata: Metadata = {
  title: { default: "Budget Flow", template: "%s · Budget Flow" },
  description: "Track where your money comes from and where it goes.",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en" className={mukta.variable}>
      <body>{children}</body>
    </html>
  );
}
