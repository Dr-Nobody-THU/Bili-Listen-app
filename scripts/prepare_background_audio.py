"""Rebuild licensed offline ambience loops; run with numpy, scipy and soundfile installed.

python scripts/prepare_background_audio.py [--direct]
--direct ignores a stale system HTTP proxy for this process only.
Original recordings stay in ignored build/audio-sources. No source code is copied.
"""
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
import argparse
import base64
import hashlib
import json
import math
import sys
import urllib.request

import numpy as np
import soundfile as sf
from scipy.signal import resample_poly

ROOT = Path(__file__).resolve().parents[1]
PIN = "bbc88ed3b9dfa8b21805d77286f3183abf88f36f"
REPO = "rafaelmardojai/blanket"
RATE = 22050
# Attribution is also shipped in the app and available from the background-sound picker.
SOURCES = {
    "rain": ("alex36917", "https://freesound.org/people/alex36917/sounds/524605/", "CC BY 4.0", "https://creativecommons.org/licenses/by/4.0/", "Porrumentzio"),
    "storm": ("Digifish music", "https://freesound.org/people/digifishmusic/sounds/41739/", "CC BY 4.0", "https://creativecommons.org/licenses/by/4.0/", "Porrumentzio"),
    "waves": ("Luftrum", "https://freesound.org/people/Luftrum/sounds/48412/", "CC BY 4.0", "https://creativecommons.org/licenses/by/4.0/", "Porrumentzio"),
    "stream": ("gluckose", "https://freesound.org/people/gluckose/sounds/333987/", "CC0", "https://creativecommons.org/publicdomain/zero/1.0/", ""),
    "wind": ("felix.blume", "https://freesound.org/people/felix.blume/sounds/217506/", "CC0", "https://creativecommons.org/publicdomain/zero/1.0/", "Porrumentzio"),
    "birds": ("kvgarlic", "https://freesound.org/people/kvgarlic/sounds/156826/", "CC0", "https://creativecommons.org/publicdomain/zero/1.0/", "Porrumentzio"),
    "summer-night": ("Lisa Redfern", "https://soundbible.com/2083-Crickets-Chirping-At-Night.html", "Public Domain", "https://creativecommons.org/publicdomain/mark/1.0/", ""),
    "fireplace": ("ezwa", "https://soundbible.com/1543-Fireplace.html", "Public Domain", "https://creativecommons.org/publicdomain/mark/1.0/", ""),
    "coffee-shop": ("stephan", "https://soundbible.com/1664-Restaurant-Ambiance.html", "Public Domain", "https://creativecommons.org/publicdomain/mark/1.0/", ""),
    "train": ("SDLx", "https://freesound.org/people/SDLx/sounds/259988/", "CC BY 3.0", "https://creativecommons.org/licenses/by/3.0/", ""),
}


