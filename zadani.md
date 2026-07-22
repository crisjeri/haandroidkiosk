# Zadání: Android kiosk aplikace pro Home Assistant

Vytvoř kompletní Android aplikaci v Kotlinu pro tablet Samsung Galaxy Tab A 10.1 LTE, model SM-T585 z roku 2016, s LineageOS 19.1 založeným na Androidu 12 (API 32).

Aplikace má fungovat jako jednoduchý kiosk pro zobrazení webového rozhraní Home Assistant. Nemá kopírovat všechny funkce Fully Kiosk Browser. Prioritou je stabilita, nízké využití CPU a baterie, jednoduché nastavení a spolehlivý provoz na starším tabletu.

## Hlavní účel

Aplikace bude trvale spuštěná na nástěnném tabletu a bude:

1. zobrazovat zadanou webovou stránku Home Assistant v celoobrazovkovém režimu,
2. automaticky zapínat nebo rozsvěcet obrazovku při detekci osoby před tabletem,
3. po nastavené době bez detekce osoby ztlumit a následně vypnout nebo uzamknout obrazovku,
4. automaticky se spouštět po restartu zařízení,
5. obnovovat webovou stránku po ztrátě a obnovení sítě,
6. fungovat lokálně bez cloudových služeb.

## Technické požadavky

Použij:

* Kotlin,
* Android SDK kompatibilní s Androidem 12, API 32,
* Android Studio / Gradle,
* AndroidX,
* klasické XML layouty, ne Jetpack Compose,
* WebView jako hlavní zobrazovací komponentu,
* CameraX pouze tehdy, pokud je na tomto zařízení a API stabilní; jinak použij Camera2 API,
* minimální počet externích knihoven,
* žádné Google Play Services,
* žádné reklamní, analytické ani cloudové SDK.

Nastav:

* `minSdk` maximálně 26,
* `targetSdk` podle aktuálně použitelné stabilní konfigurace, ale zachovej plnou funkčnost na Androidu 12,
* architekturu jednoduchou a dobře udržovatelnou,
* oddělení WebView, detekce přítomnosti, správy obrazovky a konfigurace.

Projekt musí jít sestavit pomocí:

```bash
./gradlew assembleDebug
```

## Funkce aplikace

### 1. WebView kiosk

Hlavní obrazovka bude obsahovat WebView přes celou plochu displeje.

WebView musí podporovat:

* HTTPS,
* JavaScript,
* DOM storage,
* cookies,
* lokální úložiště,
* WebSocket spojení používané Home Assistantem,
* přehrávání médií, pokud to Android WebView umožní,
* přihlášení do Home Assistantu,
* zachování přihlášení po restartu aplikace,
* správné zpracování interních odkazů,
* navigaci pouze uvnitř aplikace.

Výchozí URL nesmí být natvrdo zapsaná ve zdrojovém kódu. Uživatel ji nastaví v konfiguraci.

Při prvním spuštění zobraz konfigurační obrazovku.

WebView musí:

* při startu načíst nastavenou URL,
* při chybě sítě zobrazit jednoduchou lokální chybovou stránku,
* automaticky zkusit stránku znovu načíst po obnovení sítě,
* po probuzení obrazovky volitelně obnovit stránku,
* podporovat gesto nebo skrytou akci pro otevření nastavení.

Z bezpečnostních důvodů:

* zakaž přístup WebView k lokálním souborům, pokud není nezbytný,
* zakaž univerzální přístup z `file://` URL,
* neignoruj chyby TLS certifikátů,
* nepoužívej nebezpečné JavaScript bridge rozhraní,
* povol pouze `https://` a volitelně lokální `http://` adresy po výslovném povolení v nastavení,
* externí URL mimo povolený hostitel blokuj nebo otevři pouze po potvrzení.

### 2. Celoobrazovkový režim

Aplikace bude fungovat v immersive fullscreen režimu.

