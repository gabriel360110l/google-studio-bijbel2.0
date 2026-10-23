# AGAPE — Volledige Architectuur-, Design- & Functie-Blueprint (voor iOS / SwiftUI)

Dit document beschrijft **stap voor stap en tot in het kleinste detail** hoe de **AGAPE**-applicatie in elkaar zit: van de 3 kerkelijke tradities (en wat er visueel en inhoudelijk verandert als je wisselt van kerk), de openingsanimatie, de kleurpaletten en typografie, tot elk scherm, de databaseschema's, de liturgische kalenderberekeningen, gebedsherinneringen met kerkklok, audio-voorleesfunctie (TTS), gebedsintenties en het interactieve gebedssnoer (Chotki & Rozenkrans).

Dit document is zo geschreven dat je het **direct als prompt/specificatie aan een AI kunt geven om de exacte iOS-versie in Swift & SwiftUI te bouwen**.

---

## 1. Kernconcept & De Drie Tradities (`Tradition`)

AGAPE is een tweetalig (**Nederlands `nl` & Engels `en`**), volledig offline-first christelijk platform voor Schriftlezing, het Getijdengebed (de vaste gebedsuren), Kerkvaders, Concilies, Heiligenlevens en de Liturgische Kalender.

De app ondersteunt drie historische apostolische tradities:
1. **`EASTERN_ORTHODOX`** (Oosters-Orthodox / Byzantijns)
2. **`ORIENTAL_ORTHODOX`** (Oriëntaals-Orthodox / Koptisch, Syrisch, Armeens, Ethiopisch, Eritrees, Malankara)
3. **`CATHOLIC`** (Rooms-Katholiek / Latijnse ritus)

### Wat verandert er zodra een gebruiker van Kerk / Traditie wisselt?
Wanneer de gebruiker in **Onboarding** of later in **Instellingen → Kerkelijke Traditie** wisselt tussen `EASTERN_ORTHODOX`, `ORIENTAL_ORTHODOX` en `CATHOLIC`, verandert het volgende **direct en automatisch** door de hele app:

| Onderdeel | `EASTERN_ORTHODOX` (Oosters-Orthodox) | `ORIENTAL_ORTHODOX` (Oriëntaals-Orthodox) | `CATHOLIC` (Rooms-Katholiek) |
| :--- | :--- | :--- | :--- |
| **1. Liturgisch Kruis-symbool** | **`☦`** (3-balks Slavisch/Byzantijns kruis) | **`☩`** (Koptisch/Oriëntaals kruis) | **`✝`** (Latijns kruis) |
| **2. Openingsanimatie Overlay** | Speelt direct opnieuw af met kruis **`☦`** en onderschrift `"OOSTERS-ORTHODOX · GOD IS LIEFDE"` | Speelt direct opnieuw af met kruis **`☩`** en onderschrift `"ORIËNTAALS-ORTHODOX · GOD IS LIEFDE"` | Speelt direct opnieuw af met kruis **`✝`** en onderschrift `"ROOMS-KATHOLIEK · GOD IS LIEFDE"` |
| **3. Achtergrondfoto op Home (`Backdrop`)** | Filtert uit `backgrounds.json` op Byzantijnse/Oosterse kerken (`iconostasis`, `chora`, `fossati`, `dome`) | Filtert uit `backgrounds.json` op Oriëntaalse heiligdommen & kaarsen (`sanctuary`, `candles`) | Filtert uit `backgrounds.json` op Westerse kathedralen & basilieken (`cathedral`, `santa_pr`) |
| **4. Traditie-badge linksboven op Home** | Toont `☦ Oosters-Orthodox` | Toont `☩ Oriëntaals-Orthodox` | Toont `✝ Rooms-Katholiek` |
| **5. Bijbelcanon (`books.json`)** | **76 boeken** (Septuaginta OT incl. 1 Esdras, 3 Makkabeeën, Psalm 151, Gebed van Manasse + NT) | **75+ boeken** (Oriëntaalse canon incl. deuterocanonieke boeken + NT) | **73 boeken** (Katholieke canon: 46 OT incl. Tobit, Judit, Wijsheid, Sirach, Baruch, 1–2 Makkabeeën + 27 NT) |
| **6. Gebedenboek (`PrayerBook`)** | **Horologion** (`prayers_eo.json`): Ochtendgebed, Eerste/Derde/Zesde/Negende Uur, Vespers, Completen | **Agpeya / Shehimo** (`prayers_oo.json`): Prime (6:00), Terce (9:00), Sext (12:00), None (15:00), Vespers, Compline, Midnight, Veil | **Getijdenboek / Brevier** (`prayers_ca.json`): Lauden, Terts, Sext, Noon, Vespers, Dagsluiting (Completen) |
| **7. Rozenkrans (`RosaryScreen`)** | Verborgen (gebruikt het **Chotki Gebedssnoer**) | Verborgen (gebruikt het **Chotki Gebedssnoer**) | **Zichtbaar** op zowel Home als Gebed-tabblad (met de 4 geheimen: Blijde, Droevige, Glorievolle, Lichtende) |
| **8. Heilige van de Dag & Kalender** | Byzantijns Menologion (`saints_unified.json` → `byzantine`) + Juliaanse ("oude stijl") secundaire datum | Koptisch Synaxarium (`saints_unified.json` → `coptic`) + Koptische Anno Martyrum datum (`13 maanden: Thoot, Paopi, Hathor, Kiahk...`) | Romeins Martyrologium (`saints_unified.json` → `roman`) + Gregoriaanse kalender |
| **9. Paasberekening (Computus)** | Juliaanse Pascha-berekening ( +13 dagen offset naar Gregoriaans) + `"Christus is opgestaan! Waarlijk opgestaan!"` | Zelfde Oosterse Pascha-datum + 55-daagse Grote Vasten + 3-daagse Vasten van Nineve | Gregoriaanse Paasberekening + Aswoensdag, Veertigdagentijd, Advent |
| **10. Vasten- & Feestdag-indicator op Home** | Grote Vasten (48d), Kerstvasten (15 nov–24 dec), Moeder Gods-vasten (1–14 aug), Woensdag/Vrijdag-vasten, Lichte Week | Vasten van Nineve (3d), Grote Vasten (55d), Kiahk Kerstvasten, Woensdag/Vrijdag-vasten, Heilige 50 Dagen | Aswoensdag & Goede Vrijdag, Veertigdagentijd, Advent, Vrijdag-onthoudingsdag |
| **11. Oecumenische Concilies (`councils.json`)** | **7 Oecumenische Concilies** (Nicea I t/m Nicea II, 325–787) | **3 Oecumenische Concilies** (Nicea I 325, Constantinopel I 381, Efeze 431) | **21 Concilies** (Nicea I t/m Vaticanum II) |
| **12. Kerkvaders & Kerkleiders (`church.json`)** | Filtert op vaders/leiders erkend door `EASTERN_ORTHODOX` | Filtert op vaders/leiders erkend door `ORIENTAL_ORTHODOX` (bijv. Cyrillus van Alexandrië, Severus van Antiochië, Paus Tawadros II) | Filtert op vaders/leiders erkend door `CATHOLIC` |
| **13. Gebedsherinneringen (`HourReminder`)** | Worden bij traditiewissel automatisch gereset naar de standaarduren van de nieuw gekozen traditie | Worden gereset naar Agpeya-standaarduren | Worden gereset naar Brevier-standaarduren |

