# HA Android Kiosk

Jednoduchá lokální kiosk aplikace pro Home Assistant určená primárně pro Samsung Galaxy Tab A 10.1 LTE (SM-T585) s LineageOS 19.1 / Androidem 12 (API 32). Aplikace používá klasické XML layouty, WebView, Camera2 a jednu foreground službu. Neobsahuje reklamy, analytiku, Google Play Services ani cloudové služby.

## Funkce

- celoobrazovkový WebView s cookies, DOM storage, JavaScriptem, médii a WebSockety,
- HTTPS a volitelně výslovně povolené lokální HTTP,
- blokování navigace mimo nakonfigurovaného hostitele a odmítnutí chyb TLS,
- lokální indikátor výpadku sítě a řízené obnovení s exponenciální prodlevou,
- detekce změn obrazu přední kamerou při nízkém rozlišení a 1–3 FPS,
- stavový automat `ACTIVE → DIMMED → SCREEN_OFF`,
- krátký wake lock při detekci, jas okna a bezpečný fallback na systémový timeout,
- volitelný Device Admin pro `lockNow()`,
- foreground služba s trvalou notifikací a automatickým startem po bootu,
- možnost výchozí domovské aplikace a podpora Lock Task Mode,
- nastavení chráněné salted PBKDF2 PINem s dočasnou blokací pokusů,
- volba jedné ze čtyř pevných orientací nezávislá na senzoru tabletu,
- diagnostická obrazovka bez citlivých údajů.

Obraz z kamery se nikdy neukládá, neposílá po síti ani nezobrazuje. Zpracovává se pouze jasová složka zmenšená na čtvrtinu v obou rozměrech.

## Požadavky a sestavení

- JDK 17,
- Android SDK Platform 36 a Build Tools 36,
- přístup k Google Maven/Maven Central při prvním načtení závislostí.

Projekt obsahuje Gradle wrapper:

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

APK vznikne v `app/build/outputs/apk/debug/app-debug.apk`.

### Podepsaný release build

Release podpis se načítá z uživatelského souboru `~/.gradle/gradle.properties`, který není součástí repozitáře:

```properties
HA_KIOSK_STORE_FILE=/absolutni/cesta/ha-kiosk-release.jks
HA_KIOSK_STORE_PASSWORD=heslo-keystore
HA_KIOSK_KEY_ALIAS=ha-kiosk
HA_KIOSK_KEY_PASSWORD=heslo-klice
```

Soubor zabezpečte a sestavte release APK:

```bash
chmod 600 ~/.gradle/gradle.properties
./gradlew assembleRelease
```

Podepsané APK vznikne v `app/build/outputs/apk/release/app-release.apk`. Pokud konfigurace nebo keystore chybí, release sestavení skončí s popisnou chybou. Debug sestavení zůstává dostupné nezávisle. Keystore ani hesla nikdy neukládejte do repozitáře a klíč bezpečně zálohujte, protože všechny budoucí aktualizace musí používat stejný podpis.

## Instalace přes ADB

Na tabletu zapněte Vývojářské možnosti a Ladění USB, potvrďte otisk počítače a spusťte:

```bash
adb devices -l
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Spuštění aplikace z příkazové řádky:

```bash
adb shell am start -n cz.hykel.hakiosk/.MainActivity
```

Při prvním spuštění aplikace vyžaduje vytvoření nejméně čtyřmístného PINu a zadání Home Assistant URL. Pro lokální `http://` je nutné výslovně zapnout příslušnou volbu. Kamera se povoluje standardním systémovým dialogem.

## Přístup do nastavení

Přiložte dva prsty kamkoliv na kiosk obrazovku a držte je přibližně dvě sekundy bez výrazného pohybu. Poté zadejte PIN. Gesto neblokuje běžné ovládací prvky Home Assistantu. Po pěti chybných pokusech je ověřování na 30 sekund zablokované. V aplikaci není žádný výchozí PIN.

## Výchozí launcher

Aplikace deklaruje aktivitu `HOME`. Stiskněte systémové tlačítko Domů a v dialogu zvolte **HA Kiosk → Vždy**. Výběr lze změnit v systémovém nastavení výchozích aplikací. Jako výchozí launcher je kiosk po bootu spolehlivější než běžné spouštění aktivity z `BOOT_COMPLETED`.

## Device Admin

V nastavení aplikace zvolte **Aktivovat Device Admin** a potvrďte systémovou obrazovku. Po aktivaci může aplikace při přechodu do `SCREEN_OFF` zavolat `DevicePolicyManager.lockNow()`.

Před odinstalací oprávnění odeberte tlačítkem **Deaktivovat Device Admin** nebo v systémovém nastavení správců zařízení. Přes ADB lze správce odebrat:

```bash
adb shell dpm remove-active-admin cz.hykel.hakiosk/.DeviceAdminReceiverImpl
```

Poté lze aplikaci odinstalovat:

```bash
adb uninstall cz.hykel.hakiosk
```

## Lock Task Mode

Volba v aplikaci zavolá `startLockTask()`. Bez Device Owner obvykle Android nabídne pouze připnutí obrazovky, které uživatel může ukončit systémovou kombinací. Plný uzamčený kiosk vyžaduje, aby byla aplikace na spravovaném nebo nově připraveném zařízení povolena Device Ownerem. Samotná aktivace Device Admin z aplikace nestačí.

Nastavení Device Owner přes ADB smaže nebo vyžaduje čerstvě připravený stav zařízení a nesmí se provádět bez zálohy. Aplikace úmyslně neobchází systémové zabezpečení.

