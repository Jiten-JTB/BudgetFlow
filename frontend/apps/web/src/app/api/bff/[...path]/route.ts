import { readSession } from "@/server/auth/cookies";
import { authConfig } from "@/server/auth/config";
import { forwardToApi } from "@/server/bff";

type Context = { params: Promise<{ path: string[] }> };

async function handle(request: Request, { params }: Context) {
  return forwardToApi(request, (await params).path, await readSession(), authConfig());
}

export const GET = handle;
export const POST = handle;
export const PATCH = handle;
export const PUT = handle;
export const DELETE = handle;