---

## 2. Openingsanimatie & Traditie-Wissel Animatie (`AgapeStartupIntroOverlay`)

De app heeft een serene, liturgische openingsanimatie (`AgapeRoot.kt`).
**Wanneer speelt deze animatie af?**
1. Bij het **opstarten van de app** (`showStartupIntro = true`).
2. **Elke keer dat de gebruiker van traditie wisselt** (bijv. van Oosters-Orthodox naar Oriëntaals-Orthodox of Katholiek). In SwiftUI kun je dit doen met `.onChange(of: settings.tradition)`.

### Exact verloop van de animatie (tijdlijn):
- **Achtergrond**: Volledig scherm (`ignoresSafeArea()`) in `colors.background` met een subtiele radiale gloed in het midden (`RadialGradient` van `colors.accent.opacity(0.12)` naar `.clear`).
- **Stap 0 (`0 ms`)**: Scherm is leeg.
- **Stap 1 (`na 80 ms`)**: Het **kruis van de gekozen traditie** (`☦`, `☩` of `✝`, `32pt`, kleur `colors.accent`) verschijnt met een fade-in (`320 ms`, easeOut) en schaalt van `0.82` naar `1.0` (`360 ms`).
- **Stap 2 t/m 6 (`elke 115 ms een letter`)**: De 5 letters **`A` `G` `A` `P` `E`** (`38pt`, SemiBold, display-serif font, `12pt` tussenruimte, kleur `colors.ink`) verschijnen één voor één van links naar rechts. Elke letter animeert van `opacity: 0, offsetY: +18pt` naar `opacity: 1, offsetY: 0` in `280–300 ms`.
- **Stap 7 (`na nog 120 ms`)**: Onder de letters verschijnt een dunne gouden lijn (`breedte 48pt, hoogte 1pt, kleur colors.accent.opacity(0.6)`) en daaronder in hoofdletters (`11.5pt`, letterSpacing `2.2pt`, kleur `colors.inkMuted`):
  - Oosters-Orthodox: `"OOSTERS-ORTHODOX · GOD IS LIEFDE"` (EN: `"EASTERN ORTHODOX · GOD IS LOVE"`)
  - Oriëntaals-Orthodox: `"ORIËNTAALS-ORTHODOX · GOD IS LIEFDE"` (EN: `"ORIENTAL ORTHODOX · GOD IS LOVE"`)
  - Rooms-Katholiek: `"ROOMS-KATHOLIEK · GOD IS LIEFDE"` (EN: `"ROMAN CATHOLIC · GOD IS LOVE"`)
  - Nog geen traditie (eerste keer openen): `"GOD IS LIEFDE · 1 JOHANNES 4:16"`
- **Einde (`na 520 ms`)**: De hele overlay fadet zachtjes uit (`450 ms`) naar de app. Als de gebruiker tijdens de animatie op het scherm tikt, sluit de overlay direct.

---

## 3. Design System: Drie Visuele Stijlen (`VisualStyle`) & Kleurpaletten (`Palettes`)

