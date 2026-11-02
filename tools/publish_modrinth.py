"""Puts Cogwit on Modrinth as a draft: the project (text, icon, gallery) and one version with the jar.

Run: set -a; . ~/.config/cogwit/modrinth.env; set +a
     uv run --no-project --with requests python -I tools/publish_modrinth.py <release_dir> <jar> [--submit]

Without --submit the project stays a private draft that only its owner can see. --submit sends it to Modrinth's
moderators, which makes it public once approved; run that only after the draft has been checked by hand.
The token is read from MODRINTH_TOKEN and never printed. What was created is remembered in
<release_dir>/modrinth-state.json, so running again skips the steps that are already done.
"""
import json
import os
import sys
from collections import Counter
from pathlib import Path

import requests

API = "https://api.modrinth.com/v2"
HEADERS = {"Authorization": os.environ["MODRINTH_TOKEN"], "User-Agent": "veowner/cogwit-publish (modrinth.com/user/veowner)"}

RELEASE = Path(sys.argv[1])
JAR = Path(sys.argv[2])
SUBMIT = "--submit" in sys.argv
STATE_FILE = RELEASE / "modrinth-state.json"
STATE = json.loads(STATE_FILE.read_text()) if STATE_FILE.exists() else {}
# title and summary, shared with page_modrinth.py
META = json.loads((RELEASE / "modrinth-meta.json").read_text())

# mods Cogwit works with but doesn't need; listing them puts Cogwit in Modrinth's "depends on X" filter for each.
# Twilight Forest has no official Modrinth page, so it can't be listed.
OPTIONAL = ["l_enders-cataclysm", "mowzies-mobs", "aether", "irons-spells-n-spellbooks", "bossesrise", "emi", "create",
            "sophisticated-backpacks", "controlify"]

GALLERY = [
    ("01-crew.png", "Cogwit's brothers", "From level 4, Spark, Rivet and Anvil jump down and fight next to you.", True),
    ("02-threats.png", "What he sees", "Brackets on hostile mobs you can see. Orange is coming for you, red is about to hurt.", False),
    ("03-slab.png", "Back shield", "A slab tears out of the ground behind you and takes the hit. From level 5 it's copper.", False),
    ("04-flight.png", "Event Horizon flight", "Leggings and boots together fly on tokens, with fire from the soles and hip nozzles.", False),
    ("05-landing.png", "Hard landing", "Horizon boots turn a long fall into a shockwave.", False),
    ("06-wheel.png", "Ability wheel", "Hold G: trophy actives, scan, mark and armor abilities.", False),
    ("07-agent.png", "Agent settings", "Ask, always or never, for every action.", False),
    ("08-codex.png", "Trophy codex", "35 bosses, what each one gives and where to find it.", False),
    ("09-roadmap.png", "Path", "A step-by-step path through the mod, with a compass to the next place you need.", False),
]

CHANGELOG = """First public alpha.

Cogwit, the copper companion who learns from the damage you take: threat brackets, a thought line above the hotbar,
the ore scan, the back shield, an agent that only does what you allow, a fighting style he picks up from you, combos,
a safe escape pearl and a compass back to your things after you die. Also in this version: the copper exoframe, the
Event Horizon suit and trophies from 35 bosses.

So far two people have played it, on one server, so expect bugs."""


def save():
    STATE_FILE.write_text(json.dumps(STATE, indent=2) + "\n")


def check(response, what):
    if response.status_code >= 300:
        raise SystemExit(f"{what} failed: {response.status_code} {response.text[:500]}")
    return response


def project_id(slug):
    return check(requests.get(f"{API}/project/{slug}", headers=HEADERS, timeout=30), f"look up {slug}").json()["id"]


def dependencies():
    required = [{"project_id": project_id(slug), "dependency_type": "required"} for slug in ("curios", "geckolib")]
    return required + [{"project_id": project_id(slug), "dependency_type": "optional"} for slug in OPTIONAL]