def main():
    sys.stdout.reconfigure(encoding="utf-8")
    parser = argparse.ArgumentParser()
    parser.add_argument("--direct", action="store_true")
    args = parser.parse_args()
    opener = urllib.request.build_opener(*([urllib.request.ProxyHandler({})] if args.direct else []))
    cache = ROOT / "build/audio-sources"
    assets = ROOT / "app/src/main/assets/background"
    cache.mkdir(parents=True, exist_ok=True)
    assets.mkdir(parents=True, exist_ok=True)

    def fetch(url, timeout=25):
        return opener.open(urllib.request.Request(url, headers={"User-Agent": "BiliListen-audio-preparation"}), timeout=timeout).read()

    tree_file = cache / f"tree-{PIN}.json"
    if not tree_file.exists():
        tree_file.write_bytes(fetch(f"https://api.github.com/repos/{REPO}/git/trees/{PIN}?recursive=1"))
    tree = json.loads(tree_file.read_bytes())
    blobs = {item["path"]: item["sha"] for item in tree["tree"]}

    def prepare(name):
        relative = f"data/resources/sounds/{name}.ogg"
        source = cache / f"{name}-{PIN[:8]}.ogg"
        if not source.exists():
            try:
                data = fetch(f"https://raw.githubusercontent.com/{REPO}/{PIN}/{relative}", timeout=8)
            except Exception:
                blob = json.loads(fetch(f"https://api.github.com/repos/{REPO}/git/blobs/{blobs[relative]}"))
                data = base64.b64decode(blob["content"])
            source.write_bytes(data)
        data = source.read_bytes()
        assert hashlib.sha1(f"blob {len(data)}\0".encode() + data).hexdigest() == blobs[relative], name
        original, rate = sf.read(source, always_2d=True, dtype="float64")
        # Keep a substantial excerpt, avoiding the start/end fades of source recordings.
        mono = original.mean(axis=1)
        active = np.flatnonzero(np.abs(mono) > max(np.max(np.abs(mono)) * 0.015, 1e-5))
        assert len(active) > rate * 4, f"Insufficient audio: {name}"
        mono = mono[active[0]:active[-1] + 1]
        length = min(len(mono), rate * 60)
        start = (len(mono) - length) // 2
        mono = mono[start:start + length]
        divisor = math.gcd(rate, RATE)
        mono = resample_poly(mono, RATE // divisor, rate // divisor)
        mono -= mono.mean()
        overlap = min(RATE * 6, len(mono) // 5)
        assert overlap >= RATE, f"Clip too short: {name}"
        # The end becomes the continuation of the original head. Constant-power overlap
        # avoids the level dip of a linear crossfade between uncorrelated ambience.
        theta = np.linspace(0, np.pi / 2, overlap, endpoint=True)
        tail = mono[-overlap:] * np.cos(theta) + mono[:overlap] * np.sin(theta)
        loop = np.concatenate((mono[overlap:-overlap], tail))
        loop -= loop.mean()
        rms = np.sqrt(np.mean(loop * loop))
        # Match average levels without letting an isolated click force the entire rain/fire
        # recording to be much quieter. A smooth peak curve keeps its quieter detail intact.
        low, high = 0.0, 0.10 / max(rms, 1e-9) * 8
        for _ in range(22):
            gain = (low + high) / 2
            candidate = 0.70 * np.tanh(loop * gain / 0.70)
            if np.sqrt(np.mean(candidate * candidate)) > 0.10:
                high = gain
            else:
                low = gain
        loop = 0.70 * np.tanh(loop * ((low + high) / 2) / 0.70)
        loop -= loop.mean()
        assert np.max(np.abs(loop)) < 0.75, name
        pcm = np.rint(loop * 32767).astype("<i2")
        output = assets / f"{name}.pcm"
        output.write_bytes(pcm.tobytes())
        # Compare the wrap against local variation, not an arbitrary absolute zero-crossing.
        differences = np.abs(np.diff(pcm.astype(np.int32)))
        seam_jump = abs(int(pcm[0]) - int(pcm[-1]))
        assert seam_jump <= max(256, np.quantile(differences, .999) * 1.5), f"Bad seam: {name}"
        windows = [np.sqrt(np.mean(chunk * chunk)) for chunk in np.array_split(loop, max(1, len(loop) // (RATE // 5)))]
        assert min(windows) > 0.0001, f"Unexpected silent window: {name}"
        result = dict(name=name, sample_rate=RATE, channels=1, frames=len(pcm), seconds=round(len(pcm) / RATE, 3),
                      crossfade_seconds=round(overlap / RATE, 3), rms=round(float(np.sqrt(np.mean(loop * loop))), 6),
                      peak=round(float(np.max(np.abs(loop))), 6), seam_jump=seam_jump,
                      source_sha256=hashlib.sha256(data).hexdigest(), pcm_sha256=hashlib.sha256(pcm.tobytes()).hexdigest())
        print(json.dumps(result), flush=True)
        return result

    results = list(ThreadPoolExecutor(3).map(prepare, SOURCES))
    (assets / "manifest.json").write_text(json.dumps({"source_commit": PIN, "loops": results}, indent=2) + "\n", encoding="utf-8")
    credits = ["Qi Sheng background sound credits", "",
               "White, pink and brown noise: continuously generated by Qi Sheng; no third-party recording.",
               "Environmental recordings are sourced from Blanket's audio collection (not its program code).",
               f"https://github.com/{REPO}/blob/{PIN}/SOUNDS_LICENSING.md", "",
               "Qi Sheng modifications: excerpting, mono conversion, resampling to 22050 Hz,",
               "DC removal, equal-power loop crossfade, gain/peak normalization and PCM conversion.", ""]
    for name, (author, url, license_name, license_url, editor) in SOURCES.items():
        credits += [name, f"Author: {author}"]
        if editor:
            credits.append(f"Earlier loop edit: {editor}")
        credits += [url, f"License: {license_name}", license_url, ""]
    (assets / "SOURCES.txt").write_text("\n".join(credits), encoding="utf-8")


if __name__ == "__main__":
    main()
