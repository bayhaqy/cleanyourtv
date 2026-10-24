"""Build a curated TV Hub playlist from the public iptv-org database, keeping only streams that answer right now.

Groups, in this order (tvg-chno numbers keep TV Hub in this order and let the remote tune by number):
  ★ Favorit Indonesia (1-)     ★ Favorit Dunia (50-)
  Indonesia · <kategori> (101-)  Malaysia, Singapura (301-)  Dunia · <kategori> (401-, English/Malay/Indonesian)
Every channel is a free, publicly listed stream from https://github.com/iptv-org/iptv; up to two working streams
per channel are kept, so TV Hub can fall back between them.

Usage: python my-tv/iptv/make_playlist.py [out.m3u]   (run it from a network the TV shares: geo-blocks match)
"""
import json
import re
import sys
import urllib.request
from concurrent.futures import ThreadPoolExecutor

OUT = sys.argv[1] if len(sys.argv) > 1 else "my-tv/iptv/pilihan.m3u"
API = "https://iptv-org.github.io/api/"
sys.stdout.reconfigure(encoding="utf-8", errors="replace")
UA = "Mozilla/5.0 (Linux; Android 11; TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Safari/537.36"

FAV_ID = ["TVRI", "TVRI World", "TVRI Sport", "Kompas TV", "Metro TV", "CNN Indonesia", "CNBC Indonesia", "tvOne",
          "BeritaSatu", "iNews", "RCTI", "SCTV", "Indosiar", "Trans7", "Trans TV", "NET.", "RTV", "MNCTV", "GTV",
          "ANTV", "MDTV", "Jak TV", "DAAI TV", "Garuda TV", "Nusantara TV", "Moji", "Mentari TV", "Rodja TV"]
FAV_WORLD = ["Al Jazeera English", "DW English", "France 24 English", "NHK World-Japan", "CNA", "Euronews English",
             "Bloomberg TV", "Sky News", "ABC News Live", "TRT World", "CGTN", "Arirang TV", "NASA TV", "NASA+",
             "Red Bull TV", "CBS News", "NBC News NOW", "Reuters", "i24NEWS English", "Love Nature", "Bloomberg Originals",
             "Fashion TV", "Real Madrid TV", "Al Jazeera Documentary"]
NEIGHBOURS = [("MY", "Malaysia"), ("SG", "Singapura")]
WORLD_CATS = ["news", "documentary", "sports", "kids", "animation", "movies", "music", "science", "travel", "cooking",
              "outdoor", "lifestyle", "business"]
WORLD_LANGS = {"eng", "ind", "msa"}
WORLD_CAP = 40   # channels per world category, best quality first

CAT_ID = {"general": "Umum", "news": "Berita", "entertainment": "Hiburan", "religious": "Religi", "sports": "Olahraga",
          "kids": "Anak", "music": "Musik", "movies": "Film", "documentary": "Dokumenter", "education": "Edukasi",
          "business": "Bisnis", "culture": "Budaya", "legislative": "Parlemen", "lifestyle": "Gaya Hidup",
          "travel": "Wisata", "cooking": "Kuliner", "science": "Sains", "animation": "Animasi", "series": "Serial",
          "comedy": "Komedi", "family": "Keluarga", "shop": "Belanja", "weather": "Cuaca", "auto": "Otomotif",
          "outdoor": "Alam", "relax": "Santai", "classic": "Klasik", "public": "Publik"}


def get_json(name):
    with urllib.request.urlopen(urllib.request.Request(API + name + ".json", headers={"User-Agent": UA}), timeout=120) as r:
        return json.load(r)


def fetch(url, ua, ref, limit=65536):
    h = {"User-Agent": ua or UA}
    if ref:
        h["Referer"] = ref
    with urllib.request.urlopen(urllib.request.Request(url, headers=h), timeout=8) as r:
        if r.status != 200:
            return None, None
        return r.geturl(), r.read(limit)


def alive(s):
    """True when the stream answers: an HLS playlist (and its first variant), or MPEG-TS/MP4 bytes."""
    try:
        final, body = fetch(s["url"], s.get("user_agent"), s.get("referrer"))
        if body is None:
            return False
        if body[:1] == b"\x47" or body[4:8] == b"ftyp":
            return True
        text = body.decode("utf-8", "replace")
        if not text.lstrip("﻿").startswith("#EXTM3U"):
            return False
        if "#EXT-X-STREAM-INF" not in text:
            return "#EXTINF" in text or "#EXT-X-TARGETDURATION" in text
        variant = next((l.strip() for l in text.splitlines() if l.strip() and not l.startswith("#")), None)
        if not variant:
            return False
        _, vbody = fetch(urllib.parse.urljoin(final, variant), s.get("user_agent"), s.get("referrer"), 8192)
        return vbody is not None and vbody.lstrip(b"\xef\xbb\xbf").startswith(b"#EXTM3U")
    except Exception:
        return False


