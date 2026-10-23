#!/usr/bin/env python3
"""
Ensures app/src/main/assets/bible/bible.db is populated with all Bible verses
(Dutch Statenvertaling 'sv' + English World English Bible 'web'), chapter guides,
Church Father notes/quotes, and cross-references.

Runs automatically before every Gradle build so container resets can never leave
an empty bible.db skeleton in the APK.
"""

import json
import os
import re
import sqlite3
import sys
import urllib.request
import zipfile

ROOT_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DB_PATH = os.path.join(ROOT_DIR, "app", "src", "main", "assets", "bible", "bible.db")

LEGACY_BIBLE_URL = "https://raw.githubusercontent.com/gabriel360110l/AGAPE-Android2.0/main/android/app/src/main/assets/bible.sqlite"
LEGACY_GUIDES_URL = "https://raw.githubusercontent.com/gabriel360110l/AGAPE-Android2.0/main/android/app/src/main/assets/guides.sqlite"
WEB_USFM_URL = "https://ebible.org/Scriptures/engwebp_usfm.zip"
CROSS_REFS_URL = "https://a.openbible.info/data/cross-references.zip"

LEGACY_TO_USFM = {
    "genesis": "GEN", "exodus": "EXO", "leviticus": "LEV", "numeri": "NUM",
    "deuteronomium": "DEU", "jozua": "JOS", "richteren": "JDG", "ruth": "RUT",
    "1samuel": "1SA", "2samuel": "2SA", "1koningen": "1KI", "2koningen": "2KI",
    "1kronieken": "1CH", "2kronieken": "2CH", "1esdras": "1ES", "ezra": "EZR",
    "nehemia": "NEH", "tobit": "TOB", "judith": "JDT", "esther": "EST",
    "ester-grieks": "ESG", "1maccabeen": "1MA", "2maccabeen": "2MA",
    "3maccabeen": "3MA", "psalmen": "PSA", "psalm151": "PS2",
    "gebed-manasse": "MAN", "job": "JOB", "spreuken": "PRO", "prediker": "ECC",
    "hooglied": "SNG", "wijsheid": "WIS", "sirach": "SIR", "jesaja": "ISA",
    "jeremia": "JER", "klaagliederen": "LAM", "baruch": "BAR", "ezechiel": "EZK",
    "daniel": "DAN", "gebed-azaria": "S3Y", "susanna": "SUS", "bel": "BEL",
    "hosea": "HOS", "joel": "JOL", "amos": "AMO", "obadja": "OBA",
    "jona": "JON", "micha": "MIC", "nahum": "NAM", "habakuk": "HAB",
    "sefania": "ZEP", "haggai": "HAG", "zacharia": "ZEC", "maleachi": "MAL",
    "matteus": "MAT", "marcus": "MRK", "lucas": "LUK", "johannes": "JHN",
    "handelingen": "ACT", "romeinen": "ROM", "1korintiers": "1CO",
    "2korintiers": "2CO", "galaten": "GAL", "efeziers": "EPH",
    "filippenzen": "PHP", "kolossenzen": "COL", "1thessalonicenzen": "1TH",
    "2thessalonicenzen": "2TH", "1timotheus": "1TI", "2timotheus": "2TI",
    "titus": "TIT", "filemon": "PHM", "hebreen": "HEB", "hebreeen": "HEB",
    "jakobus": "JAS", "1petrus": "1PE", "2petrus": "2PE", "1johannes": "1JN",
    "2johannes": "2JN", "3johannes": "3JN", "judas": "JUD", "openbaring": "REV",
}

