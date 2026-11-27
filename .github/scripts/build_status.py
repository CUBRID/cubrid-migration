#!/usr/bin/env python3
"""Join one run's test results onto the TC catalog and decide what the run may claim."""
# Absence is never success: a suite that reported nothing is NO_DATA, and a suite whose job went
# green without executing a single test is EMPTY.

import argparse
import json
import sys
# The reports are this repository's own CI output, and ElementTree refuses external
# entities outright, so the XXE warning does not apply to this input.
from xml.etree import ElementTree  # nosec B405
from collections import Counter

from catalog import INVOCATION, kind, load

NS = {
    "c": "https://schemas.opentest4j.org/reporting/core/0.2.0",
    "e": "https://schemas.opentest4j.org/reporting/events/0.2.0",
    "junit": "https://schemas.junit.org/open-test-reporting",
}

SEVERITY = {"SKIPPED": 0, "SUCCESSFUL": 1, "ABORTED": 2, "FAILED": 3}

TC_STATE = {"SUCCESSFUL": "passed", "FAILED": "failed", "ABORTED": "failed", "SKIPPED": "skipped"}


def worse(current, status):
    """The status a definition takes when one more of its invocations reports."""
    if current is None:
        return status
    return max(current, status, key=lambda s: SEVERITY.get(s, 4))


def uniqueids(root):
    """The uniqueId each started event announced, keyed by the id its finished event repeats."""
    found = {}
    for started in root.findall("e:started", NS):
        meta = started.find("c:metadata", NS)
        uid = meta.find("junit:uniqueId", NS) if meta is not None else None
        if uid is not None:
            found[started.get("id")] = uid.text
    return found


def read_results(path):
    """Map each uniqueId to its outcome, with failed containers and invocation counts."""
    # A JUnit container finishes SUCCESSFUL even when one of its children failed, so where a
    # definition has invocations the invocations decide — except a failure of the node itself,
    # which is no invocation's and still counts.
    root = ElementTree.parse(path).getroot()  # nosec B314
    uid_of = uniqueids(root)
    outcomes, blocked_roots, invocations, own = {}, [], Counter(), {}
    for finished in root.findall("e:finished", NS):
        uid = uid_of.get(finished.get("id"))
        if uid is None:
            continue
        result = finished.find("c:result", NS)
        status = result.get("status") if result is not None else "UNKNOWN"
        definition, sep, _ = uid.partition(INVOCATION)
        if sep:
            invocations[definition] += 1
            outcomes[definition] = worse(outcomes.get(definition), status)
        elif kind(uid):
            own[uid] = status
        elif status in ("FAILED", "ABORTED"):
            blocked_roots.append(uid)  # a class or @Nested group that blew up in setup
    for uid, status in own.items():
        if status in ("FAILED", "ABORTED"):
            outcomes[uid] = worse(outcomes.get(uid), status)
        else:
            outcomes.setdefault(uid, status)  # disabled, or arguments that never resolved
    return outcomes, blocked_roots, invocations


def suite_state(job_conclusion, present, counts, unjoined):
    """Decide what this suite is allowed to claim."""
    # The order is the point: every branch above PASSED names something the suite does not know.
    if job_conclusion == "cancelled":
        return "CANCELLED"
    if not present:
        return "NO_DATA"
    if counts["blocked"]:
        return "BLOCKED"
    if counts["failed"]:
        return "FAILED"
    if unjoined:
        return "UNMATCHED"  # the catalog and the run describe different code
    if not counts["passed"]:
        return "EMPTY"
    if job_conclusion != "success":
        return "INCOMPLETE"  # what it never got to is missing, not fine
    return "PASSED_WITH_SKIPS" if counts["skipped"] else "PASSED"


