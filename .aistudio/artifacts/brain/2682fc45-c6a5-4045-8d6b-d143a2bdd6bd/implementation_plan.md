# Implementatieplan — AGAPE Verbeteringen

## 1. Sfeervolle & Realistische Achtergronden (Vers van de Dag)
De huidige achtergronden achter de Vers van de Dag bevatten computergetekende geometrische vormen (`art_sanctuary`, `art_dome`, `art_candles`), een plaat met witte randen en drukke mozaïekgezichten direct achter de tekst.

### Wat we aanpassen
- **Nieuwe fotorealistische achtergronden**: We vervangen de storende en kunstmatige achtergronden in `assets/backgrounds/` door een rustgevende mix van:
  - **Sfeervolle heiligdommen**: Warm kaarslicht en gouden zonnestralen door hoge kerkramen in serene Oosters-Orthodoxe, Oriëntaals-Orthodoxe en Rooms-Katholieke kerken.
  - **Rustgevende Bijbelse natuur bij zonsopgang**: Gouden ochtendlicht over bergen, een stille olijfboomgaard en kalm water bij dageraad.
- **Optimale leesbaarheid**: De composities hebben rustige, warme donkere tinten en zachte lichtinval zodat de witte tekst van de Vers van de Dag er altijd helder en rustig uitspringt.

---

## 2. Volledige Bijbelzoekfunctie & De 4 Evangeliën
Op dit moment verschijnen de 4 Evangeliën (**Matteüs, Marcus, Lucas, Johannes**) vaak niet bij het zoeken door drie oorzaken:
1. De SQLite-zoekfunctie (`searchVerses`) doorzoekt de tabel alfabetisch op 3-letterige boekcode (`1CH`, `1CO`, `1KI`, `GEN`, ...) en stopt na 80 treffers, nog vóórdat `MAT`, `MRK`, `LUK` en `JHN` worden bereikt.
2. Zoektermen zonder trema of met alternatieve spelling (zoals `"Matteus"`, `"Mattheus"`, `"Markus"`, `"Lukas"`, of `"Evangelie"` / `"Evangeliën"`) matchen niet met de exacte boeknaam.
3. In het tabblad *Alles* van het zoekscherm worden slechts de eerste 6 Bijbeltreffers getoond.

### Wat we aanpassen
- **Slimme boek- en evangelieherkenning (`SearchScreen.kt` & `BibleRepository.kt`)**:
  - Diakritische tekens (zoals `ë` in *Matteüs* en *Korintiërs*) worden genegeerd tijdens het zoeken.
  - Ondersteuning voor alle gangbare Nederlandse en Engelse spellingen (`Matteüs` / `Mattheüs` / `Matteus` / `Mattheus`, `Marcus` / `Markus`, `Lucas` / `Lukas`, `Johannes` / `Jan`).
  - Wie zoekt op `"Evangelie"`, `"Evangeliën"` of `"Gospel"` krijgt direct alle 4 de Evangeliën bovenaan te zien.
- **Verbeterde vers-zoekopdracht (`BibleRepository.kt`)**:
  - `searchVerses` doorzoekt de hele Bijbel en sorteert treffers op canonieke volgorde met voorrang voor de 4 Evangeliën en bekende passages, in plaats van alfabetisch op `1CH`/`1CO` af te kappen.
  - De limiet en groepering in `SearchScreen.kt` worden verruimd zodat treffers uit zowel de Evangeliën, het Nieuwe Testament als het Oude Testament direct zichtbaar zijn.
- **Directe Evangelie-knop in het Bijbel-tabblad (`BibleScreen.kt`)**:
  - Naast *Oude Testament* en *Nieuwe Testament* voegen we een snelle filterknop **Evangeliën** en een direct zoekveld toe.

---

## 3. Vernieuwde AGAPE-Animatie (Gouden Lichtstralen & Heilige Halo)
Bij het opstarten van de app verschijnt eerst kort het Bijbel-icoon, waarna de **AGAPE**-animatie volgt; binnen de app zelf (zoals bij het wisselen van kerktraditie) verschijnt uitsluitend de **AGAPE**-animatie met het bijbehorende kruis.

### Wat we aanpassen
- **Onderscheid tussen eerste app-start en in-app animatie (`MainActivity.kt` & `AgapeRoot.kt`)**:
  - **Bij het openen van de app**: Het systeem-opstartscherm toont kort het gouden Bijbel-embleem en vloeit daarna naadloos over in het **AGAPE**-introductiescherm.
  - **Binnen de app (bijv. bij het wisselen van kerk in Instellingen of Onboarding)**: Alleen de **AGAPE**-animatie met het kruis van de gekozen kerktraditie wordt getoond (zonder het Bijbel-icoon).
- **Nieuwe animatiestijl (`AgapeStartupIntroOverlay`)**:
  - **Heilige Halo & Gouden Lichtstralen**: Achter het traditiekruis (Rooms-Katholiek ✝, Oosters-Orthodox ☦, Oriëntaals-Orthodox ☩) ontvouwt zich een zachte gouden halo met subtiel draaiende lichtstralen.
  - **Vloeiende Letter-onthulling met Glans**: De letters **A · G · A · P · E** verschijnen met een vloeiende schaal- en lichtovergang, gevolgd door een subtiele gouden glans (*shimmer*) over de tekst en de naam van de gekozen kerktraditie.
