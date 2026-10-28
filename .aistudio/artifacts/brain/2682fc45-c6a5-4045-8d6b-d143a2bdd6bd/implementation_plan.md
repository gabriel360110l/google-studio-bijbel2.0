# Implementatieplan: Natuurlijke Mannelijke Voorleesstem voor Bijbel & Gebeden

## Doel en Gebruikerservaring
De audiobeleving in **AGAPE** wordt vernieuwd zodat zowel **Bijbelhoofdstukken** als **alle dagelijkse gebeden en gebedsuren (getijden)** worden voorgelezen door een **vaste warme, rustige mannelijke vertelstem** met een menselijke intonatie, natuurlijke adempauzes en live tekst-tracking.

---

## Wat we gaan bouwen

### 1. Natuurlijke Mannelijke Stem-Engine (`AgapeNaturalVoice`)
We bouwen een centrale stem-engine (`ui/components/AgapeSpeaker.kt`) die voor zowel de Bijbel als de Gebeden zorgt voor een zo menselijk mogelijke voordracht:
- **Selectie van de beste neurale mannenstem**:
  - Verbindt bij voorkeur met de neurale spraakengine (`com.google.android.tts`) en doorzoekt alle beschikbare stemmen voor Nederlands (`nl-NL` / `nl-BE`) en Engels (`en-US` / `en-GB`).
  - Selecteert automatisch de hoogste kwaliteit **mannelijke neurale vertelstem** (`QUALITY_VERY_HIGH` / `QUALITY_HIGH`, zoals de warme Nederlandse mannenstemmen `nl-nl-x-dma` / `nl-nl-x-bma` en Engelse `en-us-x-iom` / `en-gb-x-rjs`), ondersteund door een warme bariton-afstemming (`pitch = 0.91f`) en rustige voordrachtsnelheid (`speechRate = 0.89f`).
- **Menselijke frase-opbouw & natuurlijke adempauzes**:
  - Teksten worden niet meer als één vlak blok opgelezen, maar opgedeeld in natuurlijke ademfrasen met SSML-prosodie en subtiele stiltes (`playSilentUtterance`) bij komma's (`220 ms`), dubbele punten en puntkomma's (`380 ms`), zinseinden (`550 ms`) en tussen verzen of gebedsstrofen (`750 ms`).
  - Liturgische woorden zoals *Κύριε ἐλέησον* (uitgesproken als *"Kyrie eleison"*) en *Amen* krijgen een eerbiedige cadans en rustpauze voor en na het woord.

### 2. Bijbelhoofdstukken met Menselijke Voordracht & Vers-Tracking (`ReaderExtras.kt` & `ReaderScreen.kt`)
- **Vernieuwde `ChapterAudioController`**:
  - Gebruikt de nieuwe warme mannelijke vertelstem en voegt natuurlijke adempauzes toe tussen zinsdelen en tussen opeenvolgende verzen.
  - **Live Vers-Tracking & Auto-Scroll**: Het vers dat op dat moment wordt voorgelezen krijgt een zachte gouden markering en het scherm scrollt rustig mee met de verteller.
- **Uitgebreide Audio-Balk (`ChapterAudioBar`)**:
  - Toont het huidige vers, een visuele stem-indicator, knoppen voor Vorige vers / Pauze & Hervatten / Volgende vers / Stop, én een **Spreeksnelheid-knop** waarmee je direct kunt schakelen tussen:
    - **Rustig (`0.82×`)** — meditatief tempo
    - **Natuurlijk (`0.90×`, standaard)** — warme, menselijke vertelsnelheid
    - **Normaal (`1.00×`)** — vlot voorleestempo

### 3. Dagelijkse Gebeden & Gebedsuren met Paragraaf-Tracking (`HourScreen.kt`, `PrayerScreen.kt`, `AgapeSpeaker.kt`)
- **Interactieve Gebeds-Audio Speler (`PrayerAudioBar`)**:
  - In zowel de **Gebedsuren / Getijden (`HourScreen`)** als **Individuele Dagelijkse Gebeden (`PrayerTextScreen`)** verschijnt tijdens het luisteren een stijlvolle audiospeler onderin het scherm.
  - **Live Paragraaf- & Sectie-Tracking**: De alinea of gebedssectie die op dat moment wordt uitgesproken licht subtiel op en scrollt automatisch in beeld, met een natuurlijke adempauze tussen elke sectie en elk gebed.
  - Bevat bediening voor **Pauzeren/Hervatten**, **Vorige/Volgende gebedssectie**, **Stop** en de **Spreeksnelheid-schakelaar** (`Rustig` · `Natuurlijk` · `Normaal`).
- **Direct Luisteren vanaf het Gebedsscherm (`PrayerScreen.kt`)**:
  - Op de kaart van het huidige gebedsuur en bij de dagelijkse gebeden kun je direct het voorlezen starten en volgen.

---

## Verificatie
- Controle van stemselectie, SSML/adempauzes en vers-/sectie-tracking in zowel het Nederlands als het Engels.
- Volledige compilatie- en foutcontrole met `compile_applet`.