## Vypínání a probouzení displeje

Bez Device Admin aplikace sníží jas, odstraní `KEEP_SCREEN_ON` a čeká na systémový timeout. Doporučený timeout lze nastavit v systému nebo přes ADB, například na 30 sekund:

```bash
adb shell settings put system screen_off_timeout 30000
adb shell settings get system screen_off_timeout
```

Nastavená „prodleva do vypnutí“ určuje okamžik, kdy aplikace odstraní `KEEP_SCREEN_ON`. Bez Device Admin se k ní přičte systémový timeout. Pokud je například systémový timeout 300 sekund, může ztlumený mezistav trvat dalších pět minut.

S Device Admin se zařízení uzamkne ihned po nakonfigurované prodlevě. Aplikace nedokáže a nesmí obejít PIN, heslo ani bezpečný keyguard. Pro automatické probuzení přímo do kiosku musí být tablet bez bezpečného zámku nebo správně nasazený jako vyhrazené kiosk zařízení.

Na některých ROM může uzamčení zastavit přístup ke kameře. Služba použije exponenciální retry, ale detekce pak nemusí zařízení probudit, dokud systém kameru znovu nezpřístupní.

## Android 12 a systémová omezení

- Android 12 omezuje spouštění aktivit a foreground služeb z pozadí. `BOOT_COMPLETED` spustí službu pouze při uloženém nastavení a udělené kameře; automatické otevření aktivity je best-effort.
- Foreground služba musí zobrazovat trvalou notifikaci. Na Androidu 13+ může být navíc nutné povolit notifikace.
- Běžná aplikace nemůže přímo vypnout displej ani odemknout bezpečný keyguard.
- Plný Lock Task Mode vyžaduje správu zařízení a allowlist od Device Ownera.
- Chování kamery při zhasnutém nebo uzamčeném displeji závisí také na LineageOS, ovladači kamery a úsporných nastaveních.
- Pro stabilní provoz vypněte pro HA Kiosk agresivní optimalizaci baterie, pokud ROM službu ukončuje.

## Android System WebView

Kiosk vykresluje Home Assistant pomocí systémového WebView, nikoliv pomocí Firefoxu nebo přímo nainstalovaného Chromu. Na starším Androidu nebo na alternativní ROM, například LineageOS, může být aktivní WebView výrazně zastaralý. To se může projevit velmi pomalým vykreslováním Home Assistantu, i když je Firefox nebo Chrome aktuální a na stejném tabletu funguje rychle.

Verze aktivního WebView je viditelná na diagnostické obrazovce. Přes ADB lze poskytovatele a jeho verzi ověřit například:

```bash
adb shell dumpsys webviewupdate
adb shell pm list packages | grep -E 'webview|chrome'
```

Pokud je WebView starý, v Google Play vyhledejte a aktualizujte nebo nainstalujte aplikaci **Android System WebView**. Poté v **Nastavení → Systém → Možnosti pro vývojáře → Implementace WebView** zkontrolujte, že systém používá nově nainstalovanou verzi. Po změně poskytovatele restartujte kiosk, případně celý tablet.

Aktualizace Chromu nemusí WebView aktualizovat: některé ROM používají samostatný balíček `com.android.webview` nebo `com.google.android.webview`. Pokud novější WebView nelze z Google Play nainstalovat, použijte aktuálnější sestavení LineageOS obsahující kompatibilní WebView. Ruční instalace náhodného APK se nedoporučuje kvůli podpisu systémového balíčku, architektuře zařízení a kompatibilitě s danou verzí Androidu.

Aktuální WebView je důležitý nejen pro výkon, ale také pro přihlášení, WebSockety a moderní frontend Home Assistantu.

## Logy a diagnostika

Diagnostika v aplikaci zobrazuje síť, kameru, službu, baterii, poslední detekci, hodnotu změny, Device Admin, WebView a poslední chybu načítání. Kapacita a stav baterie se získávají standardním Android API. Pokud je nainstalována doplňková služba z adresáře `baterry`, zobrazuje se také aktuální `batt_slate_mode`; při chybějící, zastavené nebo neaktuální službě diagnostika pouze uvede, že řízení nabíjení není dostupné.

Logy bez obsahu webové stránky, cookies a tokenů:

```bash
adb logcat -s HaKiosk.Main HaKiosk.MotionService HaKiosk.Camera HaKiosk.Screen HaKiosk.Boot
```

Úplné logy procesu:

```bash
adb shell pidof cz.hykel.hakiosk
adb logcat --pid="$(adb shell pidof cz.hykel.hakiosk)"
```

## Architektura

- `MainActivity` – fullscreen kiosk, skrytý vstup a jas okna,
- `WebViewController` – bezpečná konfigurace, navigace a retry,
- `NetworkMonitor` – lokální `ConnectivityManager.NetworkCallback`,
- `SettingsActivity` / `PreferencesRepository` / `PinManager` – konfigurace a PIN,
- `CameraController` / `MotionDetector` – Camera2, downsampling a změna jasu,
- `MotionDetectionService` – foreground lifecycle, retry a zpracování mimo UI,
- `ScreenStateMachine` / `ScreenController` – monotónní čas, probuzení, ztlumení a zámek,
- `BootReceiver` / `DeviceAdminReceiverImpl` – systémové integrace,
- `DiagnosticsActivity` – provozní stav bez citlivých dat.

## Testy

Unit testy pokrývají validaci URL, detekci změny snímků, stavový automat displeje, exponenciální prodlevu a hashování/ověření PINu:

```bash
./gradlew testDebugUnitTest
```
