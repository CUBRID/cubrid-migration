#!/usr/bin/env python3
"""What the Allure report is allowed to say about a test case that did not run."""
# allure generate turns an empty input into a valid all-green report, so a report built only from
# what ran cannot say anything is missing. These pin the rule that makes the rest safe: never
# "passed".

import io
import json
import pathlib
import sys
import tempfile
import unittest
from contextlib import redirect_stdout
from unittest import mock

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import build_status as bs  # noqa: E402
from allure_results import (  # noqa: E402
    HISTORY_LIMIT, SYNTHETIC, UNACCOUNTED, carry_history, environment, main, prune_history, stamp,
    synthesize)

# A state comes from a result the run produced, or from the catalog saying it produced none.
REPORTED = set(bs.TC_STATE.values())
NO_RESULT = {"blocked", "not-selected", "no-report", "not-in-ci", "unknown"}

UID = "[engine:junit-jupiter]/[class:com.cmt.C]"


def entry(unique_id, job="unit-test", database="CUBRID"):
    """One catalog row, as build_catalog.py writes it."""
    return {"id": unique_id, "name": "a test case", "group": ["SomeTest", "Nested"],
            "class": "com.cmt.C", "method": "m", "kind": "test", "ciJob": job,
            "database": database, "sourceUrl": "https://github.com/o/r/blob/sha/C.java"}


def results_of(directory):
    """Every result written into a directory, keyed by the uniqueId it carries."""
    out = {}
    for path in pathlib.Path(directory).glob("*-result.json"):
        result = json.loads(path.read_text(encoding="utf-8"))
        uid = next((x["value"] for x in result["labels"]
                    if x["name"] == "junit.platform.uniqueid"), None)
        out[uid] = result
    return out


def labelled(result, name):
    """Every value a result carries under one label name."""
    return [x["value"] for x in result["labels"] if x["name"] == name]


class TempDir(unittest.TestCase):
    def tmp(self):
        """A directory that goes away with the test."""
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        return pathlib.Path(directory.name)


class NeverGreen(unittest.TestCase):
    def test_no_test_case_is_written_as_passed(self):
        """The one rule. A test case that did not run may not read as one that did."""
        written = [status for status, _ in SYNTHETIC.values()] + [UNACCOUNTED[0]]
        self.assertNotIn("passed", written)
        self.assertEqual(set(written) - {"skipped", "unknown", "broken"}, set())

    def test_every_state_without_a_result_is_mapped(self):
        """An unmapped state would silently become the vaguest wording there is."""
        self.assertEqual(NO_RESULT - set(SYNTHETIC), set())

    def test_a_state_that_reported_is_never_written_by_hand(self):
        """Those test cases have a real result already; writing another would double-count them."""
        self.assertEqual(REPORTED & set(SYNTHETIC), set())

    def test_a_missing_suite_is_not_a_skip(self):
        """Tibero is skipped on purpose; a suite that never reported is not, and must differ."""
        self.assertEqual(SYNTHETIC["not-in-ci"][0], "skipped")
        self.assertEqual(SYNTHETIC["no-report"][0], "unknown")


