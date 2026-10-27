/**
 * Sukot Messenger Server v2.0.0 — نسخه Cloudflare Workers — سازنده: Sukot
 * =====================================================================
 * سرور تایید شماره موبایل با کد پیامکی (OTP) — بدون سرور مجازی و بدون هزینه (پلن رایگان کلادفلر)
 *
 * این نسخه ۱۰۰٪ سازگار با API نسخه Node (server/server.js) است؛
 * یعنی اپلیکیشن v2.0.0 هیچ تغییری لازم ندارد — فقط آدرس سرور را در اپ وارد کنید.
 *
 * ذخیره‌سازی: Cloudflare KV با binding به نام «KV»
 *
 * متغیرها و Secret ها:
 *   SECRET              (secret)  کلید HMAC توکن‌ها — توسط deploy.sh ساخته و ذخیره می‌شود
 *   SMS_PROVIDER        (var)     dev | kavenegar | smsir
 *   KAVENEGAR_API_KEY   (secret)  کلید کاوه‌نگار
 *   KAVENEGAR_SENDER    (var)     شماره خط کاوه‌نگار (اختیاری)
 *   SMSIR_API_KEY       (secret)  کلید SMS.ir
 *   SMSIR_TEMPLATE_ID   (var)     شناسه قالب OTP در SMS.ir
 *   SMSIR_PARAM         (var)     نام پارامتر قالب (پیش‌فرض CODE)
 *   BRAND               (var)     نام برند در متن پیام (پیش‌فرض: سوکوت)
 *   GLOBAL_DAILY_LIMIT  (var)     سقف کلی روزانه همه شماره‌ها (۰ = بی‌نهایت، پیش‌فرض ۳۰۰)
 *
 * محدودیت‌های ضد سوءاستفاده (مطابق نسخه Node):
 *   - حداقل فاصله بین دو ارسال: ۶۰ ثانیه
 *   - حداکثر ۱۰ کد در روز برای هر شماره
 *   - اعتبار هر کد: ۵ دقیقه
 *   - حداکثر ۵ تلاش اشتباه برای هر کد
 *   - سقف کلی روزانه (محافظ اعتبار درگاه پیامک)
 */

'use strict';

// ---------- تنظیمات ثابت ----------
const RESEND_GAP_MS = 60000;       // حداقل فاصله بین دو ارسال
const DAILY_LIMIT = 10;            // حداکثر کد روزانه برای هر شماره
const CODE_TTL_MS = 5 * 60000;     // اعتبار کد: ۵ دقیقه
const MAX_VERIFY_ATTEMPTS = 5;     // حداکثر تلاش اشتباه برای هر کد
const OTP_TTL_S = 90000;           // عمر رکورد OTP در KV: ۲۵ ساعت (برای شمارنده روزانه)

const otpKey = (p) => 'otp:' + p;
const userKey = (p) => 'user:' + p;
const gcapKey = (day) => 'gcap:' + day;

// ---------- ابزارها ----------
function normalizePhone(input) {
  if (typeof input !== 'string') return '';
  let d = input.trim()
    .replace(/[۰-۹]/g, (c) => String('۰۱۲۳۴۵۶۷۸۹'.indexOf(c)))
    .replace(/[٠-٩]/g, (c) => String('٠١٢٣٤٥٦٧٨٩'.indexOf(c)))
    .replace(/\D/g, '');
  if (d.startsWith('0098')) d = d.slice(4);
  else if (d.startsWith('98') && d.length >= 12) d = d.slice(2);
  if (d.startsWith('0') && d.length === 11) d = '98' + d.slice(1);
  else if (d.length === 10 && d.startsWith('9')) d = '98' + d;
  return /^989\d{9}$/.test(d) ? d : '';
}

function localPhone(p) { return p.startsWith('98') ? '0' + p.slice(2) : p; }

function genCode() {
  const a = new Uint32Array(1);
  crypto.getRandomValues(a);
  return String(100000 + (a[0] % 900000));
}

async function hmacSha256Hex(secret, msg) {
  const key = await crypto.subtle.importKey(
    'raw', new TextEncoder().encode(secret),
    { name: 'HMAC', hash: 'SHA-256' }, false, ['sign']
  );
  const sig = await crypto.subtle.sign('HMAC', key, new TextEncoder().encode(msg));
  return [...new Uint8Array(sig)].map((b) => b.toString(16).padStart(2, '0')).join('');
}