De gebruiker kan kiezen uit **3 visuele stijlen** en **3 thema-modi** (`SYSTEM`, `LIGHT`, `DARK`). Geen enkel scherm gebruikt hardgecodeerde kleuren; alles leest uit `AgapeColors`.

### Kleur-tokens (`AgapeColors`)
Elke stijl heeft exact deze tokens in zowel Light als Dark mode:

#### Stijl 1: `NAVY_CREAM` (Modern Navy + Cream — standaard)
- **Light Mode**:
  - `background`: `#FAF6EE` (warme crème)
  - `surface`: `#FFFDF8`
  - `surfaceRaised`: `#FFFFFF`
  - `surfaceSunken`: `#F1EADC`
  - `ink` (primaire tekst): `#12203D` (diep marineblauw)
  - `inkMuted` (secundaire tekst): `#4B5670`
  - `inkFaint` (subtiele tekst): `#878EA0`
  - `primary`: `#14244A` | `onPrimary`: `#FAF6EE`
  - `accent` (goud): `#B08A3E` | `onAccent`: `#FFFDF8`
  - `line` (randen/dividers): `#E6DCC8`
  - `danger`: `#A23B3B`
  - `highlights` (4 Bijbel-markeerkleuren): `["#66E8C77A", "#5591B4D9", "#55A9C9A0", "#55E3A6A0"]`
- **Dark Mode**:
  - `background`: `#0A0F19`
  - `surface`: `#111827`
  - `surfaceRaised`: `#172036`
  - `surfaceSunken`: `#070B13`
  - `ink`: `#F1EBDD` | `inkMuted`: `#A9B0BF` | `inkFaint`: `#6B7387`
  - `primary`: `#E0B865` | `onPrimary`: `#0A0F19`
  - `accent`: `#E0B865` | `onAccent`: `#0A0F19`
  - `line`: `#243048` | `danger`: `#E08A84`

#### Stijl 2: `CLASSIC_SACRED` (Klassiek Sacraal — Perkament, Bourgondisch rood & Byzantijns goud)
- **Light Mode**:
  - `background`: `#F5EDDC` | `surface`: `#FBF6EA` | `surfaceRaised`: `#FFFAEF` | `surfaceSunken`: `#EDE2CA`
  - `ink`: `#2A1612` | `inkMuted`: `#684C40` | `inkFaint`: `#9C8573`
  - `primary`: `#6E1A26` (diep bourgondisch rood) | `onPrimary`: `#FBF6EA`
  - `accent`: `#AE8834` (oud goud) | `onAccent`: `#FFFAEF`
  - `line`: `#DCC9A2` | `danger`: `#9E2B2B`
- **Dark Mode**:
  - `background`: `#110A0B` | `surface`: `#1C1214` | `surfaceRaised`: `#26191B` | `surfaceSunken`: `#0B0607`
  - `ink`: `#F3E7CF` | `inkMuted`: `#C4AE8F` | `inkFaint`: `#80695A`
  - `primary`: `#D6B062` | `onPrimary`: `#1C1214`
  - `accent`: `#CBA455` | `onAccent`: `#1C1214`
  - `line`: `#3A2725` | `danger`: `#E28A80`

#### Stijl 3: `WARM_TERRACOTTA` (Warm Terracotta — Woestijnzand, Terracotta & Zacht goud)
- **Light Mode**:
  - `background`: `#FAF1E7` | `surface`: `#FFFAF4` | `surfaceRaised`: `#FFFDF9` | `surfaceSunken`: `#F2E4D4`
  - `ink`: `#45291E` | `inkMuted`: `#7A5F51` | `inkFaint`: `#A8928A`
  - `primary`: `#BF5F3D` | `onPrimary`: `#FFF8F1`
  - `accent`: `#C6A05A` | `onAccent`: `#45291E`
  - `line`: `#ECDAC6` | `danger`: `#A63A2E`
- **Dark Mode**:
  - `background`: `#191310` | `surface`: `#231B17` | `surfaceRaised`: `#2D241F` | `surfaceSunken`: `#130E0C`
  - `ink`: `#F4E8DA` | `inkMuted`: `#BFAA99` | `inkFaint`: `#7E6E62`
  - `primary`: `#E39472` | `onPrimary`: `#231A15`
  - `accent`: `#D9B672` | `onAccent`: `#231A15`
  - `line`: `#3A2F28` | `danger`: `#E38F80`

### Typografie (`Type.kt`)
Er zijn 6 ingebouwde Google Fonts (`.ttf`):
- `Source Sans 3` (UI & labels)
- `Cormorant Garamond` (Display/koppen voor `NAVY_CREAM`)
- `Literata` (Bijbel- en gebedstekst voor `NAVY_CREAM`)
- `EB Garamond` (Display + Bijbeltekst voor `CLASSIC_SACRED`)
- `Cinzel` (Kicker/uppercase labels voor `CLASSIC_SACRED`)
- `Lora` (Display + Bijbeltekst voor `WARM_TERRACOTTA`)

De gebruiker heeft een **lettergrootte-schuifregelaar (`fontScale` van `0.85` tot `1.60`)** in Instellingen die alle `scripture`- en `heading`-formaten vermenigvuldigt.

---

