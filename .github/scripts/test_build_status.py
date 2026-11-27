#!/usr/bin/env python3
"""What a run is allowed to claim."""
# Every test here is a way the difference between "everything passed" and "nothing ran" was, or
# could be, lost. The sweeps are exhaustive because these rules were found by running into them.

import importlib.util
import io
import itertools
import json
import pathlib
import sys
import tempfile
import unittest
from contextlib import redirect_stdout
from unittest import mock

HERE = pathlib.Path(__file__).resolve().parent

_spec = importlib.util.spec_from_file_location("build_status", HERE / "build_status.py")
bs = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(bs)

GREEN = {"PASSED", "PASSED_WITH_SKIPS"}
SUITE_STATES = ["PASSED", "PASSED_WITH_SKIPS", "FAILED", "BLOCKED", "EMPTY",
                "UNMATCHED", "INCOMPLETE", "CANCELLED", "NO_DATA"]
CONCLUSIONS = ["success", "failure", "cancelled", "timed_out", "skipped", None]
SUITE_COMBOS = [c for n in range(1, len(SUITE_STATES) + 1)
                for c in itertools.combinations(SUITE_STATES, n)]

OTR_HEAD = (
    "<?xml version='1.0' encoding='UTF-8'?>\n"
    '<e:events xmlns="https://schemas.opentest4j.org/reporting/core/0.2.0"'
    ' xmlns:e="https://schemas.opentest4j.org/reporting/events/0.2.0"'
    ' xmlns:junit="https://schemas.junit.org/open-test-reporting">\n'
)

CLASS = "[engine:junit-jupiter]/[class:x.Y]"
PLAIN = f"{CLASS}/[method:plain()]"
TEMPLATE = f"{CLASS}/[test-template:param(int)]"


def otr(*nodes):
    """An Open Test Report for (uniqueId, status) pairs, in the order JUnit would emit them."""
    return OTR_HEAD + "".join(
        f'<e:started id="{i}" time="2026-01-01T00:00:00Z">'
        f"<metadata><junit:uniqueId>{uid}</junit:uniqueId></metadata></e:started>\n"
        f'<e:finished id="{i}" time="2026-01-01T00:00:01Z">'
        f'<result status="{status}"/></e:finished>\n'
        for i, (uid, status) in enumerate(nodes, start=1)
    ) + "</e:events>\n"


def entry(uid, job):
    """The two fields build_status reads; build_catalog writes the rest for the report."""
    return {"id": uid, "ciJob": job}


def suite_inputs():
    """Every shape suite_state can be asked about. No report means no joined results."""
    for concl, present, (p, f, sk, b), unj in itertools.product(
        CONCLUSIONS, [True, False], itertools.product([0, 1], repeat=4), [0, 1]
    ):
        if present or not (p or f or sk or b):
            yield concl, present, {"passed": p, "failed": f, "skipped": sk, "blocked": b}, unj


class SuiteStateSweep(unittest.TestCase):
    """A suite may call itself green only when it knows that everything it owns passed."""

    def check(self, predicate, why):
        for concl, present, counts, unjoined in suite_inputs():
            state = bs.suite_state(concl, present, counts, unjoined)
            with self.subTest(job=concl, reported=present, counts=counts, unjoined=unjoined):
                self.assertTrue(predicate(concl, present, counts, unjoined, state), why)

    def test_passed_means_nothing_was_left_out(self):
        self.check(
            lambda c, pr, ct, u, st: st != "PASSED" or (
                pr and c == "success" and u == 0 and ct["passed"] > 0
                and not (ct["failed"] or ct["skipped"] or ct["blocked"])
            ),
            "PASSED claims every test case ran and passed",
        )

    def test_a_job_that_did_not_succeed_is_never_green(self):
        self.check(lambda c, pr, ct, u, st: not (st in GREEN and c != "success"),
                   "the job that produced these results did not finish cleanly")

    def test_absence_is_never_green(self):
        self.check(lambda c, pr, ct, u, st: pr or st not in GREEN, "nothing was reported")

    def test_a_failure_is_never_green(self):
        self.check(lambda c, pr, ct, u, st: not ct["failed"] or st not in GREEN,
                   "a test case failed")

    def test_a_blocked_test_case_is_never_green(self):
        self.check(lambda c, pr, ct, u, st: not ct["blocked"] or st not in GREEN,
                   "a test case never got to run")

    def test_a_result_outside_the_catalog_is_never_green(self):
        self.check(lambda c, pr, ct, u, st: not u or st not in GREEN,
                   "the catalog and the results describe different code")

    def test_every_state_it_returns_is_a_known_one(self):
        self.check(lambda c, pr, ct, u, st: st in SUITE_STATES, "unknown suite state")


