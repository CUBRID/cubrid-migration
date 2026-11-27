#!/usr/bin/env python3
"""Write the Allure results for every test case the catalog knows, not only those that ran."""

import argparse
import json
import pathlib
import shutil
import uuid
from collections import Counter

from catalog import definition, load

# allure generate turns an empty input into a valid all-green report, so a test case that did not
# report has to be written out as one that did not. Nothing here is ever "passed".
SYNTHETIC = {
    "not-in-ci": ("skipped", "no CI job selects this test case"),
    "not-selected": ("unknown", "its CI job ran but did not select this test case"),
    "no-report": ("unknown", "its CI job reported nothing"),
    "unknown": ("unknown", "its CI job stopped early, so nothing is known about this test case"),
    "blocked": ("broken", "something before it failed, so it never ran"),
}
UNACCOUNTED = ("unknown", "the run does not account for this test case")

HISTORY_LIMIT = 20


def read_json(path):
    """One JSON file."""
    return json.loads(path.read_text(encoding="utf-8"))


def write_json(path, data):
    """One JSON file, as Allure reads them."""
    path.write_text(json.dumps(data), encoding="utf-8")


def label(name, value):
    """One Allure label."""
    return {"name": name, "value": value}


def source_link(entry):
    """A link to the test case's own source, at the commit that was verified."""
    return {"name": "source", "url": entry["sourceUrl"], "type": "custom"}


def junit_id(result):
    """The JUnit uniqueId a result carries, which is what the catalog is keyed by."""
    return next((x["value"] for x in result.get("labels", [])
                 if x.get("name") == "junit.platform.uniqueid"), "")


def stamp(artifacts, suites, catalog, out):
    """Copy each suite's results into one directory, marked with the job that produced them."""
    reported = set()
    for suite in suites:
        results = 0
        for path in sorted((artifacts / f"allure-{suite}").glob("*")):
            if "-attachment" in path.name:
                shutil.copy(path, out / path.name)
                continue
            if not path.name.endswith("-result.json"):
                continue
            results += 1
            result = read_json(path)
            kept = [x for x in result.get("labels", []) if x.get("name") != "parentSuite"]
            result["labels"] = kept + [label("parentSuite", suite)]
            unique_id = definition(junit_id(result))
            entry = catalog.get(unique_id)
            if entry:
                reported.add(unique_id)
                result["labels"].append(label("tag", entry["database"]))
                result["links"] = result.get("links", []) + [source_link(entry)]
            write_json(out / path.name, result)
        print(f"  {suite}: {results} results")
    return reported


def synthesize(catalog, states, reported, out, notes=None):
    """Write a result for every test case that did not report, so the report can still show it."""
    made = Counter()
    for unique_id, entry in sorted(catalog.items()):
        if unique_id in reported:
            continue
        state = states.get(unique_id)
        status, message = SYNTHETIC.get(state, UNACCOUNTED)
        # Keyed by database, because a test case that fell out of CI by accident would otherwise
        # borrow the excuse of one that is meant to be out.
        if state == "not-in-ci" and (note := (notes or {}).get(entry["database"])):
            message = f"{message}. {note}"
        made[status] += 1
        group = entry.get("group") or []
        labels = [label("parentSuite", entry.get("ciJob") or "not run by CI"),
                  label("package", entry["class"]),
                  label("testClass", entry["class"]),
                  label("testMethod", entry["method"]),
                  label("tag", entry["database"]),
                  label("tag", state or "unaccounted"),
                  label("junit.platform.uniqueid", unique_id)]
        # The adapter takes suite from the test's own declaring class, so a @Nested test case
        # sits under the innermost name. Anything else files it where no real result lands.
        labels += [label("suite", group[-1])] if group else []
        uid = str(uuid.uuid5(uuid.NAMESPACE_URL, unique_id))
        write_json(out / f"{uid}-result.json", {
            "uuid": uid,
            # Allure keys history by testCaseId, so without it the same test case owns one
            # lineage for the runs it reported and another for the runs it did not. No start or
            # stop: a run's duration is max(stop) - min(start), which a zero would make an age.
            "testCaseId": unique_id,
            "name": entry["name"],
            "fullName": f'{entry["class"]}.{entry["method"]}',
            "status": status,
            "statusDetails": {"message": message},
            "stage": "finished",
            "labels": labels,
            "links": [source_link(entry)],
            "steps": [],
            "attachments": [],
            "parameters": [],
        })
    return made