## 4. Navigatiestructuur & De Zwevende Bottom Bar (`MainShell`)

De app gebruikt een **zwevende, pilvormige navigatiebalk (`BottomBar`)** onderaan het scherm met schaduw (`cornerRadius: 999`, achtergrond `surfaceRaised` in dark mode of `ink` in light mode met `0.94` opacity).

### De 5 Hoofd-tabbladen (met Gebed exact in het midden!):
1. **Home (`HomeRoute`)** — Icoon: Huis (`house` / `house.fill`)
2. **Bijbel (`BibleRoute`)** — Icoon: Open boek (`book` / `book.fill`)
3. **Gebed (`PrayerRoute`) — IN HET MIDDEN (positie 3 van 5)** — Icoon: Brandende kaars (`AgapeIcons.Candle`)
4. **Kalender (`CalendarRoute`)** — Icoon: Kalender (`calendar`)
5. **Zoeken (`SearchRoute`)** — Icoon: Vergrootglas (`magnifyingglass`)

> **Belangrijk detail**: Profiel/Activiteit/Instellingen (`MeRoute`) zit **niet** in de onderste balk, maar wordt geopend via het **tandwiel-icoon (`⚙`) rechtsboven op het Home-scherm** (of door op de traditie-badge linksboven op Home te tikken). Daarnaast staat rechtsboven op Home het **Info/Hulp-icoon (`?`)**.

---

## 5. Scherm-voor-Scherm Uitleg

### 5.1 Onboarding (`Onboarding.kt`)
Wanneer de app voor het eerst wordt geopend, doorloopt de gebruiker 5 stappen:
1. **Welkom (`WELCOME`)**: Taalwisselaar rechtsboven (`English` | `Nederlands`), gouden ornament, titel `AGAPE`, ondertitel en 3 kernpunten (Schrift, Gebed, Groei).
2. **Voorwaarden (`TERMS`)**: Overzicht van privacy & gebruiksvoorwaarden met leesbare sheets (`terms_en.md` / `terms_nl.md` en `privacy_en.md` / `privacy_nl.md`) en een verplichte checkbox.
3. **Kies je Kerk (`TRADITION`)**: 3 grote keuzekaarten (`Oosters-Orthodox`, `Oriëntaals-Orthodox`, `Rooms-Katholiek`) met uitleg wat elke traditie inhoudt.
4. **Kies je Stijl (`STYLE`)**: Live preview van `Modern Navy + Cream`, `Classic Sacred` en `Warm Terracotta` inclusief Light/Dark/System schakelaar. Terwijl je tikt verandert de hele app direct live van kleur en lettertype.
5. **Account (`ACCOUNT`)**: Inloggen of registreren via e-mail/wachtwoord of **Log in met Google** (via Supabase Auth, of lokale offline-accountmodus als Supabase niet geconfigureerd is).

---

### 5.2 Startscherm (`HomeScreen.kt`)
Het startscherm heeft **geen** weekkalender-strip meer, maar een rustige, eerbiedige opbouw van boven naar beneden:

1. **Hero Backdrop (bovenste gedeelte met dagelijkse kunst/kerkfoto)**:
   - De achtergrondfoto komt uit `Backgrounds.forDate(today, tradition)` (specifiek gefilterd op de gekozen kerkelijke traditie!) met een donkere verloop-overlay zodat de crèmekleurige tekst (`#F4EEE3`) altijd perfect leesbaar is.
   - **Bovenste balk (hoog geplaatst, direct onder de statusbar)**:
     - **Links**: Klikbare **Traditie-pil** met het kruis van de traditie (`☦`, `☩` of `✝` in goud) + de naam van de traditie (bijv. `☦ Oosters-Orthodox`).
     - **Rechts**: Twee ronde knoppen (`46x46pt`) met subtiele rand: **Info/Hulp (`?`)** en **Profiel & Instellingen (`⚙`)**.
   - **Vasten- en Feestdag-indicator badge (direct onder de bovenste balk)**:
     - Een klikbare pil die op basis van `LiturgicalCalendar.dayStatus(tradition, today)` aangeeft wat voor liturgische dag het vandaag is:
       - `✦ Feestdag · [Naam van het feest]` (op hoogfeesten en zondagen)
       - `🕯 Vastendag · [Naam van de vasten / Woensdag / Vrijdag]`
       - `☦ Vastenvrije periode · [Lichte Week / Vijftig Dagen]`
       - `• Liturgische dag · Geen vasten`
     - **Actie bij tikken**: Opent een dialoogvenster met de titel van de dag, de precieze vasten- of feestregel voor de gekozen traditie, en een knop om direct naar de **Kerkelijke Kalender** te gaan.
   - **Datum & Paasgroet**: Volledige gelokaliseerde datum + tijdens de Paastijd (40 dagen vanaf Pascha/Pasen) de gouden Paasgroet (*"Christus is opgestaan! Waarlijk, Hij is opgestaan!"*).
   - **Tekst van de Dag (Verse of the Day)**: Grote serif-quote uit de Bijbel (`verse_of_day.json`), met hoofdstuk/vers-verwijzing en twee knoppen: **Lezen** (opent het hoofdstuk in de Bijbellezer) en **Delen** (deelt de tekst via het systeem-deelmenu).