Skryj:

* stavový řádek,
* navigační lištu,
* systémové ovládací prvky, pokud to Android dovolí.

Po jejich dočasném zobrazení je opět automaticky skryj.

Aplikace musí mít volitelnou možnost nastavit se jako výchozí domovská aplikace pomocí intent filtru `HOME`.

Neimplementuj obcházení bezpečnostních mechanismů Androidu.

Pro skutečný uzamčený kiosk režim připrav podporu Android Lock Task Mode. Pokud aplikace není nastavena jako Device Owner, použij pouze dostupný omezený režim a v dokumentaci vysvětli omezení.

### 3. Detekce osoby

Detekci realizuj pomocí přední kamery.

Cílem není rozpoznávání obličeje ani identifikace konkrétní osoby. Aplikace pouze rozhoduje, zda se před tabletem pravděpodobně nachází nebo pohybuje člověk.

Preferovaná implementace MVP:

* kamera snímá v nízkém rozlišení,
* analyzuje se pouze několik snímků za sekundu, například 1 až 3 FPS,
* snímky se nikam neukládají,
* snímky se neposílají po síti,
* porovnávají se změny mezi po sobě jdoucími snímky,
* používá se downsampling a převod na odstíny šedi,
* vypočítá se průměrný rozdíl pixelů nebo změna v definovaných oblastech,
* ignorují se malé změny způsobené šumem kamery,
* po překročení nastavitelného prahu se zaznamená pohyb nebo přítomnost.

Detekce musí mít:

* nastavitelnou citlivost,
* nastavitelný počet analyzovaných snímků za sekundu,
* nastavitelnou dobu bez pohybu před zhasnutím obrazovky,
* ochrannou dobu proti rychlému zapínání a vypínání,
* možnost zapnout diagnostický režim s hodnotou detekovaného pohybu,
* možnost detekci zcela vypnout.

Optimalizuj ji pro starý hardware:

* nepoužívej plné rozlišení kamery,
* nepoužívej rozpoznávání obličeje ani neuronové sítě v základní verzi,
* nezpracovávej snímky v hlavním UI vlákně,
* po zhasnutí obrazovky používej co nejnižší rozumnou frekvenci snímání,
* správně uvolňuj kameru při ukončení nebo pozastavení služby.

Aplikace musí korektně zpracovat situace, kdy:

* uživatel nepovolí kameru,
* kamera není dostupná,
* kameru používá jiná aplikace,
* kamera selže,
* systém ukončí proces aplikace.

Při selhání kamery nesmí aplikace opakovaně padat. Zobraz stav v nastavení a použij bezpečný fallback.

### 4. Zapnutí a vypnutí obrazovky

Při detekci pohybu nebo osoby:

* rozsviť obrazovku,
* nastav normální jas,
* probuď zařízení dostupným podporovaným API,
* obnov fullscreen režim,
* volitelně obnov WebView.

Použij moderní a podporovaný přístup, například:

* `PowerManager.WakeLock` pouze krátkodobě a s časovým limitem,
* `WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON`,
* správu jasu okna,
* případně Device Admin API pro uzamknutí zařízení.

Wake lock nesmí zůstat držený trvale.

Po nastavené době bez pohybu:

1. sniž jas obrazovky na nastavitelnou hodnotu,
2. po volitelné prodlevě obrazovku uzamkni nebo vypni.

Android běžné aplikaci neumožňuje vždy přímo vypnout displej. Proto implementuj dvě varianty:

#### Varianta A – bez Device Admin

* aplikace sníží jas na minimum,
* odstraní `KEEP_SCREEN_ON`,
* nechá systém obrazovku vypnout podle systémového timeoutu.

#### Varianta B – s Device Admin

* uživatel může aplikaci dobrovolně aktivovat jako správce zařízení,
* aplikace může použít `DevicePolicyManager.lockNow()`,
* aplikace musí obsahovat obrazovku pro aktivaci a deaktivaci Device Admin,
* před odinstalováním musí být možné oprávnění jednoduše odebrat.