def run_state(suites):
    """PASSED is reserved for a run where every expected suite reported and passed."""
    states = {s["state"] for s in suites}
    if not states or states <= {"NO_DATA"}:
        return "NO_DATA"
    if "FAILED" in states:
        return "FAILED"
    if "UNMATCHED" in states:
        return "UNMATCHED"
    if "BLOCKED" in states or "EMPTY" in states:
        return "BLOCKED"
    if "CANCELLED" in states:
        return "CANCELLED"
    if "NO_DATA" in states or "INCOMPLETE" in states:
        return "INCOMPLETE"
    if "PASSED_WITH_SKIPS" in states:
        return "PASSED_WITH_SKIPS"
    return "PASSED"


def load_catalog(paths):
    """Read every catalog file into one uniqueId to entry map."""
    catalog = load(paths)
    if not catalog:
        sys.exit("catalog is empty — refusing to publish a status for an unknown test set")
    return catalog


def reject_unexpected(expected, results, catalog):
    """Stop when the catalog, the results and the expected suites disagree."""
    # A test case attributed to a suite nobody expects lands in no suite's counts, so it would
    # leave no trace in the coverage or the run state. That is a configuration fault.
    if stray := sorted(set(results) - expected):
        sys.exit(f"results given for suites that are not expected: {', '.join(stray)}")
    if stray := sorted({e["ciJob"] for e in catalog.values() if e.get("ciJob")} - expected):
        sys.exit(f"the catalog assigns test cases to unexpected suites: {', '.join(stray)}")


def attribute(catalog, results, blocked_roots):
    """Attribute every test case to a suite, and count results that joined to none."""
    by_tc, unjoined = {}, Counter()
    for suite, outcomes in results.items():
        for uid, status in outcomes.items():
            if uid in catalog:
                by_tc[uid] = {"suite": suite, "state": TC_STATE.get(status, "failed")}
            else:
                unjoined[suite] += 1
    for suite, roots in blocked_roots.items():
        # Only what the suite owns: a root as broad as the engine node prefixes every uniqueId
        # there is, and would blame this suite for another module's test cases.
        for uid, entry in catalog.items():
            if (uid not in by_tc and entry.get("ciJob") == suite
                    and any(uid.startswith(root + "/") for root in roots)):
                by_tc[uid] = {"suite": suite, "state": "blocked"}
    for uid, entry in catalog.items():
        if uid in by_tc:
            continue
        job = entry.get("ciJob")
        state = "not-in-ci" if job is None else "not-selected" if job in results else "no-report"
        by_tc[uid] = {"suite": job, "state": state}
    return by_tc, unjoined


def summarize(expect, by_tc, jobs, results, unjoined):
    """One record per suite CI was expected to report."""
    suites = []
    for name in expect:
        tallied = Counter(tc["state"] for tc in by_tc.values() if tc["suite"] == name)
        counts = {k: tallied[k] for k in ("passed", "failed", "skipped", "blocked")}
        concl, reported, unmatched = jobs.get(name), name in results, unjoined[name]
        suites.append({
            "name": name,
            "jobConclusion": concl,
            "reported": reported,
            "state": suite_state(concl, reported, counts, unmatched),
            "counts": counts,
            "unjoined": unmatched,
        })
    return suites


def forget_what_unfinished_suites_never_ran(by_tc, suites):
    """A suite that stopped early cannot claim it chose not to run the rest."""
    unfinished = {s["name"] for s in suites if s["jobConclusion"] != "success"}
    for tc in by_tc.values():
        if tc["state"] == "not-selected" and tc["suite"] in unfinished:
            tc["state"] = "unknown"


def total_runs(results, invocations):
    """How many times tests executed, which is what the Allure report counts."""
    return sum(sum(inv.values()) + len(results[suite].keys() - inv.keys())
               for suite, inv in invocations.items())