class Synthesized(TempDir):
    def synth(self, catalog, states, reported=(), notes=None):
        out = self.tmp()
        made = synthesize(catalog, states, set(reported), out, notes)
        return made, results_of(out)

    def test_the_reason_ci_runs_none_of_them_is_carried_through(self):
        """Without it a reader is left with "no job selects it" and no way to find out why."""
        uid = f"{UID}/[method:tibero()]"
        _, written = self.synth({uid: entry(uid, job=None, database="Tibero")},
                                {uid: "not-in-ci"},
                                notes={"Tibero": "Tibero needs a licensed server"})
        self.assertIn("Tibero needs a licensed server",
                      written[uid]["statusDetails"]["message"])

    def test_the_reason_is_not_put_on_a_suite_that_simply_failed_to_report(self):
        """That one did not run because something broke, not because of any policy."""
        uid = f"{UID}/[method:m()]"
        _, written = self.synth({uid: entry(uid)}, {uid: "no-report"},
                                notes={"CUBRID": "a policy note"})
        self.assertNotIn("a policy note", written[uid]["statusDetails"]["message"])

    def test_the_reason_goes_only_to_the_database_it_is_about(self):
        """A test case that dropped out of CI by accident must not borrow Tibero's excuse."""
        meant, dropped = f"{UID}/[method:tibero()]", f"{UID}/[method:oops()]"
        _, written = self.synth(
            {meant: entry(meant, job=None, database="Tibero"),
             dropped: entry(dropped, job=None, database="MySQL")},
            {meant: "not-in-ci", dropped: "not-in-ci"},
            notes={"Tibero": "Tibero needs a licensed server"})
        self.assertIn("licensed server", written[meant]["statusDetails"]["message"])
        self.assertNotIn("licensed server", written[dropped]["statusDetails"]["message"])

    def test_a_test_case_ci_never_runs_is_written_out(self):
        uid = f"{UID}/[method:tibero()]"
        made, written = self.synth({uid: entry(uid, job=None, database="Tibero")},
                                   {uid: "not-in-ci"})
        self.assertEqual(made, {"skipped": 1})
        self.assertEqual(written[uid]["status"], "skipped")
        self.assertEqual(labelled(written[uid], "parentSuite"), ["not run by CI"])
        self.assertIn("Tibero", labelled(written[uid], "tag"))
        self.assertIn("not-in-ci", labelled(written[uid], "tag"))

    def test_it_is_filed_where_the_adapter_would_have_filed_it(self):
        """Allure takes suite from the declaring class, so a @Nested case sits at the inner name."""
        uid = f"{UID}/[method:m()]"
        _, written = self.synth({uid: entry(uid)}, {uid: "no-report"})
        self.assertEqual(labelled(written[uid], "suite"), ["Nested"])
        self.assertEqual(labelled(written[uid], "subSuite"), [])

    def test_it_continues_the_history_of_the_runs_that_did_report(self):
        """Allure keys history by testCaseId; without it the same test case owns two lineages."""
        uid = f"{UID}/[method:m()]"
        _, written = self.synth({uid: entry(uid)}, {uid: "no-report"})
        self.assertEqual(written[uid]["testCaseId"], uid)

    def test_it_claims_no_time(self):
        """A run lasts max(stop) - min(start), so a zero would report it as taking an age."""
        uid = f"{UID}/[method:m()]"
        _, written = self.synth({uid: entry(uid)}, {uid: "no-report"})
        self.assertEqual({"start", "stop"} & set(written[uid]), set())

    def test_it_carries_the_catalog_name_and_source(self):
        uid = f"{UID}/[method:m()]"
        _, written = self.synth({uid: entry(uid)}, {uid: "no-report"})
        self.assertEqual(written[uid]["name"], "a test case")
        self.assertEqual([x["url"] for x in written[uid]["links"]],
                         ["https://github.com/o/r/blob/sha/C.java"])

    def test_a_test_case_that_reported_is_left_alone(self):
        uid = f"{UID}/[method:m()]"
        made, written = self.synth({uid: entry(uid)}, {uid: "passed"}, reported=[uid])
        self.assertEqual((made, written), (dict(), {}))

    def test_a_state_nobody_mapped_is_still_written_and_still_not_green(self):
        uid = f"{UID}/[method:m()]"
        made, written = self.synth({uid: entry(uid)}, {uid: "something-new"})
        self.assertEqual(made, {"unknown": 1})
        self.assertIn("something-new", labelled(written[uid], "tag"))


class Stamped(TempDir):
    def run_one(self, result):
        artifacts = self.tmp()
        (artifacts / "allure-unit-test").mkdir()
        (artifacts / "allure-unit-test" / "a-result.json").write_text(json.dumps(result))
        out = self.tmp()
        uid = f"{UID}/[method:m()]"
        reported = stamp(artifacts, ["unit-test"], {uid: entry(uid)}, out)
        return reported, results_of(out)

    def test_a_result_is_marked_with_the_job_that_produced_it(self):
        uid = f"{UID}/[method:m()]"
        _, written = self.run_one(
            {"uuid": "a", "status": "passed",
             "labels": [{"name": "junit.platform.uniqueid", "value": uid}]})
        self.assertEqual(labelled(written[uid], "parentSuite"), ["unit-test"])
        self.assertEqual([x["name"] for x in written[uid]["links"]], ["source"])
        self.assertIn("CUBRID", labelled(written[uid], "tag"))

    def test_an_invocation_counts_as_its_test_case_reporting(self):
        """Many invocations of one case still mean that case reported."""
        uid = f"{UID}/[test-template:p()]"
        artifacts = self.tmp()
        (artifacts / "allure-unit-test").mkdir()
        (artifacts / "allure-unit-test" / "a-result.json").write_text(json.dumps(
            {"uuid": "a", "status": "passed", "labels": [
                {"name": "junit.platform.uniqueid",
                 "value": f"{uid}/[test-template-invocation:#7]"}]}))
        reported = stamp(artifacts, ["unit-test"], {uid: entry(uid)}, self.tmp())
        self.assertEqual(reported, {uid})