class RunStateSweep(unittest.TestCase):
    """The run is as good as its worst suite, over every combination of suite states."""

    def check(self, predicate, why):
        for combo in SUITE_COMBOS:
            state = bs.run_state([{"state": s} for s in combo])
            with self.subTest(suites=combo):
                self.assertTrue(predicate(set(combo), state), why)

    def test_passed_means_every_suite_passed(self):
        self.check(lambda c, st: st != "PASSED" or c == {"PASSED"},
                   "a suite that did not pass is in this run")

    def test_green_run_has_only_green_suites(self):
        self.check(lambda c, st: c <= GREEN or st not in GREEN, "a suite is not green")

    def test_a_failing_suite_makes_the_run_fail(self):
        self.check(lambda c, st: "FAILED" not in c or st == "FAILED", "a suite failed")

    def test_no_suites_at_all_is_no_data(self):
        self.assertEqual(bs.run_state([]), "NO_DATA")


class Summary(unittest.TestCase):
    def test_it_states_the_verdict_the_counts_and_every_suite(self):
        md = bs.summary_markdown({
            "state": "INCOMPLETE",
            "coverage": {"expected": 2, "reported": 1},
            "counts": {"defined": 5, "executed": 3, "runs": 9,
                       "byState": {"passed": 3, "no-report": 2}},
            "suites": [{"name": "unit-test", "state": "PASSED",
                        "counts": {"passed": 3, "failed": 0, "skipped": 0, "blocked": 0}},
                       {"name": "e2e-oracle", "state": "NO_DATA",
                        "counts": {"passed": 0, "failed": 0, "skipped": 0, "blocked": 0}}],
        })
        self.assertIn("### INCOMPLETE — 1 of 2 suites reported", md)
        self.assertIn("5 test cases defined, 3 executed, 9 runs", md)
        self.assertIn("| `e2e-oracle` | NO_DATA |", md)
        self.assertIn("2 no-report", md)


class Scenarios(unittest.TestCase):
    """End to end, from report files to the status the page reads."""

    def build(self, entries, results, expect, jobs):
        d = pathlib.Path(self.tmp.name)
        (d / "catalog.json").write_text(json.dumps({"entries": entries}), encoding="utf-8")
        (d / "jobs.json").write_text(json.dumps(jobs), encoding="utf-8")
        argv = [".", "--catalog", str(d / "catalog.json"), "--jobs", str(d / "jobs.json"),
                "--commit", "c0ffee", "--run-id", "1", "--run-attempt", "1",
                "--run-url", "https://example.invalid", "--run-started-at", "2026-01-01T00:00:00Z",
                "-o", str(d / "status.json")]
        for name, xml in results.items():
            (d / f"{name}.xml").write_text(xml, encoding="utf-8")
            argv += ["--result", f"{name}={d / f'{name}.xml'}"]
        argv += [f"--expect={name}" for name in expect]
        with mock.patch.object(sys, "argv", argv), redirect_stdout(io.StringIO()):
            bs.main()
        return json.loads((d / "status.json").read_text(encoding="utf-8"))

    def rejects(self, **kwargs):
        with self.assertRaises(SystemExit) as caught:
            self.build(**kwargs)
        return str(caught.exception)

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.unit = [entry(PLAIN, "unit"), entry(TEMPLATE, "unit")]
        self.green = [{"name": "unit", "conclusion": "success"}]
        self.red = [{"name": "unit", "conclusion": "failure"}]

    def test_a_class_that_died_before_its_tests_blocks_them(self):
        """A failed @BeforeAll takes the class down: JUnit reports the container, not its tests."""
        status = self.build(self.unit, {"unit": otr((CLASS, "FAILED"))}, ["unit"], self.red)
        self.assertEqual(status["byTest"], {PLAIN: "blocked", TEMPLATE: "blocked"})
        self.assertEqual(status["suites"][0]["counts"]["blocked"], 2)
        self.assertEqual(status["suites"][0]["state"], "BLOCKED")
        self.assertEqual(status["state"], "BLOCKED")

    def test_a_clean_run_passes(self):
        status = self.build(
            self.unit,
            {"unit": otr((PLAIN, "SUCCESSFUL"), (f"{TEMPLATE}/[test-template-invocation:#1]",
                                                 "SUCCESSFUL"), (TEMPLATE, "SUCCESSFUL"))},
            ["unit"], self.green)
        self.assertEqual(status["state"], "PASSED")
        self.assertEqual(status["counts"], {"defined": 2, "executed": 2, "runs": 2,
                                            "byState": {"passed": 2}})

    def test_a_failing_parameterized_case_fails_its_test_case(self):
        """Templates finish SUCCESSFUL in JUnit even when one of their invocations failed."""
        status = self.build(
            self.unit,
            {"unit": otr((PLAIN, "SUCCESSFUL"),
                         (f"{TEMPLATE}/[test-template-invocation:#1]", "SUCCESSFUL"),
                         (f"{TEMPLATE}/[test-template-invocation:#2]", "FAILED"),
                         (TEMPLATE, "SUCCESSFUL"))},
            ["unit"], self.red)
        self.assertEqual(status["byTest"][TEMPLATE], "failed")
        self.assertEqual(status["state"], "FAILED")

    def test_a_template_that_died_mid_stream_fails_its_test_case(self):
        """Its invocations passed, but the node that produced them did not finish."""
        status = self.build(
            [entry(TEMPLATE, "unit")],
            {"unit": otr((f"{TEMPLATE}/[test-template-invocation:#1]", "SUCCESSFUL"),
                         (TEMPLATE, "FAILED"))},
            ["unit"], self.red)
        self.assertEqual(status["byTest"][TEMPLATE], "failed")
        self.assertEqual(status["state"], "FAILED")

    def test_an_abnormal_invocation_status_is_never_a_pass(self):
        """ABORTED is a failed assumption; a status nobody named must not outrank a pass either."""
        for abnormal in ("ABORTED", "ERRORED"):
            with self.subTest(status=abnormal):
                status = self.build(
                    [entry(TEMPLATE, "unit")],
                    {"unit": otr((f"{TEMPLATE}/[test-template-invocation:#1]", "SUCCESSFUL"),
                                 (f"{TEMPLATE}/[test-template-invocation:#2]", abnormal),
                                 (TEMPLATE, "SUCCESSFUL"))},
                    ["unit"], self.red)
                self.assertEqual(status["byTest"][TEMPLATE], "failed")
                self.assertEqual(status["state"], "FAILED")

    def test_a_test_case_is_skipped_only_when_nothing_ran(self):
        for statuses, expected in (
            (["SKIPPED", "SKIPPED"], "skipped"),
            (["SKIPPED", "SUCCESSFUL"], "passed"),
            (["SKIPPED", "FAILED"], "failed"),
        ):
            with self.subTest(invocations=statuses):
                nodes = [(f"{TEMPLATE}/[test-template-invocation:#{i}]", s)
                         for i, s in enumerate(statuses, start=1)]
                status = self.build(
                    [entry(TEMPLATE, "unit")], {"unit": otr(*nodes, (TEMPLATE, "SUCCESSFUL"))},
                    ["unit"], self.green)
                self.assertEqual(status["byTest"][TEMPLATE], expected)

    def test_a_skipped_run_is_not_called_passed(self):
        status = self.build(
            self.unit, {"unit": otr((PLAIN, "SUCCESSFUL"), (TEMPLATE, "SKIPPED"))},
            ["unit"], self.green)
        self.assertEqual(status["state"], "PASSED_WITH_SKIPS")
        self.assertEqual(status["counts"]["executed"], 1, "a skipped test case did not execute")

    def test_a_suite_that_stopped_early_is_not_a_pass(self):
        """Its job died mid-run, so what it never reported is missing, not fine."""
        status = self.build(
            self.unit, {"unit": otr((PLAIN, "SUCCESSFUL"))},
            ["unit"], self.red)
        self.assertEqual(status["state"], "INCOMPLETE")
        self.assertEqual(status["byTest"][TEMPLATE], "unknown")

    def test_a_suite_that_failed_cannot_claim_it_chose_not_to_run_the_rest(self):
        """Its job died after the failure, so what is left is unknown, not deliberately skipped."""
        status = self.build(self.unit, {"unit": otr((PLAIN, "FAILED"))}, ["unit"], self.red)
        self.assertEqual(status["suites"][0]["state"], "FAILED")
        self.assertEqual(status["byTest"][TEMPLATE], "unknown")

    def test_one_suite_blowing_up_does_not_blame_another(self):
        """An engine-level failure prefixes every uniqueId there is, other modules' included."""
        status = self.build(
            [entry(PLAIN, "unit"), entry(TEMPLATE, "e2e")],
            {"unit": otr(("[engine:junit-jupiter]", "FAILED"))},
            ["unit", "e2e"],
            [{"name": "unit", "conclusion": "failure"}, {"name": "e2e", "conclusion": "success"}])
        self.assertEqual(status["byTest"][PLAIN], "blocked")
        self.assertEqual(status["byTest"][TEMPLATE], "no-report")

    def test_a_report_that_cannot_be_read_is_not_a_pass(self):
        """A JVM killed mid-run leaves no closing tag, and the rest of the run still has to show."""
        status = self.build(
            self.unit, {"unit": otr((PLAIN, "SUCCESSFUL")).replace("</e:events>", "")},
            ["unit"], self.red)
        self.assertEqual(status["suites"][0]["state"], "NO_DATA")
        self.assertEqual(status["byTest"][PLAIN], "no-report")

    def test_a_suite_that_never_reported_is_not_a_pass(self):
        status = self.build(self.unit, {}, ["unit"], self.green)
        self.assertEqual(status["state"], "NO_DATA")
        self.assertEqual(status["byTest"][PLAIN], "no-report")

    def test_results_from_other_code_are_not_a_pass(self):
        status = self.build(
            [entry(PLAIN, "unit")], {"unit": otr((f"{CLASS}/[method:renamed()]", "SUCCESSFUL"))},
            ["unit"], self.green)
        self.assertEqual(status["state"], "UNMATCHED")
        self.assertEqual(status["suites"][0]["unjoined"], 1)

    def test_a_test_case_ci_never_runs_still_appears(self):
        status = self.build(
            [entry(PLAIN, "unit"), entry(TEMPLATE, None)],
            {"unit": otr((PLAIN, "SUCCESSFUL"))}, ["unit"], self.green)
        self.assertEqual(status["byTest"][TEMPLATE], "not-in-ci")
        self.assertEqual(status["counts"]["defined"], 2)

    def test_an_empty_catalog_is_refused(self):
        self.assertIn("catalog is empty", self.rejects(
            entries=[], results={}, expect=["unit"], jobs=self.green))

    def test_a_suite_nobody_expects_is_refused(self):
        """Its test cases would land in no suite's counts and vanish from the coverage."""
        self.assertIn("not expected", self.rejects(
            entries=self.unit, results={"unit": otr((PLAIN, "SUCCESSFUL"))},
            expect=["other"], jobs=self.green))

    def test_a_catalog_pointing_at_an_unexpected_suite_is_refused(self):
        self.assertIn("unexpected suites", self.rejects(
            entries=self.unit + [entry(f"{CLASS}/[method:gone()]", "removed-job")],
            results={"unit": otr((PLAIN, "SUCCESSFUL"))}, expect=["unit"], jobs=self.green))


if __name__ == "__main__":
    unittest.main(verbosity=2)