2. **Verder lezen (`Reading`)**: Toont waar de gebruiker gebleven was in de Bijbel (bijv. *"Matteüs 5"*) of *"Begin met lezen"*.
3. **Voortgang (`Progress`)**: 3 statistieken naast elkaar:
   - **Dagen op rij (`Streak`)**
   - **Gelezen hoofdstukken (`X / totaal aantal hoofdstukken in canon`)**
   - **Gebeden uren (`Hours prayed`)**
4. **Lezingen van de Dag (`ReadingsCard`)**: Uitklapbare kaart met de dagelijkse Schriftlezingen (Wet & Profeten, Psalm, Evangelie, Apostel). Tikken op een lezing opent direct de Bijbellezer op dat vers.
5. **Interactief Gebedssnoer (`Chotki`) kaart**: Snelle toegang tot het Jezusgebed met het Chotki-embleem.
6. **De Heilige Rozenkrans (alleen zichtbaar als `tradition == CATHOLIC`)**: Toont de geheimen van de betreffende weekdag (Blijde, Droevige, Glorievolle of Lichtende geheimen) en opent `RosaryScreen`.
7. **Leesplan (`Reading Plan`)**: Toont het actieve leesplan met voortgangsbalk en volgende dag, of nodigt uit om een leesplan te starten.
8. **Heilige van de Dag (`SaintOfDayCard`)**:
   - **Altijd met een echte heiligen-icoonfoto** in een boogvormige nis (`SaintIcon` met ronde bovenkant `topStart = width/2, topEnd = width/2`, gouden rand en subtiele schaduw).
   - Rechtsboven in de kaart zit de **Luister-knop (`SpeakerPill`)** die de naam en het levensverhaal van de heilige rustig voorleest via Text-to-Speech (`AVSpeechSynthesizer` op iOS).
   - Toont de naam van de hoofdheilige (of het hoogfeest), een samenvatting van het leven (tot 5 regels), en daaronder *"Ook herdacht"* met maximaal 3 andere heiligen van die dag + bronvermelding van het icoon.

---

### 5.3 Bijbel & Kerkelijke Bibliotheek (`BibleScreen.kt` & `ReaderScreen.kt`)
Het **Bijbel-tabblad** heeft twee sub-weergaven (of secties):
1. **De Heilige Schrift (`Bible`)**:
   - Gefilterd op de **canon van de gekozen traditie** (Oosters-Orthodox heeft 76 boeken incl. deuterocanonieke boeken zoals 1 Esdras, Tobit, Judit, Wijsheid, Sirach, Baruch, 1–3 Makkabeeën, Psalm 151; Katholiek 73 boeken; Oriëntaals-Orthodox 75+ boeken).
   - Oude Testament (`OT`) en Nieuwe Testament (`NT`) filter, zoekbalk voor boeken, en weergave van reeds gelezen hoofdstukken per boek.
2. **De Kerk & Traditie (`Church`)**:
   - **Artikelen** over theologie, liturgie, spiritualiteit en geschiedenis (`church.json`).
   - **Oecumenische Concilies (`CouncilsScreen` & `CouncilScreen`)**: Toont alleen de concilies die door de gekozen traditie worden erkend (3 voor Oriëntaals-Orthodox, 7 voor Oosters-Orthodox, 21 voor Katholiek), inclusief canons, geloofsbelijdenissen (`council_texts.json`) en een **kennisquiz per concilie**.
   - **Kerkvaders (`FathersScreen` & `FatherScreen`)**: Biografieën, feestdagen en alle Bijbelcommentaren van die specifieke Kerkvader (bijv. Johannes Chrysostomus, Athanasius, Augustinus, Ephrem de Syriër, Cyrillus van Alexandrië).
   - **Kerkleiders (`LeadersScreen`)**: Huidige patriarchen, pausen en aartsbisschoppen van de gekozen traditie.
   - **Leesplannen (`PlansScreen`)**: Meerdaagse Bijbelleesplannen (`plans.json`).

#### De Bijbellezer (`ReaderScreen.kt`):
- Haalt verzen op uit de offline SQLite-database (`bible.db` / `bible.db.gz`):
  - **Nederlands**: Statenvertaling (`translation = 'sv'`)
  - **Engels**: World English Bible (`translation = 'web'`) + Septuaginta/KJV voor deuterocanonieke boeken.
- **Interactie per vers**:
  - Tikken op één of meerdere verzen opent een actiebalk om te **markeren** (in 4 themakleuren), **notities toe te voegen**, **tekst te kopiëren/delen**, of het **commentaar van de Kerkvaders & kruisverwijzingen (`VerseSheet`)** voor dat specifieke vers te bekijken.
- **Hoofdstuk-uitleg & Kerkvaders (`ChapterInfo`)**:
  - Bovenaan of onderaan elk hoofdstuk kun je de hoofdstukgids (`commentary/<BOOK>.json` + `chapter_guides` tabel) en de patristische aantekeningen (`father_notes` & `father_quotes`) openen.
- **Hoofdstuk-voltooiing & Quiz**:
  - Onderaan het hoofdstuk kun je het hoofdstuk markeren als gelezen en een **Quiz over het boek/hoofdstuk (`QuizScreen`)** maken uit `quizzes.json`.

---

