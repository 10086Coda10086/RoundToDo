# =================================================
# RoundToDo 混淆规则终极版
# =================================================

# --- 1. 核心：忽略 MSAL 及其引用库的警告 ---
-dontwarn com.microsoft.identity.**
-keep class com.microsoft.identity.** { *; }

# --- 2. 忽略日志监控库 (OpenTelemetry) ---
-dontwarn io.opentelemetry.**

# --- 3. 忽略加密相关库 (Tink, Nimbus) ---
-dontwarn com.google.crypto.tink.**
-dontwarn com.nimbusds.**

# --- 4. ★★★ 新增：忽略 YubiKey 硬件密钥库 ★★★ ---
-dontwarn com.yubico.**

# --- 5. ★★★ 新增：忽略 FindBugs 注解库 ★★★ ---
-dontwarn edu.umd.cs.findbugs.**

# --- 6. 忽略谷歌自动生成代码 (AutoValue) ---
-dontwarn com.google.auto.value.**

# --- 7. 预防性忽略其他常见报错 ---
-dontwarn net.jcip.annotations.**
-dontwarn javax.annotation.**
-dontwarn org.apache.http.**
-dontwarn android.net.http.**
-dontwarn java.nio.file.**
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement