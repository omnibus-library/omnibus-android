#!/usr/bin/env python3
"""Contract tests for the CI test-gate workflows.

Each case fails with its TC id when the workflow file or a required key is
missing. Run a subset with --stage 1|2|3.
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parents[2]
WORKFLOWS = ROOT / ".github" / "workflows"
CI_TESTS = "ci-tests.yml"
ANDROID_TESTS = "android-tests.yml"
COMMENT = "ci-tests-comment.yml"

WRITE_SCOPES = ("contents", "checks", "pull-requests")


class Results:
    def __init__(self) -> None:
        self.failed = 0

    def check(self, tc_id: str, ok: bool, detail: str = "") -> None:
        if ok:
            print(f"PASS {tc_id}")
            return
        self.failed += 1
        suffix = f": {detail}" if detail else ""
        print(f"FAIL {tc_id}{suffix}")


def load_workflow(name: str) -> tuple[dict | None, str | None]:
    path = WORKFLOWS / name
    if not path.is_file():
        return None, f"missing workflow file .github/workflows/{name}"
    with path.open(encoding="utf-8") as handle:
        loaded = yaml.safe_load(handle)
    if not isinstance(loaded, dict):
        return None, f".github/workflows/{name} did not parse as a mapping"
    return loaded, None


def trigger_block(doc: dict) -> dict | None:
    # PyYAML 1.1 parses the GitHub key `on` as boolean True.
    raw = doc.get(True, doc.get("on"))
    return raw if isinstance(raw, dict) else None


def jobs(doc: dict) -> dict:
    raw = doc.get("jobs")
    return raw if isinstance(raw, dict) else {}


def steps_of(job: dict) -> list:
    raw = job.get("steps")
    return raw if isinstance(raw, list) else []


def uses_of(step: dict) -> str:
    value = step.get("uses")
    return value if isinstance(value, str) else ""


def run_of(step: dict) -> str:
    value = step.get("run")
    return value if isinstance(value, str) else ""


def with_of(step: dict) -> dict:
    raw = step.get("with")
    return raw if isinstance(raw, dict) else {}


def find_step(job: dict, action_prefix: str) -> dict | None:
    for step in steps_of(job):
        if isinstance(step, dict) and uses_of(step).startswith(action_prefix):
            return step
    return None


def permissions_of(node: dict) -> dict:
    raw = node.get("permissions")
    return raw if isinstance(raw, dict) else {}


def grants_write(perms: dict) -> list[str]:
    granted = []
    for scope in WRITE_SCOPES:
        if perms.get(scope) == "write":
            granted.append(scope)
    return granted


def working_directory(job: dict, step: dict) -> str | None:
    defaults = job.get("defaults")
    if isinstance(defaults, dict):
        run_defaults = defaults.get("run")
        if isinstance(run_defaults, dict) and isinstance(run_defaults.get("working-directory"), str):
            return run_defaults["working-directory"]
    value = step.get("working-directory")
    return value if isinstance(value, str) else None


def require(results: Results, tc_id: str, doc: dict | None, error: str | None) -> dict | None:
    if doc is None:
        results.check(tc_id, False, error or "workflow unavailable")
        return None
    return doc


def test_tc1_pull_request_target_trigger(results: Results) -> None:
    """TC-1: pull_request_target opened and synchronize on main."""
    tc_id = "TC-1"
    doc = require(results, tc_id, *load_workflow(CI_TESTS))
    if doc is None:
        return
    if doc.get("name") != "CI Tests":
        results.check(tc_id, False, f"workflow name is {doc.get('name')!r}, expected 'CI Tests'")
        return
    on = trigger_block(doc)
    if on is None:
        results.check(tc_id, False, "missing on block")
        return
    if set(on) != {"pull_request_target"}:
        results.check(tc_id, False, f"triggers are {sorted(on)}, expected only pull_request_target")
        return
    event = on["pull_request_target"]
    if not isinstance(event, dict):
        results.check(tc_id, False, "pull_request_target is not a mapping")
        return
    types = event.get("types")
    if sorted(types or []) != ["opened", "synchronize"]:
        results.check(tc_id, False, f"types are {types!r}, expected opened and synchronize")
        return
    branches = event.get("branches")
    if branches not in (["main"], "main"):
        results.check(tc_id, False, f"branches are {branches!r}, expected main")
        return
    results.check(tc_id, True)


def test_tc2_checkout_jdk_gradle(results: Results) -> None:
    """TC-2: checkout head SHA, Temurin 25, setup-gradle."""
    tc_id = "TC-2"
    doc = require(results, tc_id, *load_workflow(ANDROID_TESTS))
    if doc is None:
        return
    job = jobs(doc).get("unit-tests")
    if not isinstance(job, dict):
        results.check(tc_id, False, "unit-tests job not found")
        return
    checkout = find_step(job, "actions/checkout@v7")
    if checkout is None:
        results.check(tc_id, False, "actions/checkout@v7 step not found on unit-tests")
        return
    checkout_with = with_of(checkout)
    ref = checkout_with.get("ref")
    if not isinstance(ref, str) or "inputs.head_sha" not in ref:
        results.check(tc_id, False, f"checkout ref is {ref!r}, expected inputs.head_sha")
        return
    if checkout_with.get("persist-credentials") is not False:
        results.check(tc_id, False, "persist-credentials is not false")
        return
    if checkout_with.get("allow-unsafe-pr-checkout") is not True:
        results.check(tc_id, False, "allow-unsafe-pr-checkout is not true")
        return
    java = find_step(job, "actions/setup-java@v6")
    if java is None:
        results.check(tc_id, False, "actions/setup-java@v6 step not found")
        return
    java_with = with_of(java)
    if java_with.get("distribution") != "temurin":
        results.check(tc_id, False, f"distribution is {java_with.get('distribution')!r}")
        return
    if str(java_with.get("java-version")) != "25":
        results.check(tc_id, False, f"java-version is {java_with.get('java-version')!r}")
        return
    if "cache" in java_with:
        results.check(tc_id, False, "setup-java sets cache; Gradle caching belongs to setup-gradle")
        return
    if find_step(job, "gradle/actions/setup-gradle@v5") is None:
        results.check(tc_id, False, "gradle/actions/setup-gradle@v5 step not found")
        return
    results.check(tc_id, True)


def test_tc3_unit_test_command(results: Results) -> None:
    """TC-3: ./gradlew test --stacktrace in omnibus."""
    tc_id = "TC-3"
    doc = require(results, tc_id, *load_workflow(ANDROID_TESTS))
    if doc is None:
        return
    job = jobs(doc).get("unit-tests")
    if not isinstance(job, dict):
        results.check(tc_id, False, "unit-tests job not found")
        return
    matched = [
        step
        for step in steps_of(job)
        if isinstance(step, dict) and "./gradlew test --stacktrace" in run_of(step)
    ]
    if not matched:
        results.check(tc_id, False, "unit-tests job does not run ./gradlew test --stacktrace")
        return
    if working_directory(job, matched[0]) != "omnibus":
        results.check(tc_id, False, "unit test working directory is not omnibus")
        return
    results.check(tc_id, True)


def test_tc4_opened_verdict(results: Results) -> None:
    """TC-4: opened verdict requires both outputs to be success."""
    tc_id = "TC-4"
    doc = require(results, tc_id, *load_workflow(CI_TESTS))
    if doc is None:
        return
    job = jobs(doc).get("verdict")
    if not isinstance(job, dict):
        results.check(tc_id, False, "verdict job not found")
        return
    if job.get("name") not in (None, "verdict"):
        results.check(tc_id, False, f"verdict job name is {job.get('name')!r}")
        return
    opened = [
        step
        for step in steps_of(job)
        if isinstance(step, dict) and "opened" in str(step.get("if", ""))
    ]
    if len(opened) != 1:
        results.check(tc_id, False, "expected one verdict step conditioned on opened")
        return
    script = run_of(opened[0])
    required = (
        "needs.tests.outputs.unit",
        "needs.tests.outputs.instrumentation",
        "CI Tests: passed",
        "CI Tests: failed",
        "success",
        "exit 1",
    )
    missing = [item for item in required if item not in script]
    if missing:
        results.check(tc_id, False, f"opened verdict script missing {missing}")
        return
    results.check(tc_id, True)


def test_tc5_emulator(results: Results) -> None:
    """TC-5: API 37.0 google_apis_ps16k emulator runs connectedAndroidTest."""
    tc_id = "TC-5"
    doc = require(results, tc_id, *load_workflow(ANDROID_TESTS))
    if doc is None:
        return
    job = jobs(doc).get("instrumentation-tests")
    if not isinstance(job, dict):
        results.check(tc_id, False, "instrumentation-tests job not found")
        return
    step = find_step(job, "reactivecircus/android-emulator-runner@v2")
    if step is None:
        results.check(tc_id, False, "android-emulator-runner@v2 step not found")
        return
    params = with_of(step)
    api_level = params.get("api-level")
    if api_level != "37.0":
        results.check(tc_id, False, f"api-level is {api_level!r}, expected string 37.0")
        return
    expected = {
        "target": "google_apis_ps16k",
        "arch": "x86_64",
        "working-directory": "omnibus",
        "script": "./gradlew connectedAndroidTest --stacktrace",
    }
    mismatches = {key: params.get(key) for key, value in expected.items() if params.get(key) != value}
    if mismatches:
        results.check(tc_id, False, f"emulator inputs mismatch: {mismatches}")
        return
    results.check(tc_id, True)


def test_tc6_cmdline_tools(results: Results) -> None:
    """TC-6: cmdline-tools;latest replaces latest before the emulator starts."""
    tc_id = "TC-6"
    doc = require(results, tc_id, *load_workflow(ANDROID_TESTS))
    if doc is None:
        return
    job = jobs(doc).get("instrumentation-tests")
    if not isinstance(job, dict):
        results.check(tc_id, False, "instrumentation-tests job not found")
        return
    prior_runs: list[str] = []
    found_emulator = False
    for step in steps_of(job):
        if not isinstance(step, dict):
            continue
        if uses_of(step).startswith("reactivecircus/android-emulator-runner@"):
            found_emulator = True
            break
        if run_of(step):
            prior_runs.append(run_of(step))
    if not found_emulator:
        results.check(tc_id, False, "android-emulator-runner step not found")
        return
    script = "\n".join(prior_runs)
    required = ("cmdline-tools;latest", "latest-2", "mv ")
    missing = [item for item in required if item not in script]
    if missing or "cmdline-tools/latest" not in script:
        results.check(
            tc_id,
            False,
            "no run step before the emulator installs cmdline-tools;latest and moves latest-2 over latest",
        )
        return
    results.check(tc_id, True)


def test_tc7_collect_outputs(results: Results) -> None:
    """TC-7: collect needs both test jobs and outputs the instrumentation result."""
    tc_id = "TC-7"
    doc = require(results, tc_id, *load_workflow(ANDROID_TESTS))
    if doc is None:
        return
    job = jobs(doc).get("collect")
    if not isinstance(job, dict):
        results.check(tc_id, False, "collect job not found")
        return
    needs = job.get("needs")
    if isinstance(needs, str):
        needs = [needs]
    if sorted(needs or []) != ["instrumentation-tests", "unit-tests"]:
        results.check(tc_id, False, f"collect needs {needs!r}")
        return
    outputs = job.get("outputs")
    if not isinstance(outputs, dict):
        results.check(tc_id, False, "collect has no outputs")
        return
    instrumentation = outputs.get("instrumentation")
    if not isinstance(instrumentation, str) or "needs.instrumentation-tests.result" not in instrumentation:
        results.check(tc_id, False, f"instrumentation output is {instrumentation!r}")
        return
    results.check(tc_id, True)


def test_tc8_failure_artifacts(results: Results) -> None:
    """TC-8: both test jobs upload failure artifacts."""
    tc_id = "TC-8"
    doc = require(results, tc_id, *load_workflow(ANDROID_TESTS))
    if doc is None:
        return
    for job_id in ("unit-tests", "instrumentation-tests"):
        job = jobs(doc).get(job_id)
        if not isinstance(job, dict):
            results.check(tc_id, False, f"{job_id} job not found")
            return
        upload = find_step(job, "actions/upload-artifact@v4")
        if upload is None:
            results.check(tc_id, False, f"{job_id} does not use actions/upload-artifact@v4")
            return
        if upload.get("if") != "failure()":
            results.check(tc_id, False, f"{job_id} upload if is {upload.get('if')!r}")
            return
        if with_of(upload).get("if-no-files-found") != "ignore":
            results.check(tc_id, False, f"{job_id} upload does not set if-no-files-found: ignore")
            return
    results.check(tc_id, True)


def test_tc9_comment_phrase(results: Results) -> None:
    """TC-9: issue_comment created, exact ci test / ci tests bodies."""
    tc_id = "TC-9"
    doc = require(results, tc_id, *load_workflow(COMMENT))
    if doc is None:
        return
    on = trigger_block(doc)
    if on is None or set(on) != {"issue_comment"}:
        results.check(tc_id, False, f"triggers are {None if on is None else sorted(on)}")
        return
    types = on["issue_comment"].get("types") if isinstance(on["issue_comment"], dict) else None
    if types != ["created"]:
        results.check(tc_id, False, f"issue_comment types are {types!r}")
        return
    job = jobs(doc).get("authorize")
    if not isinstance(job, dict):
        results.check(tc_id, False, "authorize job not found")
        return
    condition = str(job.get("if", ""))
    for phrase in ("ci test", "ci tests"):
        if f"== '{phrase}'" not in condition and f'== "{phrase}"' not in condition:
            results.check(tc_id, False, f"authorize if does not equality-check {phrase!r}")
            return
    results.check(tc_id, True)


def test_tc10_authorize_head_sha(results: Results) -> None:
    """TC-10: maintainers only, open PR into main, pass head.sha through."""
    tc_id = "TC-10"
    doc = require(results, tc_id, *load_workflow(COMMENT))
    if doc is None:
        return
    job = jobs(doc).get("authorize")
    if not isinstance(job, dict):
        results.check(tc_id, False, "authorize job not found")
        return
    script = "\n".join(run_of(step) + "\n" + str(with_of(step).get("script", "")) for step in steps_of(job) if isinstance(step, dict))
    for token in ("OWNER", "MEMBER", "COLLABORATOR", "main", "open", "head.sha"):
        if token not in script:
            results.check(tc_id, False, f"authorize script missing {token!r}")
            return
    tests = jobs(doc).get("tests")
    if not isinstance(tests, dict):
        results.check(tc_id, False, "tests job not found")
        return
    with_block = tests.get("with")
    head_sha = with_block.get("head_sha") if isinstance(with_block, dict) else None
    if not isinstance(head_sha, str) or "needs.authorize.outputs.head_sha" not in head_sha:
        results.check(tc_id, False, f"tests head_sha input is {head_sha!r}")
        return
    if tests.get("uses") != "./.github/workflows/android-tests.yml":
        results.check(tc_id, False, f"tests uses {tests.get('uses')!r}")
        return
    results.check(tc_id, True)


def test_tc11_check_run(results: Results) -> None:
    """TC-11: checks.create CI Tests / verdict on the authorize head SHA."""
    tc_id = "TC-11"
    doc = require(results, tc_id, *load_workflow(COMMENT))
    if doc is None:
        return
    job = jobs(doc).get("verdict")
    if not isinstance(job, dict):
        results.check(tc_id, False, "verdict job not found")
        return
    condition = str(job.get("if", ""))
    if "cancelled" not in condition:
        results.check(tc_id, False, "verdict if does not skip cancelled test runs")
        return
    script_step = find_step(job, "actions/github-script@v9")
    if script_step is None:
        results.check(tc_id, False, "actions/github-script@v9 step not found on verdict")
        return
    script = str(with_of(script_step).get("script", ""))
    required = (
        "checks.create",
        "CI Tests / verdict",
        "needs.authorize.outputs.head_sha",
        "needs.tests.outputs.unit",
        "needs.tests.outputs.instrumentation",
        "success",
        "CI Tests: passed",
        "CI Tests: failed",
        "setFailed",
    )
    missing = [item for item in required if item not in script]
    if missing:
        results.check(tc_id, False, f"check script missing {missing}")
        return
    results.check(tc_id, True)


def test_tc12_permission_split(results: Results) -> None:
    """TC-12: reusable caller is contents read; check writer does not checkout."""
    tc_id = "TC-12"
    doc = require(results, tc_id, *load_workflow(COMMENT))
    if doc is None:
        return
    tests = jobs(doc).get("tests")
    verdict = jobs(doc).get("verdict")
    if not isinstance(tests, dict) or not isinstance(verdict, dict):
        results.check(tc_id, False, "tests or verdict job not found")
        return
    test_perms = permissions_of(tests)
    if test_perms != {"contents": "read"}:
        results.check(tc_id, False, f"tests permissions are {test_perms!r}")
        return
    verdict_perms = permissions_of(verdict)
    if verdict_perms.get("checks") != "write":
        results.check(tc_id, False, f"verdict permissions are {verdict_perms!r}")
        return
    if grants_write({key: value for key, value in verdict_perms.items() if key != "checks"}):
        results.check(tc_id, False, f"verdict grants extra write scopes {verdict_perms!r}")
        return
    for step in steps_of(verdict):
        if isinstance(step, dict) and "actions/checkout" in uses_of(step):
            results.check(tc_id, False, "verdict job checks out the repository")
            return
    results.check(tc_id, True)


def test_tc13_read_only_permissions(results: Results) -> None:
    """TC-13: Gradle workflows and reusable callers are contents read."""
    tc_id = "TC-13"
    ci, ci_error = load_workflow(CI_TESTS)
    android, android_error = load_workflow(ANDROID_TESTS)
    if ci is None:
        results.check(tc_id, False, ci_error or "missing ci-tests.yml")
        return
    if android is None:
        results.check(tc_id, False, android_error or "missing android-tests.yml")
        return
    for label, doc in ((CI_TESTS, ci), (ANDROID_TESTS, android)):
        perms = permissions_of(doc)
        if perms.get("contents") != "read":
            results.check(tc_id, False, f"{label} permissions.contents is not read")
            return
        extra = grants_write(perms)
        if extra:
            results.check(tc_id, False, f"{label} grants write on {extra}")
            return
    caller = jobs(ci).get("tests")
    if not isinstance(caller, dict):
        results.check(tc_id, False, "ci-tests.yml tests job not found")
        return
    caller_perms = permissions_of(caller)
    if caller_perms != {"contents": "read"}:
        results.check(tc_id, False, f"ci-tests tests permissions are {caller_perms!r}")
        return
    for job_id, job in jobs(android).items():
        if not isinstance(job, dict):
            continue
        extra = grants_write(permissions_of(job))
        if extra:
            results.check(tc_id, False, f"android-tests job {job_id} grants write on {extra}")
            return
        contents = permissions_of(job).get("contents")
        if contents not in (None, "read"):
            results.check(tc_id, False, f"android-tests job {job_id} contents is {contents!r}")
            return
    results.check(tc_id, True)


def test_tc14_synchronize_clears(results: Results) -> None:
    """TC-14: synchronize fails verdict with CI Tests: cleared and does not run Gradle."""
    tc_id = "TC-14"
    doc = require(results, tc_id, *load_workflow(CI_TESTS))
    if doc is None:
        return
    job = jobs(doc).get("verdict")
    if not isinstance(job, dict):
        results.check(tc_id, False, "verdict job not found")
        return
    matched = [
        step
        for step in steps_of(job)
        if isinstance(step, dict) and "synchronize" in str(step.get("if", ""))
    ]
    if len(matched) != 1:
        results.check(tc_id, False, "expected one verdict step conditioned on synchronize")
        return
    script = run_of(matched[0])
    if "CI Tests: cleared" not in script or "exit 1" not in script:
        results.check(tc_id, False, "synchronize step does not exit 1 with CI Tests: cleared")
        return
    for step in steps_of(job):
        if isinstance(step, dict) and "./gradlew" in run_of(step):
            results.check(tc_id, False, "verdict job runs ./gradlew")
            return
    results.check(tc_id, True)


STAGE_CASES = {
    1: (
        test_tc1_pull_request_target_trigger,
        test_tc2_checkout_jdk_gradle,
        test_tc3_unit_test_command,
        test_tc4_opened_verdict,
        test_tc13_read_only_permissions,
        test_tc14_synchronize_clears,
    ),
    2: (
        test_tc5_emulator,
        test_tc6_cmdline_tools,
        test_tc7_collect_outputs,
        test_tc8_failure_artifacts,
    ),
    3: (
        test_tc9_comment_phrase,
        test_tc10_authorize_head_sha,
        test_tc11_check_run,
        test_tc12_permission_split,
    ),
}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--stage", type=int, choices=(1, 2, 3))
    args = parser.parse_args()
    selected = (args.stage,) if args.stage else (1, 2, 3)
    results = Results()
    for stage in selected:
        for case in STAGE_CASES[stage]:
            case(results)
    return 1 if results.failed else 0


if __name__ == "__main__":
    sys.exit(main())