OSIS_TO_USFM = {
    "Gen": "GEN", "Exod": "EXO", "Lev": "LEV", "Num": "NUM", "Deut": "DEU",
    "Josh": "JOS", "Judg": "JDG", "Ruth": "RUT", "1Sam": "1SA", "2Sam": "2SA",
    "1Kgs": "1KI", "2Kgs": "2KI", "1Chr": "1CH", "2Chr": "2CH", "Ezra": "EZR",
    "Neh": "NEH", "Esth": "EST", "Job": "JOB", "Ps": "PSA", "Prov": "PRO",
    "Eccl": "ECC", "Song": "SNG", "Isa": "ISA", "Jer": "JER", "Lam": "LAM",
    "Ezek": "EZK", "Dan": "DAN", "Hos": "HOS", "Joel": "JOL", "Amos": "AMO",
    "Obad": "OBA", "Jonah": "JON", "Mic": "MIC", "Nah": "NAM", "Hab": "HAB",
    "Zeph": "ZEP", "Hag": "HAG", "Zech": "ZEC", "Mal": "MAL", "Matt": "MAT",
    "Mark": "MRK", "Luke": "LUK", "John": "JHN", "Acts": "ACT", "Rom": "ROM",
    "1Cor": "1CO", "2Cor": "2CO", "Gal": "GAL", "Eph": "EPH", "Phil": "PHP",
    "Col": "COL", "1Thess": "1TH", "2Thess": "2TH", "1Tim": "1TI", "2Tim": "2TI",
    "Titus": "TIT", "Phlm": "PHM", "Heb": "HEB", "Jas": "JAS", "1Pet": "1PE",
    "2Pet": "2PE", "1John": "1JN", "2John": "2JN", "3John": "3JN", "Jude": "JUD",
    "Rev": "REV",
}


AGAPE3_BIBLE_DB_URL = "https://raw.githubusercontent.com/gabriel360110l/AGAPE-Android3/main/app/src/main/assets/bible/bible.db"


def is_db_populated(path: str) -> bool:
    if not os.path.exists(path) or os.path.getsize(path) < 30_000_000:
        return False
    try:
        conn = sqlite3.connect(path)
        count = conn.execute("SELECT COUNT(*) FROM verses").fetchone()[0]
        sample = conn.execute("SELECT text FROM verses WHERE translation = 'web' AND text LIKE '%\\+w%' LIMIT 1").fetchone()
        conn.close()
        if sample is not None:
            return False
        return count > 70_000
    except Exception:
        return False


def ensure_cached(url: str, filename: str) -> str:
    dest = os.path.join("/tmp", filename)
    if not os.path.exists(dest) or os.path.getsize(dest) < 10_000:
        print(f"Downloading {filename}...")
        urllib.request.urlretrieve(url, dest)
    return dest


def clean_usfm_text(raw: str) -> str:
    # Remove footnotes \f ... \f*, \fe ... \fe* and cross-references \x ... \x*
    text = re.sub(r"\\f\b.*?\\f\*", "", raw)
    text = re.sub(r"\\fe\b.*?\\fe\*", "", text)
    text = re.sub(r"\\x\b.*?\\x\*", "", text)
    # Remove word-level attributes like |strong="H1234"
    text = re.sub(r"\|[^\\]*?(?=\\\+?[a-zA-Z0-9*])", "", text)
    text = re.sub(r"\|[^\\*\s]+(?:\s+[^\\*]+)?", "", text)
    # Remove all USFM markers, including nested markers like \+w, \+w*, \w, \w*, \add, \add*, \nd, \q1, \p
    text = re.sub(r"\\[+]?[a-zA-Z0-9]+(?:\*|\\\*)?", " ", text)
    text = re.sub(r"\\[*+]", " ", text)
    # Clean up whitespace before punctuation marks (e.g. "heart , for" -> "heart, for")
    text = re.sub(r"\s+([,.:;?!'\"\)\]])", r"\1", text)
    text = re.sub(r"([(\[])\s+", r"\1", text)
    text = re.sub(r"\s+", " ", text).strip()
    return text


def parse_web_usfm(zip_path: str) -> dict:
    verses = {}
    with zipfile.ZipFile(zip_path, "r") as z:
        for name in z.namelist():
            if not name.endswith(".usfm"):
                continue
            content = z.read(name).decode("utf-8", errors="ignore")
            book_id = None
            chapter = 0
            current_verse = 0
            buffer = []

            def flush():
                if book_id and chapter > 0 and current_verse > 0 and buffer:
                    cleaned = clean_usfm_text(" ".join(buffer))
                    if cleaned:
                        key = (book_id, chapter, current_verse)
                        if key in verses:
                            verses[key] += " " + cleaned
                        else:
                            verses[key] = cleaned

            for line in content.splitlines():
                line = line.strip()
                if not line:
                    continue
                if line.startswith("\\id "):
                    flush()
                    parts = line.split()
                    book_id = parts[1].upper() if len(parts) > 1 else None
                    chapter = 0
                    current_verse = 0
                    buffer = []
                elif line.startswith("\\c "):
                    flush()
                    parts = line.split()
                    try:
                        chapter = int(parts[1])
                    except Exception:
                        chapter = 0
                    current_verse = 0
                    buffer = []
                elif line.startswith("\\v "):
                    flush()
                    m = re.match(r"^\\v\s+(\d+)(?:-\d+)?\s+(.*)$", line)
                    if m:
                        current_verse = int(m.group(1))
                        buffer = [m.group(2)]
                    else:
                        current_verse = 0
                        buffer = []
                elif line.startswith(("\\h", "\\toc", "\\mt", "\\ip", "\\im", "\\iot", "\\io", "\\is", "\\ms", "\\s", "\\r", "\\d")):
                    continue
                else:
                    if current_verse > 0:
                        buffer.append(line)
            flush()
    return verses