### 5.4 Gebed (`PrayerScreen.kt`, `HourScreen.kt`, `ChotkiScreen.kt`, `RosaryScreen.kt`)
Het middelste tabblad **Gebed** is het liturgische hart van de app:

1. **Huidig Gebedsuur Hero-kaart (`PrayerClock`)**:
   - Berekent op basis van het huidige tijdstip (`LocalTime.now()`) welk canoniek gebedsuur **nu** aan de beurt is (en welk uur daarna volgt).
   - De kaart heeft een dynamische lucht-gradiënt (`daySky`: ochtendgloren, daglicht, schemering of nachthemel) en een geanimeerde kaarsvlam (`Flame`).
2. **Interactief Gebedssnoer — Chotki (`ChotkiScreen.kt`)**:
   - Keuze uit **33 knopen** (de aardse levensjaren van Christus), **50 knopen** of **100 knopen**.
   - Tekent een cirkelvormig gebedssnoer op een `Canvas` met wollen knopen, gouden tussenkralen (elke 11 knopen bij 33, of elke 10 knopen bij 50/100) en een geweven kruis onderaan.
   - Elke tik op de cirkel (of op de knop onderaan) verzet de teller met 1 knoop en geeft **voelbare haptische feedback** (`UIImpactFeedbackGenerator` op iOS: lichte tik bij gewone knoop, zwaardere tik bij tussenkraal en voltooiing).
   - Toont het **Jezusgebed** in NL/EN (*"Heer Jezus Christus, Zoon van God, ontferm U over mij, zondaar"*) én in het Grieks (*"Κύριε Ἰησοῦ Χριστέ, Υἱὲ τοῦ Θεοῦ, ἐλέησόν με τὸν ἁμαρτωλόν"*).
   - Op de gouden tussenkralen verschijnt het gebed tot de Moeder Gods (*"Allerheiligste Moeder Gods, red ons"*).
   - Bevat een **Luister**-knop én een schakelaar **"Spreek bij elke knoop: Aan/Uit"** zodat de stem het Jezusgebed bij elke knoop automatisch kan uitspreken.
3. **De Heilige Rozenkrans (`RosaryScreen.kt`)**:
   - Volledig interactieve Rozenkrans met 5 tientjes, kruisteken, Apostolische Geloofsbelijdenis, Onze Vader, Weesgegroet, Eer aan de Vader, Fatima-gebed, Salve Regina en bijbehorende Schriftteksten per geheim.
4. **Persoonlijke Gebedsintenties & Namenlijst — Diptycha (`PrayerIntention`)**:
   - Op het Gebed-scherm staat een kaart **"Persoonlijke gebedsintenties & namen"**.
   - Met de knop **+ Toevoegen** opent een dialoogvenster waarin de gebruiker een naam of intentie toevoegt onder één van de 3 liturgische categorieën:
     - `LIVING`: *Voor de levenden & zieken*
     - `DEPARTED`: *Voor de ontslapenen*
     - `THANKSGIVING`: *Dankzegging & intenties*
   - **Integratie in de Gebedsuren (`HourScreen.kt`)**: Zodra de gebruiker een gebedsuur opent (bijv. Ochtendgebed, Vespers of Completen), verschijnt vóór het afsluitende *"Amen"* automatisch een kaart met al zijn persoonlijke namen en intenties per categorie (*"Gedenk, o Heer, uw dienaren en verhoor onze gebeden..."*).
5. **De Vaste Gebedsuren (`book.hours`) & Extra Diensten (`book.extras`)**:
   - Lijst van alle gebedsuren van de gekozen traditie met het ingestelde tijdstip en een bel-icoontje om de herinnering per uur met één tik aan of uit te zetten.
   - Binnen een gebedsuur (`HourScreen.kt`):
     - **Audio-voorleesfunctie (`SpeakerButton` & `SpeakerPill`)** bovenaan om het volledige gebedsuur te laten voorlezen.
     - Voortgangslijn (`ProgressLine`) die meeloopt tijdens het scrollen.
     - Ondersteuning voor 6 sectietypes (`PrayerSection.Kind`): `text`, `rubric` (cursieve liturgische aanwijzingen in goud/accentkleur), `heading` (met ornament), `scripture` (haalt psalmen/lezingen uit de Bijbeldatabase), `prayer` (verwijst naar een los gebed) en **`counter`** (een interactieve cirkel-teller voor bijv. 12× of 40× *Kyrie eleison* met haptische feedback!).
     - Onderaan de knop **"Amen"**, die het gebedsuur registreert in de gebruikersstatistieken (`hoursPrayed++`, dagelijkse streak).

---

### 5.5 Kerkelijke Kalender (`CalendarScreen.kt`)
1. **Drievoudige kalenderweergave (`ThreeDates`)**:
   - Toont voor elke geselecteerde dag gelijktijdig:
     1. **Gregoriaanse datum** (gemarkeerd als hoofd-datum voor Katholiek)
     2. **Juliaanse datum — oude stijl** (`datum - 13 dagen`, gemarkeerd voor Oosters-Orthodox)
     3. **Koptische datum — Anno Martyrum** (berekend vanaf het Diocletiaanse martelarenjaar 284 n.Chr. met de 13 Koptische maanden: *Thoot, Paopi, Hathor, Kiahk, Tobi, Amshir, Baramhat, Baramoeda, Bashans, Paoni, Epip, Mesra, Nasie*, gemarkeerd voor Oriëntaals-Orthodox).