class Published(TempDir):
    """What the whole script puts in front of a reader, driven the way ci.yml drives it."""

    def publish(self, reported):
        uid = f"{UID}/[method:m()]"
        d = self.tmp()
        (d / "allure-unit-test").mkdir()
        (d / "allure-unit-test" / "a-result.json").write_text(json.dumps(
            {"uuid": "a", "status": "passed",
             "labels": [{"name": "junit.platform.uniqueid", "value": uid}]}), encoding="utf-8")
        (d / "catalog.json").write_text(json.dumps({"entries": [entry(uid)]}), encoding="utf-8")
        (d / "status.json").write_text(json.dumps({
            "commit": "c0ffee", "state": "PASSED" if reported else "NO_DATA",
            "coverage": {"expected": 1, "reported": int(reported)},
            "counts": {"defined": 1, "executed": int(reported), "runs": int(reported)},
            "suites": [{"name": "unit-test", "reported": reported}],
            "byTest": {uid: "passed" if reported else "no-report"},
        }), encoding="utf-8")
        argv = [".", "--artifacts", str(d), "--suite", "unit-test",
                "--catalog", str(d / "catalog.json"), "--status", str(d / "status.json"),
                "--out", str(d / "out")]
        with mock.patch.object(sys, "argv", argv), redirect_stdout(io.StringIO()):
            main()
        return results_of(d / "out")[uid]

    def test_only_results_the_status_accepted_are_published(self):
        """A job that ran again and uploaded nothing leaves the last attempt's results on disk."""
        for reported, status in ((True, "passed"), (False, "unknown")):
            with self.subTest(reported=reported):
                self.assertEqual(self.publish(reported)["status"], status)


class History(TempDir):
    def carry(self, files):
        source = self.tmp()
        for name, data in files.items():
            (source / name).write_text(json.dumps(data), encoding="utf-8")
        out = self.tmp()
        carry_history(source, out)
        return {p.name: json.loads(p.read_text()) for p in (out / "history").glob("*.json")}

    def test_a_trend_keeps_the_newest_points(self):
        """Allure writes the newest first, so cutting the tail is what drops the oldest."""
        trend = [{"data": {"total": n}} for n in range(HISTORY_LIMIT + 5)]
        kept = self.carry({"history-trend.json": trend})["history-trend.json"]
        self.assertEqual(len(kept), HISTORY_LIMIT)
        self.assertEqual(kept[0]["data"]["total"], 0)

    def test_a_test_case_keeps_the_newest_runs(self):
        items = [{"uid": str(n), "status": "passed"} for n in range(HISTORY_LIMIT + 5)]
        kept = self.carry({"history.json": {"abc": {"items": items, "statistic": {}}}})
        self.assertEqual(len(kept["history.json"]["abc"]["items"]), HISTORY_LIMIT)
        self.assertEqual(kept["history.json"]["abc"]["items"][0]["uid"], "0")

    def test_nothing_to_carry_writes_nothing(self):
        """The first run ever, and every run that does not publish, has no previous report."""
        out = self.tmp()
        carry_history(self.tmp(), out)
        self.assertFalse((out / "history").exists())


class Pruning(TempDir):
    def report(self, history, test_case_uids):
        d = self.tmp()
        (d / "history").mkdir()
        (d / "data" / "test-cases").mkdir(parents=True)
        (d / "history" / "history.json").write_text(json.dumps(history), encoding="utf-8")
        for uid in test_case_uids:
            (d / "data" / "test-cases" / f"{uid}.json").write_text(
                json.dumps({"uid": uid}), encoding="utf-8")
        return d

    def test_a_test_case_the_report_no_longer_has_is_dropped(self):
        """Renaming a test case, or one parameter set of many, would otherwise leak forever."""
        d = self.report({"live": {"items": [{"uid": "a"}, {"uid": "old"}]},
                         "gone": {"items": [{"uid": "b"}]}}, ["a"])
        self.assertEqual(prune_history(d), 1)
        kept = json.loads((d / "history" / "history.json").read_text())
        self.assertEqual(list(kept), ["live"])

    def test_only_the_newest_item_decides(self):
        """An entry whose newest run is older than this report is dead however long its past."""
        d = self.report({"stale": {"items": [{"uid": "old"}, {"uid": "a"}]}}, ["a"])
        self.assertEqual(prune_history(d), 1)

    def test_a_report_with_no_history_is_left_alone(self):
        d = self.tmp()
        self.assertEqual(prune_history(d), 0)


class Environment(unittest.TestCase):
    def test_it_states_both_counts_and_what_the_run_may_claim(self):
        text = environment({
            "state": "INCOMPLETE", "commit": "abc1234",
            "coverage": {"expected": 8, "reported": 1},
            "counts": {"defined": 662, "executed": 533, "runs": 1574},
        }, {"generated": "2026-09-17T07:20:00Z"})
        self.assertIn("verdict=INCOMPLETE (1 of 8 suites reported)", text)
        self.assertIn("test_cases_defined=662", text)
        self.assertIn("test_cases_executed=533", text)
        self.assertIn("generated=2026-09-17T07:20:00Z", text)

    def test_no_key_has_a_space_in_it(self):
        """Allure splits the line on the first space, so a spaced key loses its value."""
        text = environment({"state": "PASSED", "commit": "a",
                            "coverage": {"expected": 1, "reported": 1},
                            "counts": {"defined": 1, "executed": 1, "runs": 1}}, {})
        for line in text.splitlines():
            self.assertNotIn(" ", line.partition("=")[0])


if __name__ == "__main__":
    unittest.main(verbosity=2)
