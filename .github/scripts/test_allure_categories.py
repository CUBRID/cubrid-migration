#!/usr/bin/env python3
"""What each failure gets called in the Allure report."""
# These are the message shapes the e2e framework actually produces; change one there and this
# says so. Matching mirrors Allure's CategoriesPlugin: the status must be one the category lists
# and the message or trace must match its regex in full. Checked against 2.46.1.

import json
import pathlib
import re
import unittest

CATEGORIES = pathlib.Path(__file__).resolve().parents[2] / "tests/e2e/allure-categories.json"

# (expected category, status, message, trace)
SAMPLES = [
    ("Data loss", "failed",
     "Migration Report shows export/import count mismatch:\n"
     "  - Table: exported=120, imported=118", ""),
    ("Migration did not finish", "failed", "migration failed (exit=1)", ""),
    ("Migration did not finish", "failed", "migration timed out", ""),
    ("Migration did not finish", "failed", "stdout missing 'MIGRATION RESULT: SUCCESS'", ""),
    ("Fatal log on a successful run", "failed",
     "stderr contained fatal pattern: ERROR could not commit", ""),
    ("Output missing", "failed", "CMT did not produce a dump output directory: /work/out", ""),
    ("Output missing", "failed",
     "Cannot capture dump snapshot: CMT output not found at /work/out", ""),
    ("Output missing", "failed",
     "Expected dump files missing from CMT output:\n  - main_schema_schema.sql", ""),
    ("Output missing", "failed",
     "Migration Report summary block not found in CMT stdout", ""),
    ("Snapshot drift", "failed",
     "Snapshot mismatch: /snap/rowcounts.txt\nFirst difference at line 3:\n"
     "  expected: 10\n  actual:   9\n"
     "expected 42 lines, actual 42 lines; both are attached.\n"
     "To accept the new snapshot, re-run with -Dsnapshot.update=true.\n", ""),
    ("Snapshot not recorded", "failed",
     "Snapshot missing: /snap/rowcounts.txt\n"
     "Run with -Dsnapshot.update=true to capture an initial snapshot.", ""),
    ("Snapshot not recorded", "failed",
     "Dump snapshot missing: /snap/dump.txt\nRun with -Dsnapshot.update=true to capture.", ""),
    ("Snapshot not recorded", "failed", "Failed to read snapshot: /snap/dump.txt", ""),
    # The run's own states, written by allure_results.py for a test case that never reported.
    ("Not reported by CI", "unknown", "its CI job reported nothing", ""),
    ("Not reported by CI", "unknown",
     "its CI job stopped early, so nothing is known about this test case", ""),
    ("Not reported by CI", "unknown", "the run does not account for this test case", ""),
    ("Blocked before it ran", "broken", "something before it failed, so it never ran", ""),
    ("Database not ready", "broken", "seed failed",
     "com.cmt.e2e.framework.db.init.DatabaseInitializationException: seed failed\n"
     "\tat com.cmt.e2e.framework.db.init.MySqlDatabaseInitializer"
     ".initialize(MySqlDatabaseInitializer.java:67)"),
    ("Database not ready", "broken", "script failed",
     "com.cmt.e2e.framework.db.init.SqlRunnerException: script failed\n"
     "\tat com.cmt.e2e.framework.db.init.ClasspathSqlRunner.run(ClasspathSqlRunner.java:93)"),
]

# A failure no category claims is not lost: Allure keeps its own Product defects / Test defects
# and puts it there. Verified against 2.46.1 — a custom file adds buckets, it does not replace.
UNCLAIMED = [
    ("failed", "some brand new assertion message nobody wrote a bucket for", ""),
    ("broken", "connection refused", "java.net.ConnectException: connection refused"),
]


def load():
    with open(CATEGORIES, encoding="utf-8") as fh:
        return json.load(fh)


def claims(category, status, message, trace):
    if category.get("matchedStatuses") and status not in category["matchedStatuses"]:
        return False
    for key, value in (("messageRegex", message), ("traceRegex", trace)):
        if key in category and not re.fullmatch(category[key], value, re.DOTALL):
            return False
    return True


def matching(status, message, trace):
    return [c["name"] for c in load() if claims(c, status, message, trace)]


class Categories(unittest.TestCase):
    def test_every_category_is_usable(self):
        for category in load():
            with self.subTest(category=category.get("name")):
                self.assertTrue(category.get("name"), "a category needs a name")
                self.assertTrue(
                    {"messageRegex", "traceRegex"} & set(category),
                    "a category with no regex would claim every failure",
                )
                for key in ("messageRegex", "traceRegex"):
                    if key in category:
                        re.compile(category[key])

    def test_each_failure_lands_in_its_category(self):
        for expected, status, message, trace in SAMPLES:
            with self.subTest(expected=expected, message=message[:60]):
                self.assertEqual(matching(status, message, trace), [expected])

    def test_no_category_is_unused(self):
        """A bucket nothing can reach is a bucket that will not be there when it is needed."""
        used = {expected for expected, _, _, _ in SAMPLES}
        self.assertEqual({c["name"] for c in load()} - used, set())

    def test_an_unclaimed_failure_is_left_to_allure(self):
        for status, message, trace in UNCLAIMED:
            with self.subTest(message=message[:60]):
                self.assertEqual(matching(status, message, trace), [])


if __name__ == "__main__":
    unittest.main(verbosity=2)
