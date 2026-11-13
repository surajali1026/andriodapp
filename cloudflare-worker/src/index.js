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
  if (!expected) return false;
  return request.headers.get("authorization") === `Bearer ${expected}`;
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

    if (!env.ALERTS_DB) {
      return json({ ok: false, error: "D1 database is not connected yet." }, 503);
    }

    if (url.pathname === "/api/alerts" && request.method === "GET") {
      const { results } = await env.ALERTS_DB
        .prepare("SELECT id, symbol, condition, target_price, price_type, status, created_at, triggered_at FROM alerts ORDER BY created_at DESC")
        .all();
      return json({ ok: true, alerts: results });
    }

    if (url.pathname === "/api/alerts" && request.method === "POST") {
      let body;
      try { body = await request.json(); } catch { return json({ ok: false, error: "Invalid JSON body." }, 400); }
      const symbol = String(body.symbol || "").toUpperCase().replace(/[^A-Z0-9]/g, "");
      const condition = String(body.condition || "");
      const targetPrice = Number(body.targetPrice);
      const priceType = String(body.priceType || "LAST").toUpperCase();
      const allowedConditions = ["REACHES", "CROSSES_ABOVE", "CROSSES_BELOW", "CANDLE_CLOSES_ABOVE", "CANDLE_CLOSES_BELOW"];
      if (!symbol || !Number.isFinite(targetPrice) || targetPrice <= 0 ||
          !allowedConditions.includes(condition) || !["LAST", "MARK", "INDEX"].includes(priceType)) {
        return json({ ok: false, error: "Provide symbol, positive targetPrice, valid condition and priceType." }, 400);
      }
      const id = crypto.randomUUID();
      await env.ALERTS_DB.prepare(
        "INSERT INTO alerts (id, symbol, condition, target_price, price_type, status, created_at) VALUES (?, ?, ?, ?, ?, 'ACTIVE', ?)"
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
    // The minute scheduler is registered. Price monitoring is enabled after
    // the D1 database and push delivery credentials are configured.
    if (!env.ALERTS_DB) {
      console.log("Alert monitor waiting for D1 database configuration.");
      return;
    }
    ctx.waitUntil(runAlertMonitor(env));
  }
};

async function runAlertMonitor(env) {
  // Intentionally safe until push delivery is configured. The next setup
  // step adds Binance monitoring and Firebase Cloud Messaging delivery.
  console.log("Alert monitor invoked; push delivery setup is still required.");
}