2. **Maandoverzicht**:
   - Toont elke dag van de maand (en voor Oriëntaals-Orthodox ook het Koptische dagnummer in elke cel).
   - Gouden stip (`accent`) onder feestdagen; primaire stip onder dagen waarop de gebruiker zelf gelezen/gebeden heeft.
3. **Dagdetails (`DayDetails`)**:
   - Toont eventuele verschuifbare of vaste hoogfeesten (`LiturgicalCalendar.feasts(tradition, day)`).
   - Toont de **hoofdheilige van de dag met icoonfoto (`SaintIcon`)** en uitklapbare biografie (`LifeText`).
   - Toont alle **overige herdachte heiligen** van die dag (eveneens met icoonfoto) en de **Schriftlezingen van die dag** (`DayReadings`).

---

### 5.6 Garantie dat élke Heilige een Icoonfoto heeft (`SaintCalendar.kt` & `SaintOfDay.kt`)
De app bevat **169 authentieke heiligen-iconen** in `assets/icons/` (gekoppeld via `assets/content/icons.json`).
Om te garanderen dat er **nooit** een lege plek is bij *Saint of the Day* of in de kalender:
1. Eerst zoekt `ContentRepository.iconFor(names)` op reguliere expressies in `icons.json` naar een specifieke icoon van die heilige of dat feest (`fallback = false`).
2. Als er geen specifieke icoon voor een minder bekende martelaar of bisschop is, zoekt hij in de algemene iconen (`fallback = true`).
3. Als zelfs dat geen match geeft, kiest `guaranteeIcon(date)` deterministisch (op basis van `date.toEpochDay()`) een passende icoon uit de bundel. Zo heeft **elke dag van het jaar 100% gegarandeerd een echte foto**.

---

### 5.7 Meldingensysteem & Kerkklok (`Reminders.kt` & `SettingsScreen.kt`)
De gebruiker kan zijn dagelijkse gebedsherinneringen tot in detail instellen onder **Instellingen → Meldingen**:

1. **Vaste Gebedsuren aanpassen (`HourReminder`)**:
   - Elk canoniek gebedsuur van de huidige traditie heeft een eigen aan/uit-schakelaar (`Switch`) én een **klikbare tijd-pil (`HH:mm`)**.
   - Tikken op de tijd-pil opent een **TimePicker-dialoog** waarmee de gebruiker het tijdstip van dat specifieke gebedsuur naar wens kan verzetten. Wordt opgeslagen als `"hourId@HH:mm"`.
2. **Meerdere Vrije Herinneringstijden per dag (`CustomReminder`)**:
   - De gebruiker kan met de knop **`+ Eigen tijdstip toevoegen`** onbeperkt extra dagelijkse herinneringen aanmaken.
   - In het dialoogvenster kiest de gebruiker een **eigen tekst/label** (bijv. *"Ochtendgebed voor werk"* of *"Avondgebed"*) en een **tijdstip (`HH:mm`)**.
   - Bestaande eigen tijden kunnen worden bewerkt door erop te tikken of verwijderd met het prullenbak-icoon. Wordt opgeslagen als `"id|HH:mm|label"`.
3. **Kerkklok-geluid (`church_bell.wav`), Trilling & Testknop**:
   - Het audiobestand `church_bell.wav` wordt gebruikt als meldingsgeluid én direct afgespeeld met een dubbele klok-trilling (`[0ms, 500ms, 250ms, 500ms]`).
   - In Instellingen staat een prominente knop **"Test kerkklok & melding"**: wanneer de gebruiker hierop tikt, luidt direct de kerkklok, trilt de telefoon en verschijnt er een test-gebedsherinnering.
   - *Voor iOS (Swift)*: Gebruik `UNUserNotificationCenter` met `UNCalendarNotificationTrigger(dateMatching: repeats: true)` en `UNNotificationSound(named: UNNotificationSoundName("church_bell.wav"))`, plus `AVAudioPlayer` en `UINotificationFeedbackGenerator` voor de testknop.

---

## 6. Overzicht van Alle Data- & Asset-bestanden (Direct herbruikbaar in iOS!)

Alle inhoudelijke bestanden in dit project zijn platform-onafhankelijk (JSON, SQLite, JPG/PNG, WAV, TTF, Markdown) en kunnen **1-op-1 in je Xcode iOS-project (`Bundle.main`) worden gesleept**:

1. **`bible.db.gz` (of uitgepakt `bible.db`)**:
   - SQLite database met de tabellen:
     - `verses (translation TEXT, book TEXT, chapter INT, verse INT, text TEXT)` (`'sv'` = Nederlands Statenvertaling, `'web'` = Engels World English Bible)
     - `cross_refs (book, chapter, verse, to_book, to_chapter, to_verse, to_verse_end, votes)`
     - `chapter_guides (book, chapter, lang, title, summary, context, themes, related)`
     - `father_notes (book, chapter, father, work, date, verse_from, verse_to, note_en, note_nl, source, sort)`
     - `father_quotes (book, chapter, verse, verse_end, father, year, source, text)`
