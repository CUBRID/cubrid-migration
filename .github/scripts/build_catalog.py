#!/usr/bin/env python3
"""Turn JUnit Open Test Report XML into the TC catalog of what the code defines."""
# A dry run and a real run emit the same format, so the two sides join on junit:uniqueId.

import argparse
import json
import sys
# The reports are this repository's own CI output, and ElementTree refuses external
# entities outright, so the XXE warning does not apply to this input.
from xml.etree import ElementTree  # nosec B405
from collections import Counter

from catalog import kind

NS = {
    "c": "https://schemas.opentest4j.org/reporting/core/0.2.0",
    "e": "https://schemas.opentest4j.org/reporting/events/0.2.0",
    "java": "https://schemas.opentest4j.org/reporting/java/0.2.0",
    "junit": "https://schemas.junit.org/open-test-reporting",
}

SOURCE_ROOT = {
    "unit": "tests/unit-test/src/test/java",
    "e2e": "tests/e2e/src/test/java",
}

PACKAGE_ROOT = {
    "unit": "com.cubrid.cubridmigration.",
    "e2e": "com.cmt.e2e.tests.",
}

DATABASES = {
    "cubrid": "CUBRID", "oracle": "Oracle", "mysql": "MySQL", "mariadb": "MariaDB",
    "mssql": "MSSQL", "informix": "Informix", "tibero": "Tibero",
}


def database(class_name, module):
    """The database a test case is about, or "common" when it is about none."""
    # Only the package under the module's own root: the com.cubrid. vendor namespace would
    # otherwise make every unit test look like a CUBRID one.
    root = PACKAGE_ROOT[module]
    if not class_name.startswith(root):
        return "common"
    for segment in class_name[len(root):].split("."):
        if segment.lower() in DATABASES:
            return DATABASES[segment.lower()]
    return "common"


def read_nodes(path):
    """Every started event, keyed by its report id, with its parent."""
    root = ElementTree.parse(path).getroot()  # nosec B314
    nodes = {}
    for started in root.findall("e:started", NS):
        uid = started.find("c:metadata/junit:uniqueId", NS)
        if uid is None:
            continue
        method = started.find("c:sources/java:methodSource", NS)
        nodes[started.get("id")] = {
            "parent": started.get("parentId"),
            "name": started.get("name"),
            "uniqueId": uid.text,
            "class": method.get("className") if method is not None else None,
            "method": method.get("methodName") if method is not None else None,
        }
    return nodes


def group_chain(nodes, node_id):
    """Display names of the containers above the test — its class and any @Nested groups."""
    chain = []
    cur = nodes[node_id]["parent"]
    while cur in nodes:
        chain.append(nodes[cur]["name"])
        cur = nodes[cur]["parent"]
    return list(reversed(chain))[1:]  # drop the engine node


def definitions(path, module, repo, commit):
    """Map each uniqueId to one catalog entry, per test case the report defines."""
    nodes = read_nodes(path)
    out = {}
    for node_id, node in nodes.items():
        written_as = kind(node["uniqueId"])
        if written_as is None or node["class"] is None:
            continue
        top = node["class"].split("$")[0].replace(".", "/")
        out[node["uniqueId"]] = {
            "id": node["uniqueId"],
            "type": module,
            "name": node["name"],
            "group": group_chain(nodes, node_id),
            "class": node["class"],
            "method": node["method"],
            "kind": written_as,
            "database": database(node["class"], module),
            "sourceUrl":
                f"https://github.com/{repo}/blob/{commit}/{SOURCE_ROOT[module]}/{top}.java",
        }
    return out


def selecting_job(specs):
    """Map each uniqueId to the CI job that selects it, and name every job that was asked."""
    owner, jobs = {}, []
    for spec in specs:
        job, sep, path = spec.partition("=")
        if not sep or not path:
            sys.exit(f"--selected expects JOB=OTR_XML, got: {spec}")
        jobs.append(job)
        for node in read_nodes(path).values():
            if kind(node["uniqueId"]):
                owner.setdefault(node["uniqueId"], job)
    return owner, jobs


def main():
    """Write the TC catalog for one module from its dry-run report."""
    ap = argparse.ArgumentParser()
    ap.add_argument("--type", required=True, choices=sorted(SOURCE_ROOT))
    ap.add_argument("--repo", required=True)
    ap.add_argument("--commit", required=True)
    ap.add_argument("--all", required=True, help="dry-run report covering every test in the module")
    ap.add_argument(
        "--selected",
        action="append",
        default=[],
        metavar="JOB=OTR_XML",
        help="dry-run report for one CI job's -Dtest pattern; repeat per job. "
        "Omit when a single job runs the whole module.",
    )
    ap.add_argument("--ci-job", help="job that runs the whole module, when --selected is not used")
    ap.add_argument("-o", "--out", required=True)
    args = ap.parse_args()
    if not args.selected and not args.ci_job:
        sys.exit("pass --ci-job, or --selected per job: the catalog has to say who runs each test")

    entries = definitions(args.all, args.type, args.repo, args.commit)
    if not entries:
        sys.exit(f"{args.all}: no test definitions found — refusing to write an empty catalog")

    # jobs comes from what the run was told to select, not from the entries: a pattern that
    # matched nothing still leaves a suite that owes a report.
    owner, jobs = selecting_job(args.selected) if args.selected else (None, [args.ci_job])
    for entry in entries.values():
        entry["ciJob"] = args.ci_job if owner is None else owner.get(entry["id"])

    rows = sorted(entries.values(), key=lambda e: e["id"])
    with open(args.out, "w", encoding="utf-8") as fh:
        json.dump({"type": args.type, "commit": args.commit, "jobs": jobs, "entries": rows},
                  fh, indent=2, ensure_ascii=False)
        fh.write("\n")
    kinds = Counter(r["kind"] for r in rows)
    print(
        f"{args.out}: {len(rows)} definitions "
        f"({kinds['test']} test, {kinds['parameterized']} parameterized, "
        f"{sum(1 for r in rows if r['ciJob'] is None)} not run by CI)"
    )


if __name__ == "__main__":
    main()