def quality(s):
    m = re.match(r"(\d+)", s.get("quality") or "")
    return int(m.group(1)) if m else 0


def norm(n):
    return re.sub(r"[^a-z0-9]", "", n.lower())


def main():
    import urllib.parse  # noqa: F401  (used by alive)
    channels = {c["id"]: c for c in get_json("channels") if not c.get("is_nsfw") and not c.get("closed")}
    feeds = get_json("feeds")
    langs = {}
    for f in feeds:
        if f.get("is_main"):
            langs[f["channel"]] = set(f.get("languages") or [])
    logos = {}
    for l in get_json("logos"):
        if l.get("feed") is None and l["channel"] not in logos:
            logos[l["channel"]] = l["url"]
    by_channel = {}
    for s in get_json("streams"):
        if s.get("channel") in channels and s["url"].startswith("http"):
            by_channel.setdefault(s["channel"], []).append(s)

    def wanted(cid):
        c = channels[cid]
        if c["country"] in ("ID", "MY", "SG"):
            return True
        return bool(langs.get(cid, set()) & WORLD_LANGS) and bool(set(c["categories"]) & set(WORLD_CATS))

    todo = [s for cid, ss in by_channel.items() if wanted(cid) for s in ss]
    print("checking", len(todo), "streams ...", flush=True)
    with ThreadPoolExecutor(48) as ex:
        ok = dict(zip((id(s) for s in todo), ex.map(alive, todo)))
    live = {}
    for cid, ss in by_channel.items():
        good = sorted((s for s in ss if ok.get(id(s))), key=quality, reverse=True)[:2]
        if good:
            live[cid] = good
    print("live channels:", len(live))

    names = {norm(channels[c]["name"]): c for c in live}
    used, out = set(), []

    def add(cid, group):
        if cid in used or cid not in live:
            return
        used.add(cid)
        out.append((cid, group))

    for n in FAV_ID:
        if norm(n) in names:
            add(names[norm(n)], "★ Favorit Indonesia")
    for n in FAV_WORLD:
        if norm(n) in names:
            add(names[norm(n)], "★ Favorit Dunia")
    for cid in sorted((c for c in live if channels[c]["country"] == "ID"), key=lambda c: channels[c]["name"].lower()):
        cat = next((CAT_ID.get(k) for k in channels[cid]["categories"] if k in CAT_ID), "Lainnya")
        add(cid, "Indonesia · " + cat)
    for code, label in NEIGHBOURS:
        for cid in sorted((c for c in live if channels[c]["country"] == code), key=lambda c: channels[c]["name"].lower()):
            add(cid, label)
    for cat in WORLD_CATS:
        pool = [c for c in live if c not in used and channels[c]["country"] not in ("ID", "MY", "SG")
                and cat in channels[c]["categories"] and langs.get(c, set()) & WORLD_LANGS]
        pool.sort(key=lambda c: (-quality(live[c][0]), channels[c]["name"].lower()))
        for cid in pool[:WORLD_CAP]:
            add(cid, "Dunia · " + CAT_ID.get(cat, cat.title()))

    # Groups stay together in the order they were first added; numbers follow that order.
    order = []
    for _, g in out:
        if g not in order:
            order.append(g)
    starts = {"★ Favorit Indonesia": 1, "★ Favorit Dunia": 50}
    lines = ["#EXTM3U"]
    counter = {}
    nxt = 101
    for g in order:
        if g in starts:
            counter[g] = starts[g]
        else:
            if g == NEIGHBOURS[0][1] and nxt < 301:
                nxt = 301
            if g.startswith("Dunia ·") and nxt < 401:
                nxt = 401
            counter[g] = nxt
        for cid, grp in out:
            if grp != g:
                continue
            c = channels[cid]
            label = c["name"] if c["country"] in ("ID", "MY", "SG") else f'{c["name"]} · {c["country"]}'
            for s in live[cid]:
                attrs = f'tvg-id="{cid}" tvg-chno="{counter[g]}" tvg-logo="{logos.get(cid, "")}" group-title="{g}"'
                lines.append(f"#EXTINF:-1 {attrs},{label}")
                if s.get("user_agent"):
                    lines.append(f'#EXTVLCOPT:http-user-agent={s["user_agent"]}')
                if s.get("referrer"):
                    lines.append(f'#EXTVLCOPT:http-referrer={s["referrer"]}')
                lines.append(s["url"])
            counter[g] += 1
        if g not in starts:
            nxt = counter[g]
    with open(OUT, "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(lines) + "\n")
    counts = {}
    for _, g in out:
        counts[g] = counts.get(g, 0) + 1
    for g in order:
        print(f"{counts[g]:4d}  {g}")
    print("written", OUT, len(out), "channels")


if __name__ == "__main__":
    main()
