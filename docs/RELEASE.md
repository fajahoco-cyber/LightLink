# انتشار نسخه‌ی امضاشده

## یک‌بار: ساخت کلید امضا (روی کامپیوتر خودتان، نه در مخزن)
```
keytool -genkeypair -v -keystore lightlink-release.jks -alias lightlink -keyalg RSA -keysize 4096 -validity 10000
```
رمزها را و فایل `lightlink-release.jks` را در جای امن نگه دارید. **اگر گم شود، نمی‌توانید به‌روزرسانی هم‌امضا منتشر کنید.**

## یک‌بار: ثبت Secrets در گیت‌هاب
مخزن ← Settings ← Secrets and variables ← Actions ← New repository secret:

| نام | مقدار |
|---|---|
| `KEYSTORE_BASE64` | خروجی `base64 -w0 lightlink-release.jks` (در ویندوز: `certutil -encode`) |
| `KEYSTORE_PASSWORD` | رمز فایل کلید |
| `KEY_ALIAS` | `lightlink` |
| `KEY_PASSWORD` | رمز خود کلید |

## هر انتشار
مخزن ← Releases ← Draft a new release ← یک تگ جدید مثل `v1.0.0` بسازید و Publish کنید (یا از Actions ← Release ← Run workflow).
گردش‌کار تست‌ها را اجرا می‌کند، APK را می‌سازد و به Release اضافه می‌کند. بدون Secrets، APK بدون امضای انتشار ساخته می‌شود و هشدار می‌دهد.

## نکته‌ها
- نسخه را پیش از انتشار در `app/build.gradle.kts` (`versionCode` و `versionName`) بالا ببرید.
- ساخت release با R8 (کوچک‌سازی) هنوز روی گوشی واقعی آزموده نشده؛ APK را پیش از پخش، حتماً نصب و امتحان کنید.
- Lint فعلاً `abortOnError = false` است؛ گزارش آن در CI ساخته می‌شود ولی ساخت را متوقف نمی‌کند.
