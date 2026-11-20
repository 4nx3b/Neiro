# Neiro — a BitChord fork. GNU General Public License v3.0 or later.
import json
import sys

data = json.load(open(sys.argv[1]))
for bench in data.get("benchmarks", []):
    name = f"{bench.get('className', '').split('.')[-1]}.{bench.get('name', '')}"
    for metric, m in bench.get("metrics", {}).items():
        print(f"- {name} {metric}: min {m.get('minimum')} median {m.get('median')} max {m.get('maximum')}")
    for metric, m in bench.get("sampledMetrics", {}).items():
        print(f"- {name} {metric}: P50 {m.get('P50')} P90 {m.get('P90')} P99 {m.get('P99')}")
