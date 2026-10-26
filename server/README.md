# سرور پیام‌رسان سوکوت (تایید پیامکی شماره)

سرور سبک و بدون وابستگی (فقط Node.js) برای تایید شماره موبایل کاربران با **کد پیامکی (OTP)** — دقیقاً مثل پیام‌رسان‌های واقعی.

## اجرا سریع

```bash
node server.js
# سرور روی پورت 3000 بالا می‌آید (حالت dev: کد در پاسخ API برگردانده می‌شود و پیامکی ارسال نمی‌شود)
```

## درگاه‌های پیامکی

### کاوه‌نگار (پیشنهادی برای ایران)
1. در [kavenegar.com](https://kavenegar.com) ثبت‌نام کنید و از پنل، API Key بگیرید.
2. یک خط ارسال (Sender Line) فعال داشته باشید (مثلاً +985000... یا 10004346).
3. اجرا:

```bash
export SMS_PROVIDER=kavenegar
export KAVENEGAR_API_KEY=کلید-شما
export KAVENEGAR_SENDER=+985000...
export SECRET=یک-رشته-تصادفی-طولانی
export BRAND=سوکوت
node server.js
```

### SMS.ir
1. در [sms.ir](https://sms.ir) ثبت‌نام کنید و یک **قالب تایید (Verify Template)** بسازید که شامل `%CODE%` باشد.
2. اجرا:

```bash
export SMS_PROVIDER=smsir
export SMSIR_API_KEY=کلید-شما
export SMSIR_TEMPLATE_ID=شناسه-قالب
export SMSIR_PARAM=CODE
export SECRET=یک-رشته-تصادفی-طولانی
node server.js
```

## API

| مسیر | متد | ورودی | خروجی |
|---|---|---|---|
| `/` | GET | — | وضعیت سرور |
| `/api/otp/request` | POST | `{"phone":"09123456789"}` | `{"ok":true}` (در حالت dev: `dev_code`) |
| `/api/otp/verify` | POST | `{"phone":"09123456789","code":"123456"}` | `{"ok":true,"token":"..."}` |
| `/api/users/exists?phone=...` | GET | — | `{"ok":true,"registered":true}` |

## امنیت و محدودیت‌ها (ضد سوءاستفاده — غیرقابل تغییر)

- حداقل فاصله بین دو ارسال کد: **۶۰ ثانیه**
- حداکثر کد روزانه برای هر شماره: **۱۰ عدد**
- اعتبار هر کد: **۵ دقیقه** — حداکثر ۵ تلاش اشتباه
- شماره‌ها فقط موبایل ایران (`09xxxxxxxxx`) پذیرفته می‌شود

> این محدودیت‌ها عمداً سخت‌گیرانه‌اند تا از این سرور نتوان برای ارسال انبوه/آزاری استفاده کرد.

## استقرار روی سرور واقعی (VPS)

```bash
# نصب Node
curl -fsSL https://deb.nodesource.com/setup_20.x | bash - && apt install -y nodejs

# اجرای پایدار با systemd
cat >/etc/systemd/system/sukot-server.service <<'EOF'
[Unit]
Description=Sukot Messenger Server
After=network.target
[Service]
Environment=SMS_PROVIDER=kavenegar
Environment=KAVENEGAR_API_KEY=کلید-شما
Environment=SECRET=رشته-تصادفی
WorkingDirectory=/opt/sukot/server
ExecStart=/usr/bin/node server.js
Restart=always
[Install]
WantedBy=multi-user.target
EOF
systemctl enable --now sukot-server
```

سپس در برنامه، در صفحه ورود روی «آدرس سرور» بزنید و آدرس سرور را وارد کنید (مثلاً `https://sukot.example.com`).

روی پلتفرم‌های رایگان مثل Render / Railway / Koyeb هم می‌توانید همین پوشه `server/` را دپلوی کنید (Start command: `node server.js`).
