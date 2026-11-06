import { describe, expect, it } from "vitest";
import { type Session, seal, sessionDecision, unseal } from "./session";

const SECRET = "x".repeat(40);
const session: Session = {
  accessToken: "access-token-value", refreshToken: "refresh-token-plaintext-must-not-leak", idToken: "id-token-value", userId: "u-1", expiresAt: 1_000_000,
};

describe("sealed cookies", () => {
  it("round-trips and is opaque", async () => {
    const sealed = await seal(session, SECRET, 3600);
    expect(sealed).not.toContain("refresh-token-plaintext");
    expect(sealed).not.toContain(Buffer.from("refresh-token-plaintext").toString("base64url").slice(0, 16));
    expect(await unseal<Session>(sealed, SECRET)).toEqual(session);
  });

  it("rejects tampering, the wrong secret and garbage", async () => {
    const sealed = await seal(session, SECRET, 3600);
    const tampered = sealed.slice(0, -4) + (sealed.endsWith("AAAA") ? "BBBB" : "AAAA");
    expect(await unseal(tampered, SECRET)).toBeNull();
    expect(await unseal(sealed, "y".repeat(40))).toBeNull();
    expect(await unseal("not-a-jwe", SECRET)).toBeNull();
    expect(await unseal(undefined, SECRET)).toBeNull();
  });

  it("expires with its max age", async () => {
    const sealed = await seal(session, SECRET, -10);
    expect(await unseal(sealed, SECRET)).toBeNull();
  });

  it("refuses weak secrets", async () => {
    await expect(seal(session, "short", 60)).rejects.toThrow(/32/);
  });
});

describe("sessionDecision", () => {
  it("keeps, refreshes shortly before expiry, or asks to sign in", () => {
    expect(sessionDecision(null, 0)).toBe("none");
    expect(sessionDecision(session, session.expiresAt - 120_000)).toBe("valid");
    expect(sessionDecision(session, session.expiresAt - 30_000)).toBe("refresh");
    expect(sessionDecision(session, session.expiresAt + 1)).toBe("refresh");
  });
});
