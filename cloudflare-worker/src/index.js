const json = (data, status = 200) =>
  new Response(JSON.stringify(data), {
    status,
    headers: {
      "content-type": "application/json; charset=utf-8",
      "access-control-allow-origin": "*",
      "access-control-allow-methods": "GET,POST,DELETE,OPTIONS",
      "access-control-allow-headers": "content-type,authorization"
    }
  });

function authorized(request, env) {
  const expected = env.ALERT_API_KEY;
  return Boolean(expected) && request.headers.get("authorization") === `Bearer ${expected}`;
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (request.method === "OPTIONS") return new Response(null, { status: 204 });

    if (url.pathname === "/api/device/register" && request.method === "POST") {
      if (!env.ALERTS_DB) return json({ ok: false, error: "D1 database is not connected." }, 503);
      let body;
      try { body = await request.json(); } catch { return json({ ok: false, error: "Invalid JSON body." }, 400); }
      const token = String(body.token || "");
      const platform = String(body.platform || "android").slice(0, 20);
      if (token.length < 40 || token.length > 4096) return json({ ok: false, error: "Invalid FCM token." }, 400);
      await env.ALERTS_DB.prepare(
        "INSERT INTO devices (id, fcm_token, platform, updated_at) VALUES ('primary', ?, ?, ?) ON CONFLICT(id) DO UPDATE SET fcm_token = excluded.fcm_token, platform = excluded.platform, updated_at = excluded.updated_at"
      ).bind(token, platform, new Date().toISOString()).run();
      return json({ ok: true, registered: true });
    }

    if (url.pathname === "/health" || url.pathname === "/") {
      return json({
        ok: true,
        service: "suraj-trading-alerts",
        databaseConfigured: Boolean(env.ALERTS_DB),
        pushConfigured: Boolean(env.FCM_SERVICE_ACCOUNT_JSON)
      });
    }

    if (!authorized(request, env)) {
      return json({ ok: false, error: "Unauthorized. Configure ALERT_API_KEY and send it as a Bearer token." }, 401);
    }
    if (!env.ALERTS_DB) return json({ ok: false, error: "D1 database is not connected yet." }, 503);

    if (url.pathname === "/api/alerts" && request.method === "GET") {
      const { results } = await env.ALERTS_DB.prepare(
        "SELECT id, symbol, condition, target_price, price_type, status, created_at, triggered_at FROM alerts ORDER BY created_at DESC"
      ).all();
      return json({ ok: true, alerts: results });
    }

    if (url.pathname === "/api/alerts" && request.method === "POST") {
      let body;
      try { body = await request.json(); } catch { return json({ ok: false, error: "Invalid JSON body." }, 400); }
      const symbol = String(body.symbol || "").toUpperCase().replace(/[^A-Z0-9]/g, "");
      const condition = String(body.condition || "");
      const targetPrice = Number(body.targetPrice);
      const priceType = String(body.priceType || "LAST").toUpperCase();
      const allowedConditions = ["CROSSES_ABOVE", "CROSSES_BELOW", "CANDLE_CLOSES_ABOVE", "CANDLE_CLOSES_BELOW"];
      if (!/^[A-Z0-9]{3,30}$/.test(symbol) || !Number.isFinite(targetPrice) || targetPrice <= 0 ||
          !allowedConditions.includes(condition) || !["LAST", "MARK", "INDEX"].includes(priceType)) {
        return json({ ok: false, error: "Provide symbol, positive targetPrice, valid condition and priceType." }, 400);
      }
      const id = crypto.randomUUID();
      await env.ALERTS_DB.prepare(
        "INSERT INTO alerts (id, symbol, condition, target_price, price_type, status, created_at, last_price) VALUES (?, ?, ?, ?, ?, 'ACTIVE', ?, NULL)"
      ).bind(id, symbol, condition, targetPrice, priceType, new Date().toISOString()).run();
      return json({ ok: true, id });
    }

    const deleteMatch = url.pathname.match(/^\/api\/alerts\/([a-f0-9-]+)$/i);
    if (deleteMatch && request.method === "DELETE") {
      await env.ALERTS_DB.prepare("DELETE FROM alerts WHERE id = ?").bind(deleteMatch[1]).run();
      return json({ ok: true });
    }
    return json({ ok: false, error: "Not found." }, 404);
  },

  async scheduled(_event, env, ctx) {
    if (!env.ALERTS_DB) return;
    ctx.waitUntil(runAlertMonitor(env));
  }
};

function base64url(value) {
  const bytes = new TextEncoder().encode(value);
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/=/g, "").replace(/\+/g, "-").replace(/\//g, "_");
}

function pemToArrayBuffer(pem) {
  const base64 = pem.replace(/-----BEGIN PRIVATE KEY-----|-----END PRIVATE KEY-----|\s/g, "");
  const binary = atob(base64);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
  return bytes.buffer;
}