def carry_history(source, out):
    """Copy the previous report's history forward, keeping only the newest points."""
    paths = sorted(source.glob("*.json"))
    if paths:
        (out / "history").mkdir(exist_ok=True)
    for path in paths:
        data = read_json(path)
        if isinstance(data, list):  # a trend, newest point first
            data = data[:HISTORY_LIMIT]
        else:
            for entry in data.values():
                entry["items"] = entry.get("items", [])[:HISTORY_LIMIT]
        write_json(out / "history" / path.name, data)
    return len(paths)


def prune_history(report):
    """Drop the history of test cases the report no longer has, run on a generated report."""
    # Allure keeps an entry forever once it exists, so a renamed test case leaves its history
    # behind. An entry is live when this run's result is at its front, which every test case the
    # catalog knows produces.
    path = report / "history" / "history.json"
    if not path.exists():
        return 0
    here = {read_json(f)["uid"] for f in (report / "data" / "test-cases").glob("*.json")}
    history = read_json(path)
    live = {k: v for k, v in history.items()
            if v.get("items") and v["items"][0].get("uid") in here}
    write_json(path, live)
    return len(history) - len(live)


def environment(status, extra):
    """The run's own numbers, which the report shows but cannot work out for itself."""
    counts, coverage = status["counts"], status["coverage"]
    lines = {
        "verdict": f"{status['state']} "
                   f"({coverage['reported']} of {coverage['expected']} suites reported)",
        "commit": status["commit"],
        "test_cases_defined": counts["defined"],
        "test_cases_executed": counts["executed"],
        "executions": counts["runs"],
        **extra,
    }
    return "".join(f"{k}={v}\n" for k, v in lines.items())


def main():
    """Assemble one run's results, from what ran and from what the catalog says exists."""
    ap = argparse.ArgumentParser()
    ap.add_argument("--artifacts", help="directory holding the allure-<suite> dirs")
    ap.add_argument("--suite", action="append", help="CI job name; repeat per job")
    ap.add_argument("--catalog", action="append")
    ap.add_argument("--status")
    ap.add_argument("--out")
    ap.add_argument("--env", action="append", default=[], metavar="KEY=VALUE")
    ap.add_argument("--categories", help="allure categories file to copy in")
    ap.add_argument("--not-run-note", action="append", default=[], metavar="DATABASE=WHY",
                    help="why CI runs none of this database's test cases; repeat per database")
    ap.add_argument("--history", help="the previous report's history directory, to build a trend")
    ap.add_argument("--prune", metavar="REPORT",
                    help="instead: drop dead history from a report allure has just generated")
    args = ap.parse_args()

    if args.prune:
        print(f"  history pruned: {prune_history(pathlib.Path(args.prune))} test cases dropped")
        return
    if missing := [n for n in ("artifacts", "suite", "catalog", "status", "out")
                   if not getattr(args, n)]:
        ap.error("--" + ", --".join(missing) + " are required unless --prune is given")

    with open(args.status, encoding="utf-8") as fh:
        status = json.load(fh)
    catalog = load(args.catalog)
    out = pathlib.Path(args.out)
    out.mkdir(parents=True, exist_ok=True)

    # A suite that ran again and uploaded nothing still has the previous attempt's results on
    # disk. The status already dropped them; copying them would publish that attempt as this one.
    adopted = {s["name"] for s in status["suites"] if s["reported"]}
    reported = stamp(pathlib.Path(args.artifacts),
                     [s for s in args.suite if s in adopted], catalog, out)
    made = synthesize(catalog, status["byTest"], reported, out,
                      dict(pair.partition("=")[::2] for pair in args.not_run_note))
    if args.history:
        print(f"  history carried: {carry_history(pathlib.Path(args.history), out)} files")
    extra = dict(pair.partition("=")[::2] for pair in args.env)
    (out / "environment.properties").write_text(environment(status, extra), encoding="utf-8")
    if args.categories:
        shutil.copy(args.categories, out / "categories.json")
    print(f"  {len(reported)} test cases reported, "
          f"{sum(made.values())} written from the catalog: {dict(sorted(made.items()))}")


if __name__ == "__main__":
    main()
