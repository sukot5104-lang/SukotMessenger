# سرور OTP سوکت روی Cloudflare Workers — سازنده: Sukot

نسخه کلادفلریِ `server/server.js` — بدون سرور مجازی، بدون هزینه (پلن رایگان کلادفلر).
API آن ۱۰۰٪ با نسخه Node یکی است؛ اپلیکیشن v2.0.0 هیچ تغییری لازم ندارد، فقط آدرس سرور جدید را در صفحه ورود اپ وارد کنید.

## پیش‌نیازها

1. اکانت رایگان [dash.cloudflare.com](https://dash.cloudflare.com)
2. یک **API Token** با این دو پرمیشن:
   - `Account → Workers Scripts → Edit`
   - `Account → Workers KV Storage → Edit`
3. (برای ارسال واقعی پیامک) کلید کاوه‌نگار یا SMS.ir — تا آن موقع می‌توانید با حالت `dev` تست کنید

## مراحل ساخت توکن

1. داشبورد کلادفلر → گوشه بالا راست → **My Profile** → **API Tokens** → **Create Token**
2. **Create Custom Token** → نام: `sukot-deploy`
3. Permissions:
   - `Account | Workers Scripts | Edit`
   - `Account | Workers KV Storage | Edit`
4. Account Resources: **Include → All accounts**
5. (پیشنهادی) در بخش TTL تاریخ انقضا یک‌روزه بگذارید
6. **Continue to summary → Create Token** — توکن فقط یک بار نمایش داده می‌شود

⚠️ **API Token** بدهید، نه Global API Key (کلید گلوبال دسترسی کامل اکانت می‌دهد).

## دیپلوی

```bash
# حالت آزمایشی (بدون درگاه پیامک — کد در پاسخ اپ نمایش داده می‌شود)
export CLOUDFLARE_API_TOKEN=xxxxxxxx
bash deploy.sh

# ارسال واقعی با کاوه‌نگار
export CLOUDFLARE_API_TOKEN=xxxxxxxx SMS_PROVIDER=kavenegar KAVENEGAR_API_KEY=xxxxxx
bash deploy.sh

# ارسال واقعی با SMS.ir (قالب OTP با پارامتر CODE ساخته باشید)
export CLOUDFLARE_API_TOKEN=xxxxxxxx SMS_PROVIDER=smsir SMSIR_API_KEY=xxxxxx SMSIR_TEMPLATE_ID=123456
bash deploy.sh
```

خروجی: آدرس `https://sukot-otp.<subdomain>.workers.dev`
این آدرس را در اپ: صفحه ورود ← روی نام سرور (بالای صفحه) بزنید ← آدرس را ذخیره کنید.

## نکته‌های مهم

- توکن کلادفلر و کلید درگاه پیامک مثل رمز عبور هستند؛ بعد از دیپلوی، توکن را از داشبورد Delete کنید.
- `SECRET` (کلید توکن‌ها) خودکار ساخته و در `worker/.secret` ذخیره می‌شود (جزو گیت نیست) — با هر دیپلوی دوباره همان استفاده می‌شود تا توکن‌های کاربران باطل نشوند.
- سقف‌های ضد سوءاستفاده: ۶۰ ثانیه فاصله بین ارسال‌ها، ۱۰ کد در روز برای هر شماره، ۵ تلاش برای هر کد، اعتبار ۵ دقیقه‌ای کد، و سقف کلی روزانه ۳۰۰ (با `GLOBAL_DAILY_LIMIT` قابل تغییر است).
- پلن رایگان Workers: ۱۰۰,۰۰۰ درخواست در روز و ۱,۰۰۰ عملیات نوشتن KV در روز — برای OTP یعنی تا حدود ۹۰ کاربر جدید در روز؛ برای مصرف بالاتر پلن $5 در ماه کافی است.
- اگر در بعضی اپراتورهای ایران آدرس `workers.dev` باز نشد، می‌توان بعداً یک دامنه اختصاصی به همان Worker وصل کرد (رایگان) یا از نسخه Node روی هاست دیگر استفاده کرد.

## فایل‌ها

| فایل | نقش |
|---|---|
| `worker.js` | کد سرور (ES Module) |
| `deploy.sh` | دیپلوی خودکار با curl (بدون wrangler) |
| `.secret` | کلید HMAC توکن‌ها — محلی، جزو گیت نیست |
