#!/usr/bin/env python3
import argparse
import hashlib
import json
import subprocess
import sys
from pathlib import Path
from urllib.parse import quote, unquote, urlparse

REPO = "nondename/Minecraft-Legends-of-Medieval"
BRANCH = "dev"
RAW_PREFIX = f"https://raw.githubusercontent.com/{REPO}/{BRANCH}/"
MANIFEST = Path("distribution.json")


def git(*args: str) -> bytes:
    return subprocess.check_output(["git", *args])


def tracked_files() -> set[str]:
    return {
        p.decode("utf-8", "surrogateescape")
        for p in git("ls-tree", "-r", "--name-only", "-z", "HEAD").split(b"\0")
        if p
    }


def blob_digest(path: str) -> tuple[int, str]:
    proc = subprocess.Popen(
        ["git", "cat-file", "blob", f"HEAD:{path}"],
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )
    assert proc.stdout is not None
    md5 = hashlib.md5()
    size = 0
    while True:
        chunk = proc.stdout.read(1024 * 1024)
        if not chunk:
            break
        size += len(chunk)
        md5.update(chunk)
    stderr = proc.stderr.read().decode("utf-8", "replace") if proc.stderr else ""
    code = proc.wait()
    if code != 0:
        raise RuntimeError(f"git cat-file failed for {path}: {stderr.strip()}")
    return size, md5.hexdigest()


def path_from_artifact(artifact: dict) -> str | None:
    artifact_path = artifact.get("path")
    if isinstance(artifact_path, str) and artifact_path:
        return artifact_path.replace("\\", "/").lstrip("/")

    url = artifact.get("url")
    if not isinstance(url, str):
        return None
    parsed = urlparse(url)
    if parsed.netloc != "raw.githubusercontent.com":
        return None
    prefix = f"/{REPO}/{BRANCH}/"
    if not parsed.path.startswith(prefix):
        return None
    return unquote(parsed.path[len(prefix):])


def raw_url(path: str) -> str:
    return RAW_PREFIX + quote(path, safe="/")


class Stats:
    def __init__(self) -> None:
        self.repo_artifacts = 0
        self.stale_removed: list[str] = []
        self.size_fixed: list[str] = []
        self.md5_fixed: list[str] = []
        self.url_fixed: list[str] = []
        self.paths: list[str] = []
        self._cache: dict[str, tuple[int, str]] = {}

    def digest(self, path: str) -> tuple[int, str]:
        if path not in self._cache:
            self._cache[path] = blob_digest(path)
        return self._cache[path]


def sync_node(node, tracked: set[str], stats: Stats):
    if isinstance(node, list):
        result = []
        for item in node:
            synced = sync_node(item, tracked, stats)
            if synced is not None:
                result.append(synced)
        return result

    if not isinstance(node, dict):
        return node

    artifact = node.get("artifact")
    if isinstance(artifact, dict):
        path = path_from_artifact(artifact)
        if path is not None:
            stats.repo_artifacts += 1
            if path not in tracked:
                stats.stale_removed.append(path)
                return None

            stats.paths.append(path)
            size, md5 = stats.digest(path)
            expected_url = raw_url(path)

            if artifact.get("size") != size:
                stats.size_fixed.append(path)
                artifact["size"] = size
            if str(artifact.get("MD5", "")).lower() != md5:
                stats.md5_fixed.append(path)
                artifact["MD5"] = md5
            if artifact.get("url") != expected_url:
                stats.url_fixed.append(path)
                artifact["url"] = expected_url
            if "path" in artifact:
                artifact["path"] = path

    for key in list(node.keys()):
        if key == "artifact":
            continue
        synced = sync_node(node[key], tracked, stats)
        if synced is None:
            del node[key]
        else:
            node[key] = synced
    return node


def audit_unrepresented(tracked: set[str], represented: set[str]) -> list[str]:
    managed_dirs = {p.split("/", 1)[0] for p in represented if "/" in p}
    managed_top_files = {p for p in represented if "/" not in p}
    ignored_prefixes = (".github/", "tools/")
    ignored_files = {"distribution.json", ".gitignore", ".gitattributes"}

    candidates = []
    for path in tracked:
        if path in ignored_files or path.startswith(ignored_prefixes):
            continue
        root = path.split("/", 1)[0]
        if root in managed_dirs or path in managed_top_files:
            candidates.append(path)
    return sorted(set(candidates) - represented)


def print_group(title: str, values: list[str], limit: int = 80) -> None:
    print(f"{title}: {len(values)}")
    for value in values[:limit]:
        print(f"  - {value}")
    if len(values) > limit:
        print(f"  ... and {len(values) - limit} more")


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Synchronize distribution.json with the exact Git blobs published by the dev branch."
    )
    parser.add_argument("--write", action="store_true", help="rewrite distribution.json")
    args = parser.parse_args()

    tracked = tracked_files()
    data = json.loads(MANIFEST.read_text(encoding="utf-8-sig"))
    stats = Stats()
    synced = sync_node(data, tracked, stats)
    if synced is None:
        raise RuntimeError("manifest root was unexpectedly removed")

    represented = set(stats.paths)
    duplicates = sorted({p for p in represented if stats.paths.count(p) > 1})
    unrepresented = audit_unrepresented(tracked, represented)

    print("=== distribution.json audit ===")
    print(f"tracked repository files: {len(tracked)}")
    print(f"repo-backed manifest artifacts checked: {stats.repo_artifacts}")
    print(f"valid repo-backed artifacts after cleanup: {len(stats.paths)}")
    print_group("stale manifest entries removed", stats.stale_removed)
    print_group("size corrections", stats.size_fixed)
    print_group("MD5 corrections", stats.md5_fixed)
    print_group("URL corrections", stats.url_fixed)
    print_group("duplicate represented paths", duplicates)
    print_group("tracked files under managed roots not represented", unrepresented)

    changed = bool(stats.stale_removed or stats.size_fixed or stats.md5_fixed or stats.url_fixed)
    if args.write and changed:
        MANIFEST.write_text(
            json.dumps(synced, ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8",
            newline="\n",
        )
        print("distribution.json rewritten")
    elif args.write:
        print("distribution.json already synchronized")

    if duplicates:
        print("ERROR: duplicate payload paths remain in manifest", file=sys.stderr)
        return 2
    if not args.write and changed:
        print("ERROR: distribution.json is out of sync; run with --write", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