Nikdy se nepokoušej zařízení odemykat obcházením zámku obrazovky.

V dokumentaci vysvětli, že pro automatické probuzení bez PINu musí být zařízení vhodně nakonfigurované, například bez bezpečného zámku obrazovky nebo v podporovaném kiosk režimu.

### 5. Foreground service

Detekce přítomnosti musí běžet ve foreground service, aby ji Android 10 zbytečně neukončoval.

Foreground service musí:

* zobrazovat trvalou nenápadnou notifikaci,
* mít správný notification channel,
* po pádu nebo ukončení se bezpečně obnovit,
* používat `START_STICKY` pouze tehdy, pokud je to vhodné,
* neprovádět těžkou práci na hlavním vlákně,
* korektně reagovat na udělení a odebrání oprávnění kamery.

Notifikace může obsahovat:

* stav detekce,
* stav kamery,
* tlačítko pro otevření aplikace,
* tlačítko pro dočasné vypnutí detekce.

### 6. Automatický start

Po restartu zařízení aplikace automaticky spustí foreground service a případně hlavní kiosk aktivitu.

Použij `BOOT_COMPLETED`.

Respektuj omezení Androidu 12 a zajisti, aby aplikace po restartu nepadala, pokud ještě není zařízení odemčené.

V nastavení přidej volbu:

* spustit službu po restartu,
* otevřít kiosk obrazovku po restartu.

### 7. Síťová konektivita

Sleduj síť pomocí `ConnectivityManager.NetworkCallback`.

Při ztrátě sítě:

* ponech poslední obsah WebView, pokud je dostupný,
* zobraz nenápadný indikátor offline stavu,
* neprováděj agresivní opakované načítání.

Při obnovení sítě:

* počkej krátkou dobu,
* ověř dostupnost sítě,
* znovu načti Home Assistant pouze jednou,
* použij exponenciální prodlevu při opakovaných chybách.

Neprováděj ping na veřejné internetové služby. Home Assistant může fungovat pouze v lokální síti.

### 8. Nastavení

Vytvoř jednoduchou konfigurační obrazovku.

Nastavení musí obsahovat:

* Home Assistant URL,
* povolení nešifrovaného HTTP,
* automatické spuštění po restartu,
* automatické otevření kiosk aktivity,
* zapnutí detekce pohybu,
* citlivost detekce,
* počet analyzovaných snímků za sekundu,
* čas bez pohybu před ztlumením,
* čas mezi ztlumením a vypnutím,
* jas při aktivním režimu,
* jas při ztlumeném režimu,
* obnovení WebView po probuzení,
* obnovení WebView po návratu sítě,
* aktivaci Device Admin,
* test detekce kamery,
* tlačítko „Obnovit stránku“,
* tlačítko „Vymazat cache WebView“,
* tlačítko „Ukončit kiosk režim“.

Nastavení ukládej do `SharedPreferences` nebo AndroidX DataStore.

Citlivé údaje neukládej do logu.

Home Assistant token se nebude ručně ukládat. Přihlášení bude spravovat WebView pomocí cookies a webového úložiště.

### 9. Přístup do nastavení

Protože aplikace poběží přes celou obrazovku, implementuj skrytý, ale snadno použitelný vstup do nastavení.

Použij jednu z možností:

* pět rychlých klepnutí do levého horního rohu,
* dlouhé podržení v levém horním rohu,
* definované gesto.

Po aktivaci zobraz PIN dialog.

PIN:

* bude nastavitelný,
* nebude uložen jako prostý text,
* ulož pouze hash se salt,
* po několika chybných pokusech zaveď krátkou prodlevu,
* výchozí PIN v produkčním buildu nesmí existovat.

Při prvním spuštění musí uživatel PIN vytvořit.