2. **`app/src/main/assets/content/`**:
   - `books.json` — Alle Bijbelboeken met hun volgorde, testament, naam in NL/EN, aantal hoofdstukken en in welke tradities (`EASTERN_ORTHODOX`, `ORIENTAL_ORTHODOX`, `CATHOLIC`) ze tot de canon behoren.
   - `prayers_eo.json`, `prayers_oo.json`, `prayers_ca.json` — De volledige Getijdenboeken voor de drie tradities.
   - `prayers_extra.json` — Losse gebeden (Onze Vader, Geloofsbelijdenis, Trisagion, Gebed van St. Ephrem, Ochtend-/Avondgebeden).
   - `saints_unified.json` — Het complete heiligen- en lectionarium-bestand voor alle 366 dagen van het jaar in zowel de Byzantijnse, Koptische als Romeinse kalender (inclusief biografieën in NL & EN).
   - `icons.json` — Koppeling tussen heiligennamen/feesten en de 169 icoon-afbeeldingen in `assets/icons/`.
   - `councils.json` & `council_texts.json` — Alle concilies, hun geschiedenis, canons, geloofsbelijdenissen en quizvragen.
   - `church.json` — Artikelen over de Kerk, de Kerkvaders en huidige kerkleiders per traditie.
   - `commentary/<BOOK>.json` — Uitgebreide hoofdstukcommentaren in NL & EN.
   - `verse_notes.json` — Studie-aantekeningen per vers (algemeen én specifiek per traditie).
   - `verse_of_day.json` — Dagelijkse kernteksten per traditie.
   - `plans.json` & `quizzes.json` — Leesplannen en duizenden quizvragen per Bijbelboek en hoofdstuk.
3. **`app/src/main/assets/icons/`**: 169 heiligen- en feest-iconen (`.jpg`).
4. **`app/src/main/assets/backgrounds/`**: `backgrounds.json` + 19 sfeervolle kerkinterieurs voor het Home-scherm.
5. **`app/src/main/res/raw/church_bell.wav`**: Het kerkklokgeluid voor de gebedsherinneringen.
6. **`app/src/main/res/font/`**: De 6 lettertypen (`cormorant_garamond.ttf`, `literata.ttf`, `eb_garamond.ttf`, `cinzel.ttf`, `lora.ttf`, `source_sans.ttf`).

---

## 7. Kalender-wiskunde (voor directe vertaling naar Swift)

### 7.1 Oosters / Koptisch Pascha (Juliaanse Computus naar Gregoriaanse datum)
```swift
func orthodoxPascha(year: Int) -> DateComponents {
    let a = year % 4
    let b = year % 7
    let c = year % 19
    let d = (19 * c + 15) % 30
    let e = (2 * a + 4 * b - d + 34) % 7
    let month = (d + e + 114) / 31
    let day = ((d + e + 114) % 31) + 1
    // Dit is de Juliaanse datum; tel 13 dagen (JULIAN_OFFSET voor 1900-2099) op voor de Gregoriaanse kalender.
    return addDays(year: year, month: month, day: day, offset: 13)
}
```

### 7.2 Koptische Datum (`CopticDate` — Anno Martyrum)
- **Epoch**: Juliaans Dagnummer (JDN) `1825030` (1 Thoot, jaar 1 van de Martelaren = 29 augustus 284 n.Chr. Juliaans).
- **Formule**:
  - `year = Int((4 * (jdn - 1825030) + 1463) / 1461)`
  - `startOfYear = 1825030 + 365 * (year - 1) + (year / 4)`
  - `dayOfYear = Int(jdn - startOfYear)`
  - `month = dayOfYear / 30 + 1` (1 t/m 13, waarbij maand 13 *Nasie* de kleine maand van 5 of 6 dagen is)
  - `day = dayOfYear % 30 + 1`

---

## 8. Kant-en-klare Prompt voor je iOS / SwiftUI AI

Wanneer je dit bestand aan een AI geeft om de iOS-app te bouwen, kun je deze instructie meesturen:

> *"Bouw de volledige iOS-versie van de AGAPE-app in Swift 6 en SwiftUI (iOS 17+) op basis van dit `AGAPE_IOS_BLUEPRINT.md`-document. Gebruik exact dezelfde drie tradities (`EASTERN_ORTHODOX`, `ORIENTAL_ORTHODOX`, `CATHOLIC`) met hun eigen kruis-symbolen (`☦`, `☩`, `✝`), de letter-voor-letter `A G A P E` openingsanimatie bij opstarten én bij het wisselen van traditie, de 3 kleurpaletten (`NAVY_CREAM`, `CLASSIC_SACRED`, `WARM_TERRACOTTA`) in Light en Dark mode, de zwevende 5-tabbladen navigatiebalk met Gebed in het midden, de Vasten- en Feestdag-indicator op Home, de Heilige van de Dag met boogvormig icoonframe en Text-to-Speech (`AVSpeechSynthesizer`), het interactieve Chotki-gebedssnoer (33/50/100 knopen met haptics), de persoonlijke gebedsintenties (Diptycha) en de lokale gebedsherinneringen met `church_bell.wav` en testknop. Gebruik de meegeleverde JSON-bestanden en `bible.db` SQLite-database uit de app-bundle."*
