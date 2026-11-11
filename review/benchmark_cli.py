#!/usr/bin/env python3
"""Repeatable CLI benchmark. All generated test data and results live outside the repository."""
from __future__ import annotations

import argparse
import json
import os
import platform
import subprocess
import tempfile
from pathlib import Path

SYNTHETIC_FILES = 5000
JAVA_BYTES = 40_000


def generate(root: Path) -> dict:
    sources = root / "large" / "src" / "main" / "java" / "de" / "bench"
    sources.mkdir(parents=True)
    payload = "x" * (JAVA_BYTES - 125)
    total = 0
    for n in range(SYNTHETIC_FILES):
        label = "Risiko" if n % 200 == 0 else "Andere"
        name = f"{label}{n:05d}"
        path = sources / f"{name}.java"
        body = f'package de.bench;\npublic class {name} {{\n'
        body += f'    public static final String TEXT = "{payload}";\n}}\n'
        path.write_text(body, encoding="utf-8")
        total += path.stat().st_size
    ddl = root / "large" / "tables.ddl"
    with ddl.open("w", encoding="utf-8") as out:
        for n in range(500):
            out.write(f"CREATE TABLE TEST.T{n:04d} (ID BIGINT NOT NULL, ATTR1 VARCHAR(100), "
                      "ATTR2 INTEGER, ATTR3 DECIMAL(10,2), ATTR4 DATE);\n")
    return {"java_files": SYNTHETIC_FILES, "java_bytes": total, "ddl_tables": 500,
            "ddl_bytes": ddl.stat().st_size}


def measure(java: str, jar: Path, cwd: Path, name: str, args: list[str], root: Path) -> dict:
    output = root / "outputs" / name
    output.mkdir(parents=True)
    cmd = ["/usr/bin/time", "-f", "%e %M", "-o", str(root / (name + ".time")),
           java, "-Xmx512m", "-jar", str(jar), *args, f"output={output}"]
    completed = subprocess.run(cmd, cwd=cwd, stdout=subprocess.PIPE,
                               stderr=subprocess.STDOUT, text=True, check=False)
    times = (root / (name + ".time")).read_text(encoding="utf-8").strip().split()
    files = list((output / "devtoolbox").rglob("*")) if (output / "devtoolbox").exists() else []
    upload = [p for p in files if p.is_file()]
    return {"name": name, "exit_code": completed.returncode,
            "duration_seconds": float(times[0]) if len(times) >= 2 else None,
            "max_rss_kb": int(times[1]) if len(times) >= 2 else None,
            "upload_files": len(upload), "output_bytes": sum(p.stat().st_size for p in upload),
            "estimated_tokens_total": sum(p.stat().st_size for p in upload) // 4,
            "last_log_lines": completed.stdout.splitlines()[-8:]}


def commands(repo: Path, large: Path) -> dict[str, list[str]]:
    return {
        "phase_testprojekt": [
            "phase-spec", f"base={repo / 'testprojekt'}", "terms=Risiko",
            "phases=READ,PROCESS,WRITE", f"ddl={repo / 'testprojekt/ddl/altbestand.ddl'}",
            f"targetModel={repo / 'testprojekt-ziel/zielmodell'}", "scope=hits"],
        "implementation_testprojekt": [
            "implementation", f"standards={repo / 'migrationsprojekte/testziel/standards'}",
            f"spec={repo / 'testprojekt-ziel/spezifikation/risiko-spezifikation.pdf'}",
            "entity=Risiko", "phases=READ,PROCESS,WRITE",
            f"targetModel={repo / 'testprojekt-ziel/zielmodell'}"],
        "mapping_doc_testprojekt": [
            "mapping-doc", f"target={repo / 'testprojekt-ziel'}", "terms=Vertrag",
            "jira=SACH-4711", f"targetModel={repo / 'testprojekt-ziel/zielmodell'}",
            f"mappingTemplates={repo / 'testprojekt-ziel/mapping-vorlagen'}", "scope=hits"],
        "phase_large": [
            "phase-spec", f"base={large / 'src'}", "terms=Risiko",
            "phases=READ", f"ddl={large / 'tables.ddl'}", "scope=hits"],
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--baseline", type=Path, required=True)
    parser.add_argument("--baseline-repo", type=Path, required=True)
    parser.add_argument("--current", type=Path, required=True)
    parser.add_argument("--current-repo", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--java", default="java")
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="devtoolbox-review-", dir=args.output) as work:
        data = Path(work)
        generated = generate(data)
        results = []
        for variant, jar, repo in (("main", args.baseline, args.baseline_repo),
                                   ("review", args.current, args.current_repo)):
            for scenario, command in commands(repo.resolve(), data / "large").items():
                results.append(measure(args.java, jar.resolve(), repo.resolve(),
                                       f"{variant}_{scenario}", command, data))
        cpuinfo = Path("/proc/cpuinfo")
        model = next((line.split(":", 1)[1].strip() for line in cpuinfo.read_text().splitlines()
                      if line.startswith("model name")), "unbekannt") if cpuinfo.exists() else "unbekannt"
        meminfo = Path("/proc/meminfo")
        memory = next((line.split(":", 1)[1].strip() for line in meminfo.read_text().splitlines()
                       if line.startswith("MemTotal")), "unbekannt") if meminfo.exists() else "unbekannt"
        java_version = subprocess.run([args.java, "-version"], capture_output=True, text=True, check=True)
        environment = {"operating_system": platform.platform(),
                       "cpu_count": os.cpu_count(), "cpu_model": model,
                       "host_memory": memory, "java_version": java_version.stderr.splitlines()[0]}
        report = {"environment": environment, "generated": generated,
                  "java_heap_limit": "512m", "results": results}
        (args.output / "results.json").write_text(json.dumps(report, indent=2, ensure_ascii=False),
                                                  encoding="utf-8")
        print(json.dumps(report, indent=2, ensure_ascii=False))
        failed = [r["name"] for r in results if r["exit_code"] != 0]
        if failed:
            raise SystemExit("CLI benchmark failed: " + ", ".join(failed))


if __name__ == "__main__":
    main()
