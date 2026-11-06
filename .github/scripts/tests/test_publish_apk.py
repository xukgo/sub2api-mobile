import contextlib
import hashlib
import io
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import publish_apk as publisher

REPO = "xukgo/sub2api-mobile"
SHA = "a" * 40
OTHER_SHA = "b" * 40


def run(**overrides):
    return dict(id=200, head_sha=SHA, head_branch="main", head_repository={"full_name": REPO},
                event="push", status="completed", conclusion="success", run_number=12,
                run_attempt=2, html_url=f"https://github.com/{REPO}/actions/runs/200") | overrides


def artifact(**overrides):
    return dict(id=900, name="sub2api-mobile-debug-12-2", expired=False, size_in_bytes=1000) | overrides


def write_artifact(directory, **element_overrides):
    element = dict(versionName="1.8.2", versionCode=15, outputFile="app-debug.apk") | element_overrides
    (directory / "output-metadata.json").write_text(json.dumps({
        "applicationId": "com.sub2api.mobile", "elements": [element],
    }))
    with zipfile.ZipFile(directory / "app-debug.apk", "w") as apk:
        apk.writestr("AndroidManifest.xml", b"fixture")


class PublisherSelectionTest(unittest.TestCase):
    def test_only_latest_main_commit_and_latest_run_can_be_published(self):
        runs = [run(id=300, head_sha=OTHER_SHA), run(id=100), run()]
        self.assertEqual(200, publisher.select_run(runs, SHA, REPO)["id"])

    def test_newest_failed_or_pending_run_does_not_fall_back_to_earlier_success(self):
        for status, conclusion in [("queued", None), ("in_progress", None), ("completed", "failure")]:
            with self.subTest(status=status), self.assertRaises(publisher.PublishError):
                publisher.select_run([run(id=100), run(status=status, conclusion=conclusion)], SHA, REPO)

    def test_old_commit_other_branch_pull_request_and_fork_are_rejected(self):
        for candidate in [run(head_sha=OTHER_SHA), run(head_branch="feature"), run(event="pull_request"),
                          run(head_repository={"full_name": "someone/other"})]:
            with self.subTest(candidate=candidate), self.assertRaises(publisher.PublishError):
                publisher.select_run([candidate], SHA, REPO)

    def test_only_current_attempts_unexpired_apk_artifact_is_accepted(self):
        candidates = [artifact(name="android-reports-12-2"), artifact(name="sub2api-mobile-debug-12-1"), artifact()]
        self.assertEqual(900, publisher.select_artifact(candidates, run())["id"])
        for bad in [artifact(expired=True), artifact(size_in_bytes=0), artifact(name="sub2api-mobile-debug-12-1")]:
            with self.subTest(bad=bad), self.assertRaises(publisher.PublishError):
                publisher.select_artifact([bad], run())

    def test_metadata_controls_version_and_renaming_does_not_change_apk(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            write_artifact(directory)
            original = (directory / "app-debug.apk").read_bytes()
            version, code, apk, checksum, digest = publisher.prepare_apk(directory)
            self.assertEqual(("1.8.2", 15, "sub2api-mobile-v1.8.2.apk"), (version, code, apk.name))
            self.assertEqual(original, apk.read_bytes())
            self.assertEqual(hashlib.sha256(original).hexdigest(), digest)
            self.assertEqual(f"{digest}  {apk.name}\n", checksum.read_text())

    def test_invalid_versions_and_output_paths_are_rejected(self):
        for overrides in [{"versionName": "apk-200"}, {"versionName": "1.8.2;echo injected"},
                          {"versionCode": 0}, {"outputFile": "../other.apk"}]:
            with self.subTest(overrides=overrides), tempfile.TemporaryDirectory() as temporary:
                directory = Path(temporary)
                write_artifact(directory, **overrides)
                with self.assertRaises(publisher.PublishError):
                    publisher.prepare_apk(directory)

    def test_legacy_artifact_without_metadata_is_not_guessed(self):
        with tempfile.TemporaryDirectory() as temporary, self.assertRaises(publisher.PublishError):
            publisher.prepare_apk(Path(temporary))

    def test_optional_api_does_not_hide_permission_errors(self):
        error = subprocess.CalledProcessError(1, "gh", stderr="gh: Forbidden (HTTP 403)")
        with patch.object(publisher, "gh", side_effect=error), self.assertRaises(subprocess.CalledProcessError):
            publisher.api("example", optional=True)


class PublisherFlowTest(unittest.TestCase):
    def setUp(self):
        self.calls = []
        self.heads = [SHA, SHA, SHA]
        self.existing_tag = False

    def fake_gh(self, *args):
        self.calls.append(args)
        if args[0] == "api":
            path = args[-1]
            if path.endswith("/commits/main"):
                return json.dumps({"sha": self.heads.pop(0)})
            if "/workflows/android.yml/runs?" in path:
                return json.dumps([{"workflow_runs": [run()]}])
            if "/artifacts?" in path:
                return json.dumps([{"artifacts": [artifact()]}])
            if "/git/ref/tags/" in path and self.existing_tag:
                return json.dumps({"object": {"type": "commit", "sha": OTHER_SHA}})
            if "/git/ref/tags/" in path or "/releases/tags/" in path:
                raise subprocess.CalledProcessError(1, "gh", stderr="gh: Not Found (HTTP 404)")
        if args[:2] == ("run", "download"):
            write_artifact(Path(args[args.index("--dir") + 1]))
            return ""
        if args[:2] in [("release", "create"), ("release", "edit")]:
            return ""
        raise AssertionError(f"Unexpected gh call: {args}")

    def publish(self):
        with patch.object(publisher, "gh", side_effect=self.fake_gh), contextlib.redirect_stdout(io.StringIO()):
            publisher.publish(REPO)

    def test_uploads_a_draft_for_exact_sha_before_publishing_as_latest_stable(self):
        self.publish()
        create = next(call for call in self.calls if call[:2] == ("release", "create"))
        edit = next(call for call in self.calls if call[:2] == ("release", "edit"))
        self.assertEqual("v1.8.2", create[2])
        self.assertEqual(SHA, create[create.index("--target") + 1])
        self.assertIn("--draft", create)
        self.assertTrue(any(arg.endswith(".apk") for arg in create))
        self.assertTrue(any(arg.endswith("SHA256SUMS.txt") for arg in create))
        self.assertIn("--draft=false", edit)
        self.assertIn("--prerelease=false", edit)
        self.assertIn("--latest", edit)
        self.assertLess(self.calls.index(create), self.calls.index(edit))

    def test_main_advancing_during_upload_never_publishes_the_draft(self):
        self.heads[-1] = OTHER_SHA
        with self.assertRaises(publisher.PublishError):
            self.publish()
        self.assertTrue(any(call[:2] == ("release", "create") for call in self.calls))
        self.assertFalse(any(call[:2] == ("release", "edit") for call in self.calls))

    def test_existing_version_is_never_overwritten(self):
        self.existing_tag = True
        with self.assertRaises(publisher.PublishError):
            self.publish()
        self.assertFalse(any(call[0] == "release" for call in self.calls))


if __name__ == "__main__":
    unittest.main()