async function tokenFor(env, phone) {
  const exp = Date.now() + 90 * 24 * 3600 * 1000; // ۹۰ روز
  const body = phone + ':' + exp;
  const sig = await hmacSha256Hex(env.SECRET || 'sukot-dev-secret-change-me', body);
  return body + ':' + sig;
}

async function readBody(request) {
  try {
    const t = await request.text();
    if (t.length > 100000) return {};
    return JSON.parse(t || '{}');
  } catch (e) { return {}; }
}

function json(status, obj) {
  return new Response(JSON.stringify(obj), {
    status,
    headers: {
      'Content-Type': 'application/json; charset=utf-8',
      'Access-Control-Allow-Origin': '*',
      'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
      'Access-Control-Allow-Headers': 'Content-Type'
    }
  });
}

// ---------- درگاه‌های پیامک ----------
async function sendKavenegar(env, phone, code) {
  const msg = 'با سلام، کد تایید شما: ' + code + ' ' + (env.BRAND || 'سوکوت');
  let p = '/v1/' + env.KAVENEGAR_API_KEY + '/sms/send.json?receptor=' +
    encodeURIComponent(localPhone(phone)) + '&message=' + encodeURIComponent(msg);
  if (env.KAVENEGAR_SENDER) p += '&sender=' + encodeURIComponent(env.KAVENEGAR_SENDER);
  const r = await fetch('https://api.kavenegar.com' + p, {
    method: 'GET', signal: AbortSignal.timeout(15000)
  });
  const j = await r.json().catch(() => null);
  return !!(j && j.return && j.return.status === 200);
}

async function sendSmsIr(env, phone, code) {
  const r = await fetch('https://api.sms.ir/v1/send/verify', {
    method: 'POST',
    signal: AbortSignal.timeout(15000),
    headers: {
      'x-api-key': env.SMSIR_API_KEY,
      'Content-Type': 'application/json; charset=utf-8'
    },
    body: JSON.stringify({
      mobile: localPhone(phone),
      templateId: Number(env.SMSIR_TEMPLATE_ID),
      parameters: [{ name: env.SMSIR_PARAM || 'CODE', value: code }]
    })
  });
  const j = await r.json().catch(() => null);
  return !!(j && (j.status === 1 || j.status === '1' || j.successful));
}

async function sendCode(env, phone, code) {
  const provider = (env.SMS_PROVIDER || 'dev').toLowerCase();
  if (provider === 'kavenegar') {
    if (!env.KAVENEGAR_API_KEY) return { sent: false, error: 'کلید KAVENEGAR_API_KEY روی سرور تنظیم نشده است' };
    try { return (await sendKavenegar(env, phone, code)) ? { sent: true } : { sent: false, error: 'ارسال پیامک از طریق کاوه‌نگار ناموفق بود' }; }
    catch (e) { return { sent: false, error: 'ارسال پیامک از طریق کاوه‌نگار ناموفق بود' }; }
  }
  if (provider === 'smsir') {
    if (!env.SMSIR_API_KEY || !env.SMSIR_TEMPLATE_ID) return { sent: false, error: 'SMSIR_API_KEY یا SMSIR_TEMPLATE_ID تنظیم نشده است' };
    try { return (await sendSmsIr(env, phone, code)) ? { sent: true } : { sent: false, error: 'ارسال پیامک از طریق SMS.ir ناموفق بود' }; }
    catch (e) { return { sent: false, error: 'ارسال پیامک از طریق SMS.ir ناموفق بود' }; }
  }
  // حالت dev: کد در پاسخ API برگردانده می‌شود (فقط برای تست)
  return { sent: false, dev: true };
}

async function bumpGlobalCap(env, today) {
  try {
    const used = parseInt((await env.KV.get(gcapKey(today))) || '0', 10) + 1;
    await env.KV.put(gcapKey(today), String(used), { expirationTtl: 172800 });
  } catch (e) { /* شمارنده حیاتی نیست */ }
}

async function loadOtp(env, phone) {
  try { return JSON.parse((await env.KV.get(otpKey(phone))) || '{}'); }
  catch (e) { return {}; }
}