### 10. Oprávnění

Požaduj pouze nezbytná oprávnění:

* `INTERNET`,
* `ACCESS_NETWORK_STATE`,
* `CAMERA`,
* `WAKE_LOCK`,
* `RECEIVE_BOOT_COMPLETED`,
* `FOREGROUND_SERVICE`,
* případně oprávnění potřebné pro foreground service s kamerou podle target SDK.

Oprávnění kamery vyžádej za běhu a vysvětli jeho účel.

Device Admin nesmí být aktivován automaticky. Uživatel musí aktivaci výslovně potvrdit v systémové obrazovce.

### 11. Soukromí a bezpečnost

Dodrž následující pravidla:

* žádný obraz z kamery neukládej,
* žádný obraz z kamery neposílej po síti,
* žádná telemetrie,
* žádná analytika,
* žádné reklamy,
* žádné cloudové API,
* neloguj cookies, hesla, tokeny ani obsah Home Assistant stránky,
* neignoruj chyby certifikátů,
* WebView debugging povol pouze v debug buildu,
* release build musí mít debugging vypnutý,
* exportované Android komponenty nastav jen tam, kde je to nutné,
* všechny broadcast receivery a služby nakonfiguruj podle bezpečnostních pravidel Androidu.

### 12. Logování a diagnostika

Použij strukturované logování s jasnými tagy.

V release buildu omez logování na chyby a důležité provozní události.

Přidej diagnostickou obrazovku, která zobrazí:

* verzi aplikace,
* verzi Androidu,
* verzi Android System WebView,
* aktuální URL,
* stav sítě,
* stav kamery,
* stav foreground service,
* čas poslední detekce pohybu,
* aktuální hodnotu detekce,
* stav Device Admin,
* poslední chybu WebView.

Nepoužívej root oprávnění.

## Uživatelské rozhraní

UI musí být jednoduché a použitelné na rozlišení tabletu 1920 × 1200.

Použij tmavé barevné schéma.

Hlavní kiosk obrazovka:

* WebView přes celý displej,
* žádný toolbar,
* volitelný malý offline indikátor,
* žádná viditelná ovládací tlačítka.

Konfigurační obrazovka:

* přehledné sekce,
* popisy nastavení v češtině,
* validace URL,
* potvrzovací dialog pro rizikové operace,
* jasná informace o oprávnění kamery a Device Admin.

## Struktura projektu

Navrhni projekt přibližně s těmito komponentami:

```text
app/
  src/main/
    java/.../
      MainActivity.kt
      SettingsActivity.kt
      MotionDetectionService.kt
      MotionDetector.kt
      CameraController.kt
      ScreenController.kt
      WebViewController.kt
      NetworkMonitor.kt
      BootReceiver.kt
      DeviceAdminReceiverImpl.kt
      PreferencesRepository.kt
      PinManager.kt
    res/
      layout/
      values/
      xml/
    AndroidManifest.xml
```

Názvy můžeš upravit, pokud navrhneš lepší členění.

## Robustnost

Aplikace musí:

* přežít dlouhodobý provoz,
* neuvolněné prostředky nesmí způsobovat memory leak,
* při změně konfigurace nesmí zbytečně vytvářet nové instance kamery,
* správně ukončit executory a background thready,
* zachytit běžné chyby WebView a kamery,
* nepadat při chybějícím oprávnění,
* neprovádět nekonečné rychlé retry smyčky,
* šetřit CPU a baterii,
* zabránit současnému spuštění více instancí motion service.

## Testy

Přidej alespoň základní unit testy pro:

* validaci URL,
* výpočet hodnoty pohybu mezi dvěma snímky,
* časovou logiku přechodu aktivní → ztlumený → vypnutý stav,
* exponenciální retry síťového připojení,
* hashování a kontrolu PINu.

Pokud je to rozumné, přidej jednoduchý instrumentační test spuštění hlavní aktivity.

