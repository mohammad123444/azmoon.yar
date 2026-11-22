# راه‌اندازی اتصال آنلاین (Firebase) — یک بار، حدود ۱۰ دقیقه

بدون این مراحل برنامه مثل قبل آفلاین کار می‌کند (ارسال با لینک/فایل). برای «حساب معلم»، «کد آزمون» و «نتیجهٔ خودکار»:

1. به https://console.firebase.google.com بروید ← **Add project** (نام دلخواه؛ Analytics لازم نیست).
2. **Build ← Authentication ← Get started ← Email/Password ← Enable ← Save**.
3. **Build ← Realtime Database ← Create database** (هر منطقه؛ حالت **locked** را انتخاب کنید). آدرس بالای صفحه مثل `https://NAME-default-rtdb.firebaseio.com` است ← کپی کنید.
4. در همان صفحهٔ Realtime Database، تب **Rules** را باز کنید و این متن را جایگزین و **Publish** کنید:

```json
{
  "rules": {
    "exams": {
      "$code": {
        ".read": true,
        ".write": "auth != null && (!data.exists() || data.child('owner').val() === auth.uid)",
        ".validate": "newData.hasChildren(['owner','data']) && newData.child('owner').val() === auth.uid"
      }
    },
    "results": {
      "$code": {
        ".read": "auth != null && root.child('exams').child($code).child('owner').val() === auth.uid",
        "$rid": {
          ".write": "!data.exists() && root.child('exams').child($code).exists()",
          ".validate": "newData.hasChildren(['c','t']) && newData.child('c').isString() && newData.child('c').val().length < 20000"
        }
      }
    }
  }
}
```

5. **Project settings (چرخ‌دنده) ← General ← Web API Key** را کپی کنید.
6. فایل `app/src/main/java/com/example/quizapp/OnlineConfig.kt` را باز کنید و دو مقدار را بنویسید:
   `API_KEY = "…کلید…"` و `DB_URL = "https://NAME-default-rtdb.firebaseio.com"`.
7. فایل‌ها را در گیتهاب آپلود کنید تا APK ساخته شود.

نکته‌ها
- کلید Web API در Firebase محرمانه نیست (محافظت با قوانین بالا است). دانش‌آموز بدون حساب فقط می‌تواند آزمون را با کد بخواند و نتیجه بفرستد؛ فقط معلمِ منتشرکننده نتیجه‌ها را می‌خواند.
- آزمونِ منتشرشده شامل گزینهٔ درست هم هست (برنامه با همان نمره می‌دهد)؛ برای مدرسه کافی است، ولی در برابر کسی که ترافیک شبکه را بررسی کند مقاوم نیست.
- زمان آزمون در برنامهٔ دانش‌آموز اعمال می‌شود، نه روی سرور.
