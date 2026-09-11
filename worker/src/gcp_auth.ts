/**
 * Google OAuth2 for FCM HTTP v1, Workers-native (no Firebase Admin SDK):
 *
 *   service account (client_email + private_key)
 *     -> RS256 JWT signed with WebCrypto
 *     -> oauth2.googleapis.com/token exchange (jwt-bearer grant)
 *     -> short-lived access token, cached in isolate memory
 *
 * Rules enforced here:
 *  - the JWT is NEVER used as the FCM Bearer token (always exchanged first)
 *  - cache refreshed 60s before expiry; cold isolates simply re-exchange
 *  - concurrent requests share one in-flight exchange
 *  - access tokens / private keys are never logged
 */

const TOKEN_URL = "https://oauth2.googleapis.com/token";
const FCM_SCOPE = "https://www.googleapis.com/auth/firebase.messaging";
const EXPIRY_SKEW_MS = 60_000;

interface TokenCache {
  token: string;
  expiresAt: number;
}

// Module scope = isolate-global memory. Lost on cold start, which is fine:
// a cold isolate just performs one extra OAuth exchange.
let cache: TokenCache | null = null;
let inflight: Promise<string> | null = null;

function base64UrlEncode(bytes: ArrayBuffer | Uint8Array): string {
  const view = bytes instanceof Uint8Array ? bytes : new Uint8Array(bytes);
  let binary = "";
  for (const byte of view) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function base64UrlEncodeJson(value: unknown): string {
  return base64UrlEncode(new TextEncoder().encode(JSON.stringify(value)));
}

function privateKeyPem(raw: string): string {
  if (raw.includes("-----BEGIN")) return raw;
  // base64-encoded PEM transport: decode, then treat as PEM.
  const decoded = atob(raw.replace(/\s+/g, ""));
  const bytes = new Uint8Array(decoded.length);
  for (let i = 0; i < decoded.length; i++) bytes[i] = decoded.charCodeAt(i);
  return new TextDecoder().decode(bytes);
}

function pemToPkcs8Bytes(pem: string): Uint8Array {
  const b64 = pem
    .replace("-----BEGIN PRIVATE KEY-----", "")
    .replace("-----END PRIVATE KEY-----", "")
    .replace(/\s+/g, "");
  const binary = atob(b64);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
  return bytes;
}

async function signJwt(clientEmail: string, privateKey: string): Promise<string> {
  const iat = Math.floor(Date.now() / 1000);
  const header = { alg: "RS256", typ: "JWT" };
  const claims = {
    iss: clientEmail,
    scope: FCM_SCOPE,
    aud: TOKEN_URL,
    iat,
    exp: iat + 3600,
  };
  const signingInput = `${base64UrlEncodeJson(header)}.${base64UrlEncodeJson(claims)}`;
  const key = await crypto.subtle.importKey(
    "pkcs8",
    pemToPkcs8Bytes(privateKey),
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const signature = await crypto.subtle.sign(
    "RSASSA-PKCS1-v1_5",
    key,
    new TextEncoder().encode(signingInput),
  );
  return `${signingInput}.${base64UrlEncode(signature)}`;
}

async function exchangeJwtForToken(jwt: string): Promise<{ token: string; expiresIn: number }> {
  const body = new URLSearchParams({
    grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
    assertion: jwt,
  });
  const response = await fetch(TOKEN_URL, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body,
  });
  if (!response.ok) {
    // Never include the JWT or token material in the error text.
    throw new Error(`oauth token exchange failed: HTTP ${response.status}`);
  }
  const json = (await response.json()) as { access_token?: string; expires_in?: number };
  if (!json.access_token) throw new Error("oauth response contained no access_token");
  return { token: json.access_token, expiresIn: json.expires_in ?? 3600 };
}

export interface AccessToken {
  token: string;
  source: "cache" | "fresh";
}

export async function getAccessToken(
  clientEmail: string,
  privateKeyRaw: string,
): Promise<AccessToken> {
  if (cache && Date.now() < cache.expiresAt - EXPIRY_SKEW_MS) {
    return { token: cache.token, source: "cache" };
  }
  if (!inflight) {
    inflight = (async () => {
      const jwt = await signJwt(clientEmail, privateKeyPem(privateKeyRaw));
      const { token, expiresIn } = await exchangeJwtForToken(jwt);
      cache = { token, expiresAt: Date.now() + expiresIn * 1000 };
      return token;
    })();
    inflight.finally(() => {
      inflight = null;
    });
  }
  return { token: await inflight, source: "fresh" };
}

export function resetTokenCacheForTests(): void {
  cache = null;
  inflight = null;
}