def parse_ref_token(token: str):
    parts = token.split(".")
    if len(parts) < 3:
        return None
    book = OSIS_TO_USFM.get(parts[0])
    if not book:
        return None
    try:
        return book, int(parts[1]), int(parts[2])
    except ValueError:
        return None


def build_database():
    legacy_bible = ensure_cached(LEGACY_BIBLE_URL, "legacy_bible.sqlite")
    legacy_guides = ensure_cached(LEGACY_GUIDES_URL, "legacy_guides.sqlite")
    web_zip = ensure_cached(WEB_USFM_URL, "engwebp_usfm.zip")
    cross_zip = ensure_cached(CROSS_REFS_URL, "cross-references.zip")

    os.makedirs(os.path.dirname(DB_PATH), exist_ok=True)
    tmp_db = DB_PATH + ".building"
    if os.path.exists(tmp_db):
        os.remove(tmp_db)

    conn = sqlite3.connect(tmp_db)
    conn.execute("PRAGMA journal_mode = OFF")
    conn.execute("PRAGMA synchronous = OFF")

    conn.executescript(
        """
        CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT NOT NULL);
        CREATE TABLE verses (
            translation TEXT NOT NULL,
            book TEXT NOT NULL,
            chapter INTEGER NOT NULL,
            verse INTEGER NOT NULL,
            text TEXT NOT NULL,
            PRIMARY KEY (translation, book, chapter, verse)
        ) WITHOUT ROWID;
        CREATE TABLE cross_refs (
            book TEXT NOT NULL,
            chapter INTEGER NOT NULL,
            verse INTEGER NOT NULL,
            to_book TEXT NOT NULL,
            to_chapter INTEGER NOT NULL,
            to_verse INTEGER NOT NULL,
            to_verse_end INTEGER NOT NULL,
            votes INTEGER NOT NULL
        );
        CREATE INDEX cross_refs_from ON cross_refs (book, chapter, verse);
        CREATE TABLE chapter_guides (
            book TEXT NOT NULL,
            chapter INTEGER NOT NULL,
            lang TEXT NOT NULL,
            title TEXT NOT NULL,
            summary TEXT NOT NULL,
            context TEXT NOT NULL,
            themes TEXT NOT NULL,
            related TEXT NOT NULL,
            PRIMARY KEY (book, chapter, lang)
        ) WITHOUT ROWID;
        CREATE TABLE father_notes (
            book TEXT NOT NULL,
            chapter INTEGER NOT NULL,
            father TEXT NOT NULL,
            work TEXT NOT NULL,
            date TEXT NOT NULL,
            verse_from INTEGER NOT NULL,
            verse_to INTEGER NOT NULL,
            note_en TEXT NOT NULL,
            note_nl TEXT NOT NULL,
            source TEXT NOT NULL,
            sort INTEGER NOT NULL
        );
        CREATE INDEX father_notes_chapter ON father_notes (book, chapter);
        CREATE TABLE father_quotes (
            book TEXT NOT NULL,
            chapter INTEGER NOT NULL,
            verse INTEGER NOT NULL,
            verse_end INTEGER NOT NULL,
            father TEXT NOT NULL,
            year INTEGER NOT NULL,
            source TEXT NOT NULL,
            text TEXT NOT NULL
        );
        CREATE INDEX father_quotes_verse ON father_quotes (book, chapter, verse);
        """
    )

    # 1. Populate Dutch ('sv') and English ('web') verses
    web_verses = parse_web_usfm(web_zip)
    bconn = sqlite3.connect(legacy_bible)
    verse_rows = []
    seen_web = set()

    for (book, ch, v), txt in web_verses.items():
        verse_rows.append(("web", book, ch, v, txt))
        seen_web.add((book, ch, v))

    for book_id, ch, v, sv, hsv, kjv in bconn.execute(
        "SELECT book_id, chapter, verse, sv, hsv, kjv FROM verses"
    ):
        usfm = LEGACY_TO_USFM.get(book_id)
        if not usfm:
            continue
        nl_text = (sv or hsv or "").strip()
        if nl_text:
            verse_rows.append(("sv", usfm, int(ch), int(v), nl_text))
        en_text = (kjv or "").strip()
        if en_text and (usfm, int(ch), int(v)) not in seen_web:
            verse_rows.append(("web", usfm, int(ch), int(v), en_text))
            seen_web.add((usfm, int(ch), int(v)))
    bconn.close()

    conn.executemany(
        "INSERT OR REPLACE INTO verses (translation, book, chapter, verse, text) VALUES (?, ?, ?, ?, ?)",
        verse_rows,
    )

    # 2. Populate chapter_guides & father_notes & father_quotes
    gconn = sqlite3.connect(legacy_guides)
    guide_rows = []
    for row in gconn.execute(
        "SELECT book_id, chapter, title, summary, context, themes, related FROM guides"
    ):
        book_id, ch, title, summary, context, themes, related_raw = row
        usfm = LEGACY_TO_USFM.get(book_id)
        if not usfm:
            continue
        converted_related = []
        try:
            for item in json.loads(related_raw or "[]"):
                b_raw = item.get("book") or item.get("bookId") or ""
                b_usfm = LEGACY_TO_USFM.get(b_raw.lower(), b_raw.upper())
                if b_usfm:
                    converted_related.append(
                        {
                            "book": b_usfm,
                            "chapter": item.get("chapter"),
                            "verse": item.get("verse"),
                        }
                    )
        except Exception:
            pass
        guide_rows.append(
            (
                usfm,
                int(ch),
                "en",
                title or "",
                summary or "",
                context or "",
                themes or "[]",
                json.dumps(converted_related),
            )
        )
    conn.executemany(
        "INSERT OR REPLACE INTO chapter_guides (book, chapter, lang, title, summary, context, themes, related) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
        guide_rows,
    )

    note_rows = []
    quote_rows = []
    for row in gconn.execute(
        "SELECT book_id, chapter, father, work, date, commentary, source, verses, sort, commentary_nl FROM fathers"
    ):
        book_id, ch, father, work, date, comm_en, source, verses_str, sort_val, comm_nl = row
        usfm = LEGACY_TO_USFM.get(book_id)
        if not usfm:
            continue
        v_from, v_to = 1, 1
        m = re.match(r"^(\d+)(?:-(\d+))?", str(verses_str or "1").strip())
        if m:
            v_from = int(m.group(1))
            v_to = int(m.group(2)) if m.group(2) else v_from
        note_rows.append(
            (
                usfm,
                int(ch),
                father or "",
                work or "",
                date or "",
                v_from,
                v_to,
                comm_en or "",
                comm_nl or "",
                source or "",
                int(sort_val or 0),
            )
        )
        year_match = re.search(r"(\d{3,4})", str(date or ""))
        year = int(year_match.group(1)) if year_match else 400
        if comm_en:
            quote_rows.append(
                (
                    usfm,
                    int(ch),
                    v_from,
                    v_to,
                    father or "",
                    year,
                    f"{work} ({source})".strip(" ()"),
                    comm_en,
                )
            )
    gconn.close()

    conn.executemany(
        "INSERT INTO father_notes (book, chapter, father, work, date, verse_from, verse_to, note_en, note_nl, source, sort) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
        note_rows,
    )
    conn.executemany(
        "INSERT INTO father_quotes (book, chapter, verse, verse_end, father, year, source, text) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
        quote_rows,
    )

    # 3. Populate cross_refs
    cross_rows = []
    with zipfile.ZipFile(cross_zip, "r") as z:
        for name in z.namelist():
            if not name.endswith(".txt"):
                continue
            with z.open(name) as f:
                for raw_line in f:
                    line = raw_line.decode("utf-8", errors="ignore").strip()
                    if not line or line.startswith("From"):
                        continue
                    parts = line.split("\t")
                    if len(parts) < 3:
                        continue
                    frm = parse_ref_token(parts[0])
                    if not frm:
                        continue
                    to_range = parts[1].split("-")
                    to_start = parse_ref_token(to_range[0])
                    if not to_start:
                        continue
                    to_end = parse_ref_token(to_range[1]) if len(to_range) > 1 else to_start
                    try:
                        votes = int(parts[2])
                    except ValueError:
                        votes = 1
                    if votes < 2:
                        continue
                    cross_rows.append(
                        (
                            frm[0],
                            frm[1],
                            frm[2],
                            to_start[0],
                            to_start[1],
                            to_start[2],
                            to_end[2] if to_end else to_start[2],
                            votes,
                        )
                    )
    conn.executemany(
        "INSERT INTO cross_refs (book, chapter, verse, to_book, to_chapter, to_verse, to_verse_end, votes) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
        cross_rows,
    )

    conn.execute("INSERT INTO meta (key, value) VALUES ('version', '6')")
    conn.commit()
    conn.execute("VACUUM")
    conn.close()

    os.replace(tmp_db, DB_PATH)
    print(
        f"Populated {DB_PATH}: {len(verse_rows)} verses, {len(guide_rows)} guides, "
        f"{len(note_rows)} father notes, {len(cross_rows)} cross-refs ({os.path.getsize(DB_PATH)} bytes)"
    )


