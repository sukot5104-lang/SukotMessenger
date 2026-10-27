#!/usr/bin/env bash
# =====================================================================
# دیپلوی سرور OTP سوکت روی Cloudflare Workers — سازنده: Sukot
# بدون نیاز به wrangler یا node — فقط curl + jq + openssl
# ---------------------------------------------------------------------
# استفاده:
#   export CLOUDFLARE_API_TOKEN=...      # توکن با پرمیشن Workers Scripts + Workers KV Storage
#   export SMS_PROVIDER=dev              # dev | kavenegar | smsir
#   export KAVENEGAR_API_KEY=...         # فقط اگر kavenegar
#   export KAVENEGAR_SENDER=...          # اختیاری (شماره خط)
#   export SMSIR_API_KEY=...             # فقط اگر smsir
#   export SMSIR_TEMPLATE_ID=...         # فقط اگر smsir
#   bash deploy.sh
# =====================================================================
set -euo pipefail
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CF="https://api.cloudflare.com/client/v4"
: "${SMS_PROVIDER:=dev}"
: "${BRAND:=سوکوت}"
: "${SMSIR_PARAM:=CODE}"
: "${GLOBAL_DAILY_LIMIT:=300}"

fail() { echo "❌ $*" >&2; exit 1; }
step() { printf '\n==> %s\n' "$*"; }
api() { curl -sS -H "Authorization: Bearer ${CLOUDFLARE_API_TOKEN:?CLOUDFLARE_API_TOKEN تنظیم نشده}" "$@"; }

command -v jq >/dev/null     || fail "jq نصب نیست"
command -v openssl >/dev/null || fail "openssl نصب نیست"
[ -f "$DIR/worker.js" ]      || fail "worker.js کنار این اسکریپت نیست"

# ---------- ۱/۷ اکانت ----------
step "۱/۷ بررسی توکن و دریافت Account ID"
ACC=$(api "$CF/accounts" | jq -re '.result[0].id // empty' 2>/dev/null || true)
[ -n "${ACC:-}" ] || fail "توکن نامعتبر است یا به هیچ اکانتی دسترسی ندارد (پرمیشن‌های توکن را چک کنید)"
echo "account: $ACC"

# ---------- ۲/۷ KV ----------
step "۲/۷ ساخت/یافتن KV namespace (sukot_otp)"
NS=$(api "$CF/accounts/$ACC/storage/kv/namespaces" | jq -r '.result[]? | select(.title=="sukot_otp") | .id' | head -n1)
if [ -z "${NS:-}" ]; then
  NS=$(api -X POST "$CF/accounts/$ACC/storage/kv/namespaces" \
        -H "Content-Type: application/json" -d '{"title":"sukot_otp"}' \
        | jq -re '.result.id // empty' 2>/dev/null || true)
fi
[ -n "${NS:-}" ] || fail "ساخت KV namespace ناموفق بود — پرمیشن Workers KV Storage Edit لازم است"
echo "kv: $NS"

# ---------- ۳/۷ subdomain ----------
step "۳/۷ بررسی آدرس workers.dev"
SUB=$(api "$CF/accounts/$ACC/workers/subdomain" | jq -r '.result.subdomain // empty')
if [ -z "${SUB:-}" ]; then
  OK_SUB=""
  for i in 1 2 3 4 5; do
    SUB="sukot-$(openssl rand -hex 3)"
    if api -X PUT "$CF/accounts/$ACC/workers/subdomain" -H "Content-Type: application/json" \
         -d "{\"subdomain\":\"$SUB\"}" | jq -e '.success' >/dev/null 2>&1; then
      OK_SUB=1; break
    fi
  done
  [ -n "${OK_SUB:-}" ] || fail "ساخت subdomain ناموفق بود"
fi
echo "subdomain: $SUB"

