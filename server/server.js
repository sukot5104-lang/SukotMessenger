#!/usr/bin/env node
'use strict';
/*
 * Sukot Messenger Server v2.0.0 — سازنده: Sukot
 * سرور تایید شماره موبایل با کد پیامکی (OTP)
 * بدون هیچ وابستگی — فقط Node.js 14+
 * اجرا:  node server.js
 */

const http = require('http');
const https = require('https');
const crypto = require('crypto');
const fs = require('fs');
const path = require('path');

// ---------- تنظیمات (متغیرهای محیطی) ----------
const PORT = process.env.PORT || 3000;
const SECRET = process.env.SECRET || 'sukot-dev-secret-change-me';
const PROVIDER = (process.env.SMS_PROVIDER || 'dev').toLowerCase(); // dev | kavenegar | smsir
const KAVENEGAR_API_KEY = process.env.KAVENEGAR_API_KEY || '';
const KAVENEGAR_SENDER = process.env.KAVENEGAR_SENDER || '';
const SMSIR_API_KEY = process.env.SMSIR_API_KEY || '';
const SMSIR_TEMPLATE_ID = process.env.SMSIR_TEMPLATE_ID || '';
const SMSIR_PARAM = process.env.SMSIR_PARAM || 'CODE';
const BRAND = process.env.BRAND || 'سوکوت';

// محدودیت‌های ضد سوءاستفاده (ثابت و ایمن)
const RESEND_GAP_MS = 60000;        // حداقل فاصله بین دو ارسال: ۶۰ ثانیه
const DAILY_LIMIT = 10;             // حداکثر کد روزانه برای هر شماره
const CODE_TTL_MS = 5 * 60000;      // اعتبار کد: ۵ دقیقه
const MAX_VERIFY_ATTEMPTS = 5;      // حداکثر تلاش اشتباه برای هر کد

const DATA_FILE = path.join(__dirname, 'data.json');
let users = {};
try { users = JSON.parse(fs.readFileSync(DATA_FILE, 'utf8')); } catch (e) { /* first run */ }
function saveUsers() {
  try { fs.writeFileSync(DATA_FILE, JSON.stringify(users, null, 2)); } catch (e) { /* ignore */ }
}

// phone -> { day, dayCount, code, expires, attempts, lastSent }
const otps = new Map();

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

function genCode() { return String(crypto.randomInt(100000, 1000000)); }

function tokenFor(phone) {
  const exp = Date.now() + 90 * 24 * 3600 * 1000;
  const body = phone + ':' + exp;
  const sig = crypto.createHmac('sha256', SECRET).update(body).digest('hex');
  return body + ':' + sig;
}

// ---------- درگاه‌های پیامک ----------
function sendKavenegar(phone, code) {
  const msg = 'با سلام، کد تایید شما: ' + code + ' ' + BRAND;
  let p = '/v1/' + KAVENEGAR_API_KEY + '/sms/send.json?receptor=' + encodeURIComponent(localPhone(phone)) +
    '&message=' + encodeURIComponent(msg);
  if (KAVENEGAR_SENDER) p += '&sender=' + encodeURIComponent(KAVENEGAR_SENDER);
  return new Promise((resolve) => {
    const req = https.get({ host: 'api.kavenegar.com', path: p, timeout: 15000 }, (res) => {
      let b = '';
      res.on('data', (c) => { b += c; });
      res.on('end', () => {
        try { const j = JSON.parse(b); resolve(!!(j && j.return && j.return.status === 200)); }
        catch (e) { resolve(false); }
      });
    });
    req.on('error', () => resolve(false));
    req.on('timeout', () => { req.destroy(); resolve(false); });
  });
}

function sendSmsIr(phone, code) {
  const payload = JSON.stringify({
    mobile: localPhone(phone),
    templateId: Number(SMSIR_TEMPLATE_ID),
    parameters: [{ name: SMSIR_PARAM, value: code }]
  });
  return new Promise((resolve) => {
    const req = https.request({
      host: 'api.sms.ir', path: '/v1/send/verify', method: 'POST', timeout: 15000,
      headers: {
        'x-api-key': SMSIR_API_KEY,
        'Content-Type': 'application/json',
        'Content-Length': Buffer.byteLength(payload)
      }
    }, (res) => {
      let b = '';
      res.on('data', (c) => { b += c; });
      res.on('end', () => {
        try { const j = JSON.parse(b); resolve(!!(j && (j.status === 1 || j.status === '1' || j.successful))); }
        catch (e) { resolve(false); }
      });
    });
    req.on('error', () => resolve(false));
    req.on('timeout', () => { req.destroy(); resolve(false); });
    req.write(payload);
    req.end();
  });
}

