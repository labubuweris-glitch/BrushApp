const PROJECT_ID = "brushapp-b1aef";
const SERVICE_ACCOUNT_SECRET = "FIREBASE_SERVICE_ACCOUNT";

function b64url(bytes) {
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/=/g, "").replace(/\+/g, "-").replace(/\//g, "_");
}
function decodeBase64Url(value) {
  const padded = value.replace(/-/g, "+").replace(/_/g, "/") + "=".repeat((4 - value.length % 4) % 4);
  return Uint8Array.from(atob(padded), c => c.charCodeAt(0));
}
function decodeJwtPart(value) {
  return JSON.parse(new TextDecoder().decode(decodeBase64Url(value)));
}
async function verifyFirebaseUser(idToken) {
  const parts = idToken.split(".");
  if (parts.length !== 3) throw new Error("Token Firebase tidak valid.");
  const header = decodeJwtPart(parts[0]);
  const payload = decodeJwtPart(parts[1]);
  if (header.alg !== "RS256" || !header.kid) throw new Error("Header token tidak valid.");
  const now = Math.floor(Date.now() / 1000);
  if (payload.exp <= now || payload.iat > now + 60 ||
      payload.aud !== PROJECT_ID ||
      payload.iss !== "https://securetoken.google.com/" + PROJECT_ID ||
      typeof payload.sub !== "string" || payload.sub.length === 0) {
    throw new Error("Token Firebase tidak sah atau sudah kedaluwarsa.");
  }
  const keysResponse = await fetch("https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com");
  if (!keysResponse.ok) throw new Error("Gagal mengambil kunci verifikasi Firebase.");
  const keys = await keysResponse.json();
  const jwk = keys.keys.find(k => k.kid === header.kid);
  if (!jwk) throw new Error("Kunci Firebase tidak ditemukan.");
  const publicKey = await crypto.subtle.importKey("jwk", jwk, { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["verify"]);
  const valid = await crypto.subtle.verify("RSASSA-PKCS1-v1_5", publicKey, decodeBase64Url(parts[2]), new TextEncoder().encode(parts[0] + "." + parts[1]));
  if (!valid) throw new Error("Tanda tangan token Firebase tidak valid.");
  return payload.sub;
}
async function createGoogleAccessToken(serviceAccount) {
  const now = Math.floor(Date.now() / 1000);
  const pem = serviceAccount.private_key.replace(/-----BEGIN PRIVATE KEY-----/g, "").replace(/-----END PRIVATE KEY-----/g, "").replace(/\s/g, "");
  const der = Uint8Array.from(atob(pem), c => c.charCodeAt(0));
  const privateKey = await crypto.subtle.importKey("pkcs8", der, { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["sign"]);
  const header = b64url(new TextEncoder().encode(JSON.stringify({ alg: "RS256", typ: "JWT" })));
  const claim = b64url(new TextEncoder().encode(JSON.stringify({
    iss: serviceAccount.client_email,
    scope: "https://www.googleapis.com/auth/firebase.messaging https://www.googleapis.com/auth/datastore",
    aud: "https://oauth2.googleapis.com/token",
    iat: now, exp: now + 3600
  })));
  const unsigned = header + "." + claim;
  const signature = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", privateKey, new TextEncoder().encode(unsigned));
  const jwt = unsigned + "." + b64url(new Uint8Array(signature));
  const response = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({ grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer", assertion: jwt })
  });
  const data = await response.json();
  if (!response.ok || !data.access_token) throw new Error("Gagal memperoleh token Google.");
  return data.access_token;
}
async function firestoreGet(path, accessToken) {
  const url = "https://firestore.googleapis.com/v1/projects/" + PROJECT_ID + "/databases/(default)/documents/" + path;
  const response = await fetch(url, { headers: { Authorization: "Bearer " + accessToken } });
  if (response.status === 404) return null;
  if (!response.ok) throw new Error("Gagal membaca data Firestore.");
  return response.json();
}
async function sendNotifications(env, request) {
  const body = await request.json();
  if (!body.idToken || !body.conversationId || !body.messageId) throw new Error("Data permintaan belum lengkap.");
  const senderUid = await verifyFirebaseUser(body.idToken);
  const serviceAccount = JSON.parse(env[SERVICE_ACCOUNT_SECRET]);
  const accessToken = await createGoogleAccessToken(serviceAccount);
  const conversationId = body.conversationId;
  const messageId = body.messageId;
  if (!/^[a-f0-9]{64}$/.test(conversationId) || !/^[A-Za-z0-9_-]{1,150}$/.test(messageId)) throw new Error("ID percakapan atau pesan tidak valid.");
  const conversation = await firestoreGet("conversations/" + conversationId, accessToken);
  if (!conversation) throw new Error("Percakapan tidak ditemukan.");
  const members = conversation.fields?.members?.arrayValue?.values?.map(v => v.stringValue) || [];
  if (members.length !== 2 || !members.includes(senderUid)) throw new Error("Pengirim bukan anggota percakapan.");
  const message = await firestoreGet("conversations/" + conversationId + "/messages/" + messageId, accessToken);
  if (!message || message.fields?.senderUid?.stringValue !== senderUid) throw new Error("Pesan tidak cocok dengan pengirim.");
  const recipientUid = members.find(uid => uid !== senderUid);
  const messageText = message.fields?.text?.stringValue || "";
  const senderName = conversation.fields?.memberInfo?.mapValue?.fields?.[senderUid]?.mapValue?.fields?.displayName?.stringValue || "BrushApp";
  const tokenUrl = "https://firestore.googleapis.com/v1/projects/" + PROJECT_ID + "/databases/(default)/documents/users/" + recipientUid + "/fcm_tokens";
  const tokenResponse = await fetch(tokenUrl, { headers: { Authorization: "Bearer " + accessToken } });
  if (!tokenResponse.ok) throw new Error("Gagal mengambil token penerima.");
  const tokenData = await tokenResponse.json();
  const tokens = (tokenData.documents || []).map(doc => doc.fields?.token?.stringValue).filter(Boolean);
  let sent = 0;
  for (const token of tokens) {
    const fcmResponse = await fetch("https://fcm.googleapis.com/v1/projects/" + PROJECT_ID + "/messages:send", {
      method: "POST",
      headers: { Authorization: "Bearer " + accessToken, "Content-Type": "application/json" },
      body: JSON.stringify({
        message: {
          token,
          data: { title: senderName, body: messageText.length > 180 ? messageText.slice(0, 177) + "..." : messageText },
          android: { priority: "HIGH" }
        }
      })
    });
    if (fcmResponse.ok) sent++;
  }
  return { sent, total: tokens.length };
}
export default {
  async fetch(request, env) {
    if (request.method === "OPTIONS") return new Response(null, { status: 204, headers: { "Access-Control-Allow-Origin": "*", "Access-Control-Allow-Headers": "Content-Type, Authorization", "Access-Control-Allow-Methods": "POST, OPTIONS" } });
    if (request.method !== "POST") return new Response("BrushApp notification worker", { status: 200 });
    try {
      const result = await sendNotifications(env, request);
      return new Response(JSON.stringify(result), { status: 200, headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": "*" } });
    } catch (error) {
      return new Response(JSON.stringify({ error: error.message || "Request gagal." }), { status: 400, headers: { "Content-Type": "application/json", "Access-Control-Allow-Origin": "*" } });
    }
  }
};