# ---------- ۴/۷ آپلود اسکریپت ----------
step "۴/۷ آپلود Worker (sukot-otp)"
META_FILE="$DIR/.meta.json"
jq -n --arg ns "$NS" --arg provider "$SMS_PROVIDER" --arg brand "$BRAND" \
      --arg sender "${KAVENEGAR_SENDER:-}" --arg tid "${SMSIR_TEMPLATE_ID:-}" \
      --arg tparam "$SMSIR_PARAM" --arg gdl "$GLOBAL_DAILY_LIMIT" '
  {main_module:"worker.js", compatibility_date:"2025-01-01",
   bindings:[
     {type:"kv_namespace",name:"KV",namespace_id:$ns},
     {type:"plain_text",name:"SMS_PROVIDER",text:$provider},
     {type:"plain_text",name:"BRAND",text:$brand},
     {type:"plain_text",name:"SMSIR_PARAM",text:$tparam},
     {type:"plain_text",name:"GLOBAL_DAILY_LIMIT",text:$gdl}
   ] + (if $sender != "" then [{type:"plain_text",name:"KAVENEGAR_SENDER",text:$sender}] else [] end)
     + (if $tid != "" then [{type:"plain_text",name:"SMSIR_TEMPLATE_ID",text:$tid}] else [] end)}' > "$META_FILE"

RES=$(api -X PUT "$CF/accounts/$ACC/workers/scripts/sukot-otp" \
      -F "metadata=@${META_FILE};type=application/json" \
      -F "worker.js=@${DIR}/worker.js;type=application/javascript+module")
if ! echo "$RES" | jq -e '.success' >/dev/null 2>&1; then
  echo "$RES" | jq -r '.errors[]? | "\(.code): \(.message)"' 2>/dev/null || echo "$RES" | head -c 500
  fail "آپلود اسکریپت ناموفق بود"
fi
rm -f "$META_FILE"
echo "آپلود شد ✓"

# ---------- ۵/۷ secret ها ----------
step "۵/۷ ثبت Secret ها"
SECRET_FILE="$DIR/.secret"
if [ -n "${SECRET:-}" ]; then
  echo -n "$SECRET" > "$SECRET_FILE"
elif [ -f "$SECRET_FILE" ]; then
  SECRET="$(cat "$SECRET_FILE")"
else
  SECRET="$(openssl rand -hex 32)"
  echo -n "$SECRET" > "$SECRET_FILE"
fi

put_secret() {
  api -X PUT "$CF/accounts/$ACC/workers/scripts/sukot-otp/secrets" \
      -H "Content-Type: application/json" \
      -d "$(jq -n --arg n "$1" --arg t "$2" '{name:$n,text:$t,type:"secret_text"}')" \
      | jq -e '.success' >/dev/null 2>&1 || fail "ثبت secret «$1» ناموفق بود"
}
put_secret "SECRET" "$SECRET"
echo "SECRET ثبت شد ✓ (در فایل $SECRET_FILE ذخیره شد — جزو گیت نیست)"
if [ -n "${KAVENEGAR_API_KEY:-}" ]; then put_secret "KAVENEGAR_API_KEY" "$KAVENEGAR_API_KEY"; echo "KAVENEGAR_API_KEY ثبت شد ✓"; fi
if [ -n "${SMSIR_API_KEY:-}" ];     then put_secret "SMSIR_API_KEY" "$SMSIR_API_KEY";         echo "SMSIR_API_KEY ثبت شد ✓"; fi

# ---------- ۶/۷ فعال‌سازی workers.dev ----------
step "۶/۷ فعال‌سازی آدرس عمومی"
api -X PUT "$CF/accounts/$ACC/workers/scripts/sukot-otp/subdomain" \
    -H "Content-Type: application/json" \
    -d '{"enabled":true,"previews_enabled":false}' | jq -e '.success' >/dev/null 2>&1 \
    || fail "فعال‌سازی subdomain ناموفق بود"

# ---------- ۷/۷ تست ----------
URL="https://sukot-otp.${SUB}.workers.dev"
step "۷/۷ تست نهایی"
sleep 4
OK_HEALTH=""
for i in 1 2 3 4 5; do
  HEALTH="$(curl -sS --max-time 15 "$URL/" 2>/dev/null || true)"
  if echo "$HEALTH" | jq -e '.name' >/dev/null 2>&1; then OK_HEALTH=1; break; fi
  echo "   تلاش $i ناموفق — ۵ ثانیه صبر..."
  sleep 5
done
if [ -n "${OK_HEALTH:-}" ]; then
  echo "$HEALTH" | jq .
  echo ""
  echo "============================================================"
  echo "✅ سرور آماده است: $URL"
  echo "   این آدرس را در اپلیکیشن (صفحه ورود ← روی نام سرور بزنید) وارد کنید"
  echo "============================================================"
else
  echo "⚠️ تست اولیه جواب نداد — subdomain تازه ساخته شده و ممکن است چند دقیقه طول بکشد."
  echo "   بعدا با این دستور تست کنید:  curl $URL/"
fi