def create_project():
    if "project_id" in STATE:
        return STATE["project_id"]
    if requests.get(f"{API}/project/cogwit", headers=HEADERS, timeout=30).status_code != 404:
        raise SystemExit("the slug 'cogwit' is taken")
    data = {
        "slug": "cogwit",
        "title": META["title"],
        "description": META["summary"],
        "categories": ["adventure", "equipment", "game-mechanics"],
        "additional_categories": ["mobs", "utility"],
        "client_side": "required",
        "server_side": "required",
        "body": (RELEASE / "modrinth-body.md").read_text(),
        "project_type": "mod",
        "initial_versions": [],
        "is_draft": True,
        "license_id": "MIT",
    }
    with open(RELEASE / "icon-512.png", "rb") as icon:
        r = check(requests.post(f"{API}/project", headers=HEADERS, timeout=60,
                                files={"data": (None, json.dumps(data), "application/json"),
                                       "icon": ("icon.png", icon, "image/png")}), "create project")
    STATE["project_id"] = r.json()["id"]
    save()
    return STATE["project_id"]


def upload_gallery(pid):
    done = set(STATE.get("gallery", []))
    for i, (name, title, description, featured) in enumerate(GALLERY):
        if name in done:
            continue
        params = {"ext": "png", "featured": str(featured).lower(), "title": title, "description": description,
                  "ordering": i}
        check(requests.post(f"{API}/project/{pid}/gallery", headers={**HEADERS, "Content-Type": "image/png"},
                            params=params, data=(RELEASE / "gallery" / name).read_bytes(), timeout=60),
              f"gallery {name}")
        done.add(name)
        STATE["gallery"] = sorted(done)
        save()


def upload_version(pid):
    if "version_id" in STATE:
        return
    version = JAR.stem.split("-", 1)[1]
    data = {
        "name": f"Cogwit {version} (alpha)",
        "version_number": version,
        "changelog": CHANGELOG,
        "dependencies": dependencies(),
        "game_versions": ["1.21.1"],
        "version_type": "alpha",
        "loaders": ["neoforge"],
        "featured": True,
        "project_id": pid,
        "file_parts": ["jar"],
        "primary_file": "jar",
    }
    with open(JAR, "rb") as jar:
        r = check(requests.post(f"{API}/version", headers=HEADERS, timeout=120,
                                files={"data": (None, json.dumps(data), "application/json"),
                                       "jar": (JAR.name, jar, "application/java-archive")}), "upload version")
    STATE["version_id"] = r.json()["id"]
    save()


def main():
    pid = create_project()
    upload_gallery(pid)
    upload_version(pid)
    # Modrinth now derives the sides from the versions, so the ones sent at creation come back as "unknown".
    check(requests.patch(f"{API}/project/{pid}", headers=HEADERS, timeout=30,
                         json={"client_side": "required", "server_side": "required"}), "set sides")
    # an existing version gets the current changelog and dependency list too
    check(requests.patch(f"{API}/version/{STATE['version_id']}", headers=HEADERS, timeout=60,
                         json={"changelog": CHANGELOG, "dependencies": dependencies()}), "update version")
    if SUBMIT:
        check(requests.patch(f"{API}/project/{pid}", headers=HEADERS, json={"status": "processing"}, timeout=30),
              "submit for review")
        print("submitted for review")
    project = check(requests.get(f"{API}/project/{pid}", headers=HEADERS, timeout=30), "read back").json()
    version = check(requests.get(f"{API}/version/{STATE['version_id']}", headers=HEADERS, timeout=30),
                    "read version").json()
    print(json.dumps({"id": pid, "slug": project["slug"], "status": project["status"],
                      "versions": project["versions"], "gallery": len(project["gallery"]),
                      "dependencies": Counter(d["dependency_type"] for d in version["dependencies"])}, indent=2))


if __name__ == "__main__":
    main()
