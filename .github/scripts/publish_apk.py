"""Publish the APK built for main's current commit; never fall back to older code."""

import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
from urllib.parse import urlencode
import zipfile


class PublishError(RuntimeError):
    pass


def gh(*args):
    return subprocess.run(
        ["gh", *args], check=True, capture_output=True, text=True, timeout=300,
    ).stdout


def api(path, *, optional=False):
    try:
        return json.loads(gh("api", path))
    except subprocess.CalledProcessError as error:
        if optional and "(HTTP 404)" in (error.stderr or ""):
            return None
        raise


def items(path, key):
    pages = json.loads(gh("api", "--paginate", "--slurp", path))
    return [item for page in pages for item in page[key]]


def main_sha(repo):
    sha = api(f"repos/{repo}/commits/main")["sha"]
    if not re.fullmatch(r"[0-9a-f]{40}", sha):
        raise PublishError("main 返回了无效的提交 SHA")
    return sha


def select_run(runs, sha, repo):
    candidates = [run for run in runs if
                  run["head_sha"] == sha and run["head_branch"] == "main"
                  and run["event"] in {"push", "workflow_dispatch"}
                  and run["head_repository"]["full_name"].lower() == repo.lower()]
    if not candidates:
        raise PublishError("main 最新提交尚无 Android CI 构建，请等待构建成功后再发布")
    run = max(candidates, key=lambda item: item["id"])
    if run["status"] != "completed" or run["conclusion"] != "success":
        raise PublishError(f"main 最新提交的 Android CI 尚未成功：{run['html_url']}；不会改用旧提交的 APK")
    return run


def select_artifact(artifacts, run):
    name = f"sub2api-mobile-debug-{run['run_number']}-{run['run_attempt']}"
    matches = [artifact for artifact in artifacts if artifact["name"] == name]
    if len(matches) != 1 or matches[0]["expired"] or matches[0]["size_in_bytes"] <= 0:
        raise PublishError(f"当前构建的 APK 产物 {name} 不存在或已过期，请重新运行 Android CI")
    return matches[0]


def prepare_apk(directory):
    metadata_path = directory / "output-metadata.json"
    if not metadata_path.is_file():
        raise PublishError("APK 产物缺少 output-metadata.json，请使用更新后的 Android CI 重新构建")
    metadata = json.loads(metadata_path.read_text())
    elements = metadata.get("elements", [])
    if metadata.get("applicationId") != "com.sub2api.mobile" or len(elements) != 1:
        raise PublishError("APK 元数据的应用 ID 或输出数量不符合预期")
    element = elements[0]
    version, code = element.get("versionName", ""), element.get("versionCode")
    if not re.fullmatch(r"(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)", version):
        raise PublishError("正式发布需要 x.y.z 格式的 versionName")
    if type(code) is not int or code <= 0:
        raise PublishError("APK 的 versionCode 无效")
    apk = directory / "app-debug.apk"
    if element.get("outputFile") != apk.name or not apk.is_file() or not zipfile.is_zipfile(apk):
        raise PublishError("未找到与版本元数据对应的有效 app-debug.apk")
    output = directory / f"sub2api-mobile-v{version}.apk"
    shutil.move(apk, output)
    with output.open("rb") as stream:
        digest = hashlib.file_digest(stream, "sha256").hexdigest()
    checksum = directory / "SHA256SUMS.txt"
    checksum.write_text(f"{digest}  {output.name}\n")
    return version, code, output, checksum, digest


def require_current_main(repo, sha):
    if main_sha(repo) != sha:
        raise PublishError("发布期间 main 出现了新提交，已停止发布；请等待最新构建成功后重试")


def publish(repo):
    sha = main_sha(repo)
    query = urlencode({"branch": "main", "head_sha": sha, "per_page": 100})
    run = select_run(items(f"repos/{repo}/actions/workflows/android.yml/runs?{query}", "workflow_runs"), sha, repo)
    artifact = select_artifact(items(f"repos/{repo}/actions/runs/{run['id']}/artifacts?per_page=100", "artifacts"), run)
    print(f"Source: {sha}\nCI: {run['html_url']}\nArtifact: {artifact['name']}", flush=True)

    with tempfile.TemporaryDirectory(prefix="sub2api-publish-") as temporary:
        directory = Path(temporary)
        gh("run", "download", str(run["id"]), "--repo", repo, "--name", artifact["name"], "--dir", temporary)
        version, code, apk, checksum, digest = prepare_apk(directory)
        tag = f"v{version}"
        tag_path = f"repos/{repo}/git/ref/tags/{tag}"
        if api(tag_path, optional=True) is not None:
            raise PublishError(f"版本标签 {tag} 已存在，不会覆盖；请增加 versionName/versionCode 并重新构建")
        if api(f"repos/{repo}/releases/tags/{tag}", optional=True) is not None:
            raise PublishError(f"{tag} 已有 Release 或草稿，请先检查该版本，或增加版本号后重新构建")

        notes = directory / "release-notes.md"
        notes.write_text(
            f"Android APK v{version}（versionCode {code}），Android 13 及以上。\n\n"
            f"- 源码提交：{sha}\n- 构建记录：{run['html_url']}\n"
            f"- SHA-256：`{digest}`\n\n"
            "APK 直接转存自 Android CI，未经重新编译或重新签名。"
            "当前为调试 APK，覆盖安装需要与已安装版本使用相同的签名。\n",
        )
        require_current_main(repo, sha)
        # Upload into a draft first, so the update checker never sees an empty release.
        gh("release", "create", tag, str(apk), str(checksum), "--repo", repo,
           "--target", sha, "--title", f"Sub2API Mobile {tag}", "--notes-file", str(notes), "--draft")
        require_current_main(repo, sha)
        reference = api(tag_path, optional=True)
        if reference is not None and (reference["object"]["type"] != "commit" or reference["object"]["sha"] != sha):
            raise PublishError("版本标签未指向本次构建的提交，已停止发布，请检查 Release 草稿")
        gh("release", "edit", tag, "--repo", repo, "--draft=false", "--prerelease=false", "--latest")
        url = f"https://github.com/{repo}/releases/tag/{tag}"
        summary = f"## APK 已发布\n\n[{tag}]({url}) · versionCode {code}\n\n源码：`{sha}`\n\n构建：{run['html_url']}\n"
        print(summary)
        if os.environ.get("GITHUB_STEP_SUMMARY"):
            with open(os.environ["GITHUB_STEP_SUMMARY"], "a") as report:
                report.write(summary)


if __name__ == "__main__":
    try:
        if os.environ.get("GITHUB_REF") != "refs/heads/main":
            raise PublishError("请在 Run workflow 中选择 main 分支")
        repository = os.environ["GH_REPO"]
        if not re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", repository):
            raise PublishError("仓库名称无效")
        publish(repository)
    except (PublishError, subprocess.SubprocessError, ValueError, OSError, KeyError) as error:
        print(f"::error::{error}", file=sys.stderr)
        if isinstance(error, subprocess.CalledProcessError):
            print((error.stderr or "")[-2000:], file=sys.stderr)
        sys.exit(1)
