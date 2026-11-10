"use server";

import { revalidatePath } from "next/cache";
import { hideSetupForCurrentUser } from "@/server/setup-visibility";

/** "Hide these steps" on the overview. Next checks the Origin of every server action call. */
export async function hideSetup(): Promise<void> {
  await hideSetupForCurrentUser();
  revalidatePath("/");
}