async function sendCode(phone, code) {
  if (PROVIDER === 'kavenegar') {
    if (!KAVENEGAR_API_KEY) return { sent: false, error: 'کلید KAVENEGAR_API_KEY روی سرور تنظیم نشده است' };
    const ok = await sendKavenegar(phone, code);
    return ok ? { sent: true } : { sent: false, error: 'ارسال پیامک از طریق کاوه‌نگار ناموفق بود' };
  }
  if (PROVIDER === 'smsir') {
    if (!SMSIR_API_KEY || !SMSIR_TEMPLATE_ID) return { sent: false, error: 'SMSIR_API_KEY یا SMSIR_TEMPLATE_ID تنظیم نشده است' };
    const ok = await sendSmsIr(phone, code);
    return ok ? { sent: true } : { sent: false, error: 'ارسال پیامک از طریق SMS.ir ناموفق بود' };
  }
  // حالت dev: کد در پاسخ API برگردانده می‌شود (فقط برای تست)
  return { sent: false, dev: true };
}

// ---------- HTTP ----------
function json(res, code, obj) {
  const b = JSON.stringify(obj);
  res.writeHead(code, { 'Content-Type': 'application/json; charset=utf-8', 'Content-Length': Buffer.byteLength(b) });
  res.end(b);
}

function readBody(req) {
  return new Promise((resolve) => {
    let b = '';
    req.on('data', (c) => { b += c; if (b.length > 100000) req.destroy(); });
    req.on('end', () => { try { resolve(JSON.parse(b || '{}')); } catch (e) { resolve({}); } });
    req.on('error', () => resolve({}));
  });
}

const server = http.createServer(async (req, res) => {
  let u;
  try { u = new URL(req.url, 'http://localhost'); } catch (e) { return json(res, 400, { ok: false, error: 'bad request' }); }
  const p = u.pathname;

  if (p === '/' && req.method === 'GET') {
    return json(res, 200, {
      name: 'Sukot Messenger Server',
      version: '2.0.0',
      maker: 'Sukot',
      provider: PROVIDER,
      dev_mode: PROVIDER === 'dev'
    });
  }

  // ---- درخواست کد ----
  if (p === '/api/otp/request' && req.method === 'POST') {
    const body = await readBody(req);
    const phone = normalizePhone(body.phone || '');
    if (!phone) return json(res, 400, { ok: false, error: 'شماره موبایل معتبر نیست (مثال: 09123456789)' });

    const now = Date.now();
    const today = Math.floor(now / 86400000);
    let o = otps.get(phone);
    if (!o || o.day !== today) o = { day: today, dayCount: 0 };

    if (o.lastSent && now - o.lastSent < RESEND_GAP_MS) {
      return json(res, 429, { ok: false, error: 'درخواست زیاد؛ یک دقیقه دیگر دوباره تلاش کنید' });
    }
    if (o.dayCount >= DAILY_LIMIT) {
      return json(res, 429, { ok: false, error: 'سقف روزانه دریافت کد برای این شماره پر شده است' });
    }

    const code = genCode();
    o.code = code;
    o.expires = now + CODE_TTL_MS;
    o.attempts = 0;
    o.lastSent = now;
    o.dayCount++;
    otps.set(phone, o);

    const r = await sendCode(phone, code);
    if (r.sent) return json(res, 200, { ok: true });
    if (r.dev) return json(res, 200, { ok: true, dev_code: code, note: 'حالت آزمایشی سرور: پیامکی ارسال نمی‌شود' });
    return json(res, 502, { ok: false, error: r.error || 'ارسال پیامک ناموفق بود' });
  }

  // ---- تایید کد ----
  if (p === '/api/otp/verify' && req.method === 'POST') {
    const body = await readBody(req);
    const phone = normalizePhone(body.phone || '');
    const code = String(body.code || '').replace(/\D/g, '');
    if (!phone) return json(res, 400, { ok: false, error: 'شماره موبایل معتبر نیست' });

    const o = otps.get(phone);
    if (!o || !o.code) return json(res, 400, { ok: false, error: 'ابتدا کد تایید درخواست کنید' });
    if (Date.now() > o.expires) return json(res, 400, { ok: false, error: 'کد منقضی شده؛ کد جدید درخواست کنید' });
    if ((o.attempts || 0) >= MAX_VERIFY_ATTEMPTS) {
      return json(res, 429, { ok: false, error: 'تلاش‌های بیش از حد؛ کد جدید درخواست کنید' });
    }
    if (o.code !== code) {
      o.attempts = (o.attempts || 0) + 1;
      return json(res, 400, { ok: false, error: 'کد وارد شده اشتباه است' });
    }

    users[phone] = { at: Date.now() };
    saveUsers();
    otps.delete(phone);
    return json(res, 200, { ok: true, token: tokenFor(phone), phone: phone });
  }

  // ---- بررسی عضویت شماره ----
  if (p === '/api/users/exists' && req.method === 'GET') {
    const phone = normalizePhone(u.searchParams.get('phone') || '');
    return json(res, 200, { ok: true, registered: !!users[phone] });
  }

  return json(res, 404, { ok: false, error: 'not found' });
});

server.listen(PORT, () => {
  console.log('Sukot Messenger Server v2.0.0 (maker: Sukot) on port ' + PORT + ' | provider: ' + PROVIDER);
  if (PROVIDER === 'dev') console.log('WARN: dev mode — OTP codes are returned in the API response, not sent by SMS.');
});