// ---------- ورکر ----------
export default {
  async fetch(request, env) {
    let u;
    try { u = new URL(request.url); } catch (e) { return json(400, { ok: false, error: 'bad request' }); }
    const p = u.pathname;

    if (request.method === 'OPTIONS') return new Response(null, { status: 204 });

    if (p === '/' && request.method === 'GET') {
      const provider = env.SMS_PROVIDER || 'dev';
      return json(200, {
        name: 'Sukot Messenger Server',
        version: '2.0.0',
        maker: 'Sukot',
        runtime: 'cloudflare-workers',
        provider: provider,
        dev_mode: provider === 'dev'
      });
    }

    // ---- درخواست کد ----
    if (p === '/api/otp/request' && request.method === 'POST') {
      const body = await readBody(request);
      const phone = normalizePhone(body.phone || '');
      if (!phone) return json(400, { ok: false, error: 'شماره موبایل معتبر نیست (مثال: 09123456789)' });

      const now = Date.now();
      const today = Math.floor(now / 86400000);

      // سقف کلی روزانه (محافظ اعتبار درگاه پیامک)
      const gMax = parseInt(env.GLOBAL_DAILY_LIMIT || '300', 10);
      if (gMax > 0) {
        const gUsed = parseInt((await env.KV.get(gcapKey(today))) || '0', 10);
        if (gUsed >= gMax) return json(429, { ok: false, error: 'سرور شلوغ است؛ کمی بعد دوباره تلاش کنید' });
      }

      let o = await loadOtp(env, phone);
      if (!o || o.day !== today) o = { day: today, dayCount: 0 };

      if (o.lastSent && now - o.lastSent < RESEND_GAP_MS) {
        return json(429, { ok: false, error: 'درخواست زیاد؛ یک دقیقه دیگر دوباره تلاش کنید' });
      }
      if (o.dayCount >= DAILY_LIMIT) {
        return json(429, { ok: false, error: 'سقف روزانه دریافت کد برای این شماره پر شده است' });
      }

      const code = genCode();
      o.code = code;
      o.expires = now + CODE_TTL_MS;
      o.attempts = 0;
      o.lastSent = now;
      o.dayCount = (o.dayCount || 0) + 1;
      await env.KV.put(otpKey(phone), JSON.stringify(o), { expirationTtl: OTP_TTL_S });

      const r = await sendCode(env, phone, code);
      if (r.sent) { await bumpGlobalCap(env, today); return json(200, { ok: true }); }
      if (r.dev) { await bumpGlobalCap(env, today); return json(200, { ok: true, dev_code: code, note: 'حالت آزمایشی سرور: پیامکی ارسال نمی‌شود' }); }
      return json(502, { ok: false, error: r.error || 'ارسال پیامک ناموفق بود' });
    }

    // ---- تایید کد ----
    if (p === '/api/otp/verify' && request.method === 'POST') {
      const body = await readBody(request);
      const phone = normalizePhone(body.phone || '');
      const code = String(body.code || '').replace(/\D/g, '');
      if (!phone) return json(400, { ok: false, error: 'شماره موبایل معتبر نیست' });

      const o = await loadOtp(env, phone);
      if (!o || !o.code) return json(400, { ok: false, error: 'ابتدا کد تایید درخواست کنید' });
      if (Date.now() > o.expires) return json(400, { ok: false, error: 'کد منقضی شده؛ کد جدید درخواست کنید' });
      if ((o.attempts || 0) >= MAX_VERIFY_ATTEMPTS) {
        return json(429, { ok: false, error: 'تلاش‌های بیش از حد؛ کد جدید درخواست کنید' });
      }
      if (o.code !== code) {
        o.attempts = (o.attempts || 0) + 1;
        await env.KV.put(otpKey(phone), JSON.stringify(o), { expirationTtl: OTP_TTL_S });
        return json(400, { ok: false, error: 'کد وارد شده اشتباه است' });
      }

      await env.KV.put(userKey(phone), JSON.stringify({ at: Date.now() }));
      await env.KV.delete(otpKey(phone));
      return json(200, { ok: true, token: await tokenFor(env, phone), phone: phone });
    }

    // ---- بررسی عضویت شماره ----
    if (p === '/api/users/exists' && request.method === 'GET') {
      const phone = normalizePhone(u.searchParams.get('phone') || '');
      let registered = false;
      try { registered = !!(await env.KV.get(userKey(phone))); } catch (e) { registered = false; }
      return json(200, { ok: true, registered: registered });
    }

    return json(404, { ok: false, error: 'not found' });
  }
};