def summary_markdown(status):
    """The verdict and the numbers, for the job summary a reviewer reads without downloading."""
    counts, coverage = status["counts"], status["coverage"]
    rows = "\n".join(
        f"| `{s['name']}` | {s['state']} | {s['counts']['passed']} | {s['counts']['failed']}"
        f" | {s['counts']['skipped']} | {s['counts']['blocked']} |"
        for s in status["suites"])
    tally = ", ".join(f"{v} {k}" for k, v in counts["byState"].items())
    return (f"### {status['state']} — {coverage['reported']} of {coverage['expected']}"
            f" suites reported\n\n"
            f"{counts['defined']} test cases defined, {counts['executed']} executed,"
            f" {counts['runs']} runs\n\n"
            "| suite | state | passed | failed | skipped | blocked |\n"
            "|---|---|---|---|---|---|\n" + rows + f"\n\n{tally}\n")


def main():
    """Write the status the report is built from, from one run's catalogs and reports."""
    ap = argparse.ArgumentParser()
    ap.add_argument("--catalog", action="append", required=True)
    ap.add_argument("--result", action="append", default=[], metavar="SUITE=OTR_XML")
    ap.add_argument("--jobs", required=True, help='JSON array of {"name":…,"conclusion":…}')
    ap.add_argument("--expect", action="append", required=True, help="suite name CI must report")
    ap.add_argument("--commit", required=True)
    ap.add_argument("--run-id", required=True)
    ap.add_argument("--run-attempt", required=True)
    ap.add_argument("--run-url", required=True)
    ap.add_argument("--run-started-at", required=True)
    ap.add_argument("-o", "--out", required=True)
    ap.add_argument("--summary", help="file to append a markdown summary to")
    args = ap.parse_args()

    catalog = load_catalog(args.catalog)
    results, blocked_roots, invocations = {}, {}, {}
    for spec in args.result:
        suite, _, path = spec.partition("=")
        try:
            results[suite], blocked_roots[suite], invocations[suite] = read_results(path)
        except ElementTree.ParseError as err:
            # The report is streamed and closed only when the test plan finishes, so a JVM that
            # died mid-run leaves one that cannot be read. The suite then reported nothing.
            print(f"::warning::{path}: {err}; {suite} reported nothing that can be read")
    reject_unexpected(set(args.expect), results, catalog)
    with open(args.jobs, encoding="utf-8") as fh:
        jobs = {j["name"]: j.get("conclusion") for j in json.load(fh)}

    by_tc, unjoined = attribute(catalog, results, blocked_roots)
    suites = summarize(args.expect, by_tc, jobs, results, unjoined)
    forget_what_unfinished_suites_never_ran(by_tc, suites)

    tallies = Counter(tc["state"] for tc in by_tc.values())
    status = {
        "commit": args.commit,
        "run": {
            "id": args.run_id,
            "attempt": args.run_attempt,
            "url": args.run_url,
            "startedAt": args.run_started_at,
        },
        "state": run_state(suites),
        "coverage": {
            "expected": len(args.expect),
            "reported": sum(1 for s in suites if s["reported"]),
        },
        "counts": {
            "defined": len(catalog),
            "executed": tallies["passed"] + tallies["failed"],  # a skip did not execute
            "runs": total_runs(results, invocations),
            "byState": dict(sorted(tallies.items())),
        },
        "suites": suites,
        "byTest": {uid: tc["state"] for uid, tc in sorted(by_tc.items())},
    }
    if len(status["byTest"]) != len(catalog):
        sys.exit("every test case must end up with a state — this one lost some")
    with open(args.out, "w", encoding="utf-8") as fh:
        json.dump(status, fh, indent=2, ensure_ascii=False)
        fh.write("\n")
    if args.summary:
        with open(args.summary, "a", encoding="utf-8") as fh:
            fh.write(summary_markdown(status))
    print(
        f"{args.out}: run {status['state']}, "
        f"{status['coverage']['reported']}/{status['coverage']['expected']} suites reported, "
        f"{status['counts']['byState']}, "
        f"{sum(unjoined.values())} results matched no catalog entry"
    )


if __name__ == "__main__":
    main()