## Stavový automat obrazovky

Implementuj explicitní stavový automat:

```text
ACTIVE
  -> po době bez pohybu DIMMED

DIMMED
  -> při pohybu ACTIVE
  -> po další době bez pohybu SCREEN_OFF

SCREEN_OFF
  -> při detekci pohybu ACTIVE
```

Zabraň opakovanému volání zapnutí nebo vypnutí obrazovky při každém analyzovaném snímku.

Použij monotónní čas, například `SystemClock.elapsedRealtime()`, ne systémový kalendářní čas.

## Výstup

Vytvoř kompletní sestavitelný projekt, ne pouze ukázky kódu.

Dodej:

1. všechny Kotlin soubory,
2. XML layouty,
3. `AndroidManifest.xml`,
4. Gradle konfiguraci,
5. konfiguraci Device Admin,
6. české textové řetězce,
7. unit testy,
8. soubor `README.md`,
9. instalační a konfigurační postup,
10. popis známých omezení Androidu 12,
11. postup sestavení APK přes příkazovou řádku,
12. postup instalace přes ADB.

README musí obsahovat například:

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Také vysvětli:

* jak nastavit aplikaci jako výchozí launcher,
* jak aktivovat Device Admin,
* jak Device Admin před odinstalováním deaktivovat,
* jak aplikaci odinstalovat přes ADB,
* jak nastavit systémový timeout obrazovky,
* jak ověřit verzi Android System WebView,
* jak získat logy pomocí `adb logcat`.

## Postup implementace

Pracuj po etapách:

1. vytvoř kostru projektu,
2. implementuj WebView kiosk,
3. implementuj nastavení,
4. implementuj síťový monitor,
5. implementuj správu obrazovky,
6. implementuj kameru a detekci změn obrazu,
7. implementuj foreground service,
8. implementuj automatický start,
9. implementuj Device Admin,
10. implementuj PIN a přístup do nastavení,
11. přidej testy,
12. proveď kontrolu manifestu, oprávnění a lifecycle,
13. doplň README.

Po každé etapě:

* spusť sestavení,
* oprav chyby kompilace,
* zkontroluj možné memory leaky,
* zkontroluj kompatibilitu s API 32.

Nezastavuj se u návrhu. Vygeneruj skutečný projekt a průběžně opravuj chyby, dokud `./gradlew assembleDebug` neproběhne úspěšně.

## Omezení první verze

Do první verze nepřidávej:

* rozpoznávání obličejů,
* nahrávání videa,
* odesílání obrazu,
* vzdálenou cloudovou administraci,
* MQTT server,
* vlastní aktualizační službu,
* root funkce,
* obcházení zámku obrazovky,
* Accessibility Service,
* sledování uživatele,
* Google Firebase.

Nejprve vytvoř stabilní a úsporné MVP.

## Kritéria přijetí

Projekt je hotový, pokud:

* lze jej sestavit příkazem `./gradlew assembleDebug`,
* lze jej nainstalovat na Android 10,
* WebView zobrazí Home Assistant,
* přihlášení v Home Assistantu zůstane zachované,
* aplikace funguje přes celou obrazovku,
* po restartu lze automaticky spustit službu,
* kamera detekuje významnou změnu obrazu,
* při detekci se displej rozsvítí,
* po době nečinnosti se displej ztlumí,
* s Device Admin se zařízení může uzamknout,
* bez Device Admin aplikace bezpečně použije systémový timeout,
* výpadek sítě nezpůsobí pád,
* obnovení sítě vyvolá řízené obnovení stránky,
* obraz z kamery se neukládá ani neodesílá,
* aplikace nevytěžuje trvale jedno CPU jádro,
* všechna důležitá nastavení lze změnit bez úpravy zdrojového kódu.

Začni kontrolou technických omezení Androidu 12 a následně vytvoř celý projekt.
