#!/usr/bin/env python3
"""Writes the AltStore source for Knit Studio.

An AltStore *source* is a small JSON file that AltStore checks for new versions. Once it is added
in AltStore (Sources › +), new builds show up as updates on the iPhone and install with one tap.

Usage: make-altstore-source.py <ipa-meta.json> <release-download-base-url> <output.json> [notes]
The base URL is where the release files live, e.g.
https://github.com/<owner>/knit-studio/releases/download/latest
"""
import json
import sys
from datetime import datetime, timezone

meta_path, base_url, out_path = sys.argv[1:4]
notes = sys.argv[4] if len(sys.argv) > 4 else ""
meta = json.load(open(meta_path))
repo_url = base_url.split("/releases/")[0]
raw_url = repo_url.replace("https://github.com/", "https://raw.githubusercontent.com/") + "/main"
icon = raw_url + "/iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/AppIcon.png"
tint = "#B4533C"

source = {
    "name": "Knit Studio",
    "identifier": "studio.knit.source",
    "subtitle": "Personal knitting pattern studio",
    "description": "Builds of Knit Studio from " + repo_url,
    "iconURL": icon,
    "website": repo_url,
    "tintColor": tint,
    "apps": [
        {
            "name": "Knit Studio",
            # Must equal the app's CFBundleIdentifier, or AltStore treats it as another app.
            "bundleIdentifier": meta["bundleId"],
            "developerName": "Knit Studio",
            "subtitle": "Design, check and knit your own patterns",
            "localizedDescription": "Charts, written rows, validation, PDF export and a knitting companion. Works offline; your patterns stay on your phone.",
            "iconURL": icon,
            "tintColor": tint,
            # Older AltStore Classic versions read these app-level fields instead of "versions";
            # newer ones prefer "versions". Both describe the same build.
            "version": meta["version"],
            "versionDate": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
            "versionDescription": notes or "Build " + meta["build"],
            "downloadURL": base_url + "/" + meta["file"],
            "size": int(meta["size"]),
            "versions": [
                {
                    "version": meta["version"],
                    "buildVersion": meta["build"],
                    "date": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
                    "localizedDescription": notes or "Build " + meta["build"],
                    "downloadURL": base_url + "/" + meta["file"],
                    "size": int(meta["size"]),
                    "minOSVersion": meta["minOS"],
                }
            ],
            # Knit Studio uses no entitlements and asks for no privacy permissions; AltStore
            # checks the installed app against this declaration.
            "appPermissions": {"entitlements": [], "privacy": {}},
        }
    ],
    "news": [],
}
json.dump(source, open(out_path, "w"), indent=2)
print("Wrote", out_path, "for", meta["version"], "build", meta["build"])
