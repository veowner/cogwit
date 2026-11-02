"""Fills in Cogwit's Modrinth page: uploads the GIFs and the recipe to the gallery, then writes the body with their links,
along with the title and summary from <release_dir>/modrinth-meta.json.

Run: set -a; . ~/.config/cogwit/modrinth.env; set +a
     uv run --no-project --with requests python -I tools/page_modrinth.py <release_dir> [--dry]

The body template is <release_dir>/modrinth-body.md. {{name}} becomes the link of that gallery image and {{TROPHIES}}
the table of all trophies, built from the English language file. The finished body is also saved next to the
template as modrinth-body.rendered.md. --dry uploads nothing and writes only that file, with local paths for images.
Gallery uploads are remembered in modrinth-state.json, so running again only sends what is new. The project stays
whatever it was (a draft stays a draft). The token is read from MODRINTH_TOKEN and never printed.
"""
import json
import os
import re
import sys
from pathlib import Path

import requests

API = "https://api.modrinth.com/v2"
RELEASE = Path(sys.argv[1])
DRY = "--dry" in sys.argv
ROOT = Path(__file__).resolve().parent.parent
STATE_FILE = RELEASE / "modrinth-state.json"
STATE = json.loads(STATE_FILE.read_text())
# title and summary, shared with publish_modrinth.py
META = json.loads((RELEASE / "modrinth-meta.json").read_text())
HEADERS = {} if DRY else {"Authorization": os.environ["MODRINTH_TOKEN"],
                          "User-Agent": "veowner/cogwit-publish (modrinth.com/user/veowner)"}

# name in the template, file, gallery title, gallery description
NEW_IMAGES = [
    ("slab", "gifs/slab.gif", "An arrow caught at your back", "From level 3 a slab tears out of the ground behind you."),
    ("scan", "gifs/scan.gif", "Ore scan", "A wave rolls out 32 blocks and ores light up as it reaches them."),
    ("crew", "gifs/crew.gif", "The brothers jump down", "Spark, Rivet and Anvil, from level 4."),
    ("landing", "gifs/landing.gif", "Landing in Horizon boots", "A fall from 5 blocks or more lands as a shockwave."),
    ("slam", "gifs/slam.gif", "Netherite Monstrosity trophy", "Cracks of magma run out and geysers burst along them."),
    ("beam", "gifs/beam.gif", "Umvuthi trophy", "A beam of sunlight comes down on a mob and follows it."),
    ("recipe", "recipe.png", "Recipe", "Copper blocks, amethyst shards, a redstone block and an eye of ender."),
]
# images already in the gallery (uploaded by publish_modrinth.py), found by their title; the file is for --dry
OLD_IMAGES = {"threats": ("What he sees", "gallery/02-threats.png"), "agent": ("Agent settings", "gallery/07-agent.png")}

MOD_NAMES = {"minecraft": "Vanilla", "cataclysm": "L_Ender's Cataclysm", "mowziesmobs": "Mowzie's Mobs",
             "aether": "The Aether", "irons_spellbooks": "Iron's Spells 'n Spellbooks",
             "twilightforest": "Twilight Forest", "block_factorys_bosses": "Bosses'Rise"}


def check(response, what):
    if response.status_code >= 300:
        raise SystemExit(f"{what} failed: {response.status_code} {response.text[:500]}")
    return response


def gallery(pid):
    return check(requests.get(f"{API}/project/{pid}", headers=HEADERS, timeout=30), "read gallery").json()["gallery"]


def upload(pid, name, path, title, description, ordering):
    """Uploads one image and returns its link: the one gallery url that wasn't there before."""
    before = {g["url"] for g in gallery(pid)}
    ext = path.suffix.lstrip(".")
    check(requests.post(f"{API}/project/{pid}/gallery", headers={**HEADERS, "Content-Type": f"image/{ext}"},
                        params={"ext": ext, "featured": "false", "title": title, "description": description,
                                "ordering": ordering},
                        data=path.read_bytes(), timeout=120), f"gallery {name}")
    added = [g["url"] for g in gallery(pid) if g["url"] not in before]
    if len(added) != 1:
        raise SystemExit(f"gallery {name}: expected one new image, found {len(added)}")
    return added[0]


def links(pid):
    if DRY:
        out = {name: file for name, file, _, _ in NEW_IMAGES}
        out.update({name: file for name, (_, file) in OLD_IMAGES.items()})
        return out
    uploaded = STATE.setdefault("page_images", {})
    for i, (name, file, title, description) in enumerate(NEW_IMAGES):
        if name not in uploaded:
            uploaded[name] = upload(pid, name, RELEASE / file, title, description, 20 + i)
            STATE_FILE.write_text(json.dumps(STATE, indent=2) + "\n")
    # the page shows the original files: a gallery "url" can be a 350 px thumbnail, "raw_url" never is
    images = gallery(pid)
    raw = {g["url"]: g["raw_url"] for g in images}
    out = {name: raw.get(url, url) for name, url in uploaded.items()}
    by_title = {g["title"]: g["raw_url"] for g in images}
    for name, (title, _) in OLD_IMAGES.items():
        out[name] = by_title[title]
    return out


def trophy_table():
    """Boss, mod, passive and active of every trophy, in the codex's order, from the English language file."""
    lang = json.loads((ROOT / "src/main/resources/assets/cogwit/lang/en_us.json").read_text())
    enum = (ROOT / "src/main/java/dev/cogwit/trophy/Trophy.java").read_text()
    rows = ["| Boss | From | Passive | Active |", "|---|---|---|---|"]
    for key, mod in re.findall(r'^\s{4}([A-Z_]+)\("([a-z_]+):', enum, re.M):
        k = key.lower()
        active = lang[f"cogwit.trophy.{k}.active"]
        passive = lang[f"cogwit.trophy.{k}.passive"]
        rows.append(f"| {lang[f'cogwit.trophy.{k}.name']} | {MOD_NAMES[mod]} | "
                    f"{'none' if passive == 'No passive' else passive} | "
                    f"{'none' if active == 'No active' else active} |")
    return "\n".join(rows), len(rows) - 2


def main():
    pid = STATE["project_id"]
    body = (RELEASE / "modrinth-body.md").read_text()
    table, count = trophy_table()
    if count != 35:
        raise SystemExit(f"expected 35 trophies, found {count}")
    body = body.replace("{{TROPHIES}}", table)
    for name, url in links(pid).items():
        body = body.replace("{{" + name + "}}", url)
    left = re.findall(r"\{\{[a-zA-Z_]+\}\}", body)
    if left:
        raise SystemExit(f"unfilled: {left}")
    (RELEASE / "modrinth-body.rendered.md").write_text(body)
    print(f"body: {len(body)} characters")
    if DRY:
        return
    page = {"title": META["title"], "description": META["summary"], "body": body}
    check(requests.patch(f"{API}/project/{pid}", headers=HEADERS, json=page, timeout=30), "write page")
    project = check(requests.get(f"{API}/project/{pid}", headers=HEADERS, timeout=30), "read back").json()
    print(json.dumps({"status": project["status"], "gallery": len(project["gallery"]),
                      "page_matches": all(project[k] == v for k, v in page.items())}, indent=2))


if __name__ == "__main__":
    main()