async function getFcmAccessToken(serviceAccount) {
  const now = Math.floor(Date.now() / 1000);
  const header = base64url(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const claims = base64url(JSON.stringify({
    iss: serviceAccount.client_email,
    scope: "https://www.googleapis.com/auth/firebase.messaging",
    aud: "https://oauth2.googleapis.com/token",
    iat: now,
    exp: now + 3600
  }));
  const unsigned = header + "." + claims;
  const key = await crypto.subtle.importKey(
    "pkcs8", pemToArrayBuffer(serviceAccount.private_key),
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["sign"]
  );
  const signature = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, new TextEncoder().encode(unsigned));
  const bytes = new Uint8Array(signature);
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  const assertion = unsigned + "." + btoa(binary).replace(/=/g, "").replace(/\+/g, "-").replace(/\//g, "_");
  const response = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion
    })
  });
  const result = await response.json();
  if (!response.ok || !result.access_token) throw new Error("Google OAuth failed: " + JSON.stringify(result));
  return result.access_token;
}

async function sendPush(env, token, title, body) {
  const account = JSON.parse(env.FCM_SERVICE_ACCOUNT_JSON);
  const accessToken = await getFcmAccessToken(account);
  const response = await fetch(
    `https://fcm.googleapis.com/v1/projects/${account.project_id}/messages:send`,
    {
      method: "POST",
      headers: { authorization: `Bearer ${accessToken}`, "content-type": "application/json" },
      body: JSON.stringify({
        message: {
          token,
          notification: { title, body },
          data: { title, body },
          android: { priority: "HIGH", notification: { channel_id: "trading_price_alerts", sound: "default" } }
        }
      })
    }
  );
  const result = await response.json();
  if (!response.ok) throw new Error("FCM send failed: " + JSON.stringify(result));
  return result;
}

async function getMarketPrice(symbol, priceType) {
  if (priceType === "LAST") {
    const response = await fetch(`https://fapi.binance.com/fapi/v1/ticker/price?symbol=${encodeURIComponent(symbol)}`);
    if (!response.ok) throw new Error("Binance last-price request failed");
    return Number((await response.json()).price);
  }
  const response = await fetch(`https://fapi.binance.com/fapi/v1/premiumIndex?symbol=${encodeURIComponent(symbol)}`);
  if (!response.ok) throw new Error("Binance mark/index request failed");
  const data = await response.json();
  return Number(priceType === "MARK" ? data.markPrice : data.indexPrice);
}

async function runAlertMonitor(env) {
  if (!env.FCM_SERVICE_ACCOUNT_JSON) {
    console.log("FCM_SERVICE_ACCOUNT_JSON is missing; skipping push monitor.");
    return;
  }
  const { results: alerts } = await env.ALERTS_DB.prepare(
    "SELECT id, symbol, condition, target_price, price_type, last_price FROM alerts WHERE status = 'ACTIVE' LIMIT 100"
  ).all();
  if (!alerts.length) return;
  const { results: devices } = await env.ALERTS_DB.prepare("SELECT fcm_token FROM devices").all();
  if (!devices.length) {
    console.log("No registered FCM devices yet.");
    return;
  }

  const cache = new Map();
  for (const alert of alerts) {
    try {
      const key = alert.symbol + ":" + alert.price_type;
      if (!cache.has(key)) cache.set(key, await getMarketPrice(alert.symbol, alert.price_type));
      const price = cache.get(key);
      if (!Number.isFinite(price) || price <= 0) continue;

      if (alert.condition.startsWith("CANDLE_CLOSES_")) {
        const k = await fetch(`https://fapi.binance.com/fapi/v1/klines?symbol=${encodeURIComponent(alert.symbol)}&interval=1m&limit=2`);
        if (!k.ok) continue;
        const candles = await k.json();
        const closedPrice = Number(candles[candles.length - 2]?.[4]);
        const hit = alert.condition === "CANDLE_CLOSES_ABOVE"
          ? closedPrice >= alert.target_price : closedPrice <= alert.target_price;
        if (hit) await triggerAlert(env, alert, devices, closedPrice, "1m candle close");
        continue;
      }

      if (alert.last_price == null) {
        await env.ALERTS_DB.prepare("UPDATE alerts SET last_price = ? WHERE id = ?").bind(price, alert.id).run();
        continue;
      }
      const hit = alert.condition === "CROSSES_ABOVE"
        ? alert.last_price < alert.target_price && price >= alert.target_price
        : alert.last_price > alert.target_price && price <= alert.target_price;
      await env.ALERTS_DB.prepare("UPDATE alerts SET last_price = ? WHERE id = ?").bind(price, alert.id).run();
      if (hit) await triggerAlert(env, alert, devices, price, alert.price_type + " price");
    } catch (error) {
      console.error("Alert monitor error for", alert.symbol, alert.id, String(error));
    }
  }
}

async function triggerAlert(env, alert, devices, price, label) {
  const changed = await env.ALERTS_DB.prepare(
    "UPDATE alerts SET status = 'TRIGGERED', triggered_at = ? WHERE id = ? AND status = 'ACTIVE'"
  ).bind(new Date().toISOString(), alert.id).run();
  if (!changed.meta?.changes) return;
  const title = alert.symbol + " price alert";
  const body = `${label} crossed ${alert.condition === "CROSSES_ABOVE" || alert.condition === "CANDLE_CLOSES_ABOVE" ? "above" : "below"} ${alert.target_price}. Current: ${price}`;
  for (const device of devices) {
    try { await sendPush(env, device.fcm_token, title, body); }
    catch (error) { console.error("Push delivery failed:", String(error)); }
  }
}