import gzip
import shutil

GENERATED_BIBLE_DIR = os.path.join(ROOT_DIR, "app", "build", "generated", "assets", "bible")
GENERATED_GZ_PATH = os.path.join(GENERATED_BIBLE_DIR, "bible.db.gz")
CACHE_GZ_PATH = "/tmp/agape_bible.db.gz"

if __name__ == "__main__":
    os.makedirs(GENERATED_BIBLE_DIR, exist_ok=True)
    # Remove any old uncompressed bible.db in src/main/assets so git push stays lightweight
    if os.path.exists(DB_PATH):
        os.remove(DB_PATH)
    src_gz = DB_PATH + ".gz"
    if os.path.exists(src_gz):
        if not os.path.exists(CACHE_GZ_PATH):
            shutil.copyfile(src_gz, CACHE_GZ_PATH)
        os.remove(src_gz)

    if os.path.exists(GENERATED_GZ_PATH) and os.path.getsize(GENERATED_GZ_PATH) > 5_000_000:
        print(f"Generated bible.db.gz already present ({os.path.getsize(GENERATED_GZ_PATH)} bytes)")
        sys.exit(0)

    if os.path.exists(CACHE_GZ_PATH) and os.path.getsize(CACHE_GZ_PATH) > 5_000_000:
        shutil.copyfile(CACHE_GZ_PATH, GENERATED_GZ_PATH)
        print(f"Restored bible.db.gz from cache ({os.path.getsize(GENERATED_GZ_PATH)} bytes)")
        sys.exit(0)

    try:
        tmp = "/tmp/agape_downloaded_bible.db"
        print("Downloading AGAPE-Android3 bible.db...")
        urllib.request.urlretrieve(AGAPE3_BIBLE_DB_URL, tmp)
        if is_db_populated(tmp):
            with open(tmp, "rb") as f_in, gzip.open(CACHE_GZ_PATH, "wb", compresslevel=9) as f_out:
                shutil.copyfileobj(f_in, f_out)
            os.remove(tmp)
            shutil.copyfile(CACHE_GZ_PATH, GENERATED_GZ_PATH)
            print(f"Downloaded and compressed bible.db.gz ({os.path.getsize(GENERATED_GZ_PATH)} bytes)")
            sys.exit(0)
    except Exception as e:
        print(f"Direct download fallback: {e}")

    build_database()
    if os.path.exists(DB_PATH):
        with open(DB_PATH, "rb") as f_in, gzip.open(CACHE_GZ_PATH, "wb", compresslevel=9) as f_out:
            shutil.copyfileobj(f_in, f_out)
        os.remove(DB_PATH)
        shutil.copyfile(CACHE_GZ_PATH, GENERATED_GZ_PATH)


