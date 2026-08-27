#!/usr/bin/env python3
"""
gen_datagen_csvs.py
====================

Reads a JSON / JSONL file and produces the directory-of-CSV-files structure
required by the DataGen tool (see README.md) to statistically reproduce it.

Usage:
    python3 gen_datagen_csvs.py <input.jsonl|.json> <out_dir> \
        [--cutoff 100] [--min-prevalence 0.05] [--max-array-depth 4]

Pure standard-library implementation (json, csv, gzip, collections, re,
statistics, argparse, os).

See the report printed at the end of a run for a summary of decisions made
and fields that need human attention ("questionable cardinality").
"""

import argparse
import csv
import gzip
import io
import json
import math
import os
import re
import statistics
import sys
from collections import Counter, defaultdict
from datetime import datetime, timezone

# --------------------------------------------------------------------------
# Data ingestion
# --------------------------------------------------------------------------


def load_records(path):
    with open(path, "r", encoding="utf-8") as f:
        text = f.read()
    text_stripped = text.strip()
    if not text_stripped:
        return []
    # Try JSON array / single object first
    if text_stripped[0] in "[{":
        try:
            data = json.loads(text_stripped)
            if isinstance(data, list):
                return data
            if isinstance(data, dict):
                return [data]
        except json.JSONDecodeError:
            pass
    # Fall back to JSONL (one JSON value per line)
    records = []
    for line in text_stripped.splitlines():
        line = line.strip()
        if not line:
            continue
        records.append(json.loads(line))
    return records


# --------------------------------------------------------------------------
# Schema tree
# --------------------------------------------------------------------------


class Node:
    """Represents one field position in the document tree.

    A Node is either:
      - a scalar leaf (has `stats`)
      - an object (has `children` dict, keyed by local field name)
      - an array (has `elem`, a Node representing the element schema, plus
        `lengths`, a list of observed array lengths)
    """

    __slots__ = (
        "kind",
        "children",
        "elem",
        "lengths",
        "occ",
        "types",
        "values",
        "num_min",
        "num_max",
        "samples",
    )

    def __init__(self):
        self.kind = None  # 'object' | 'array' | 'scalar'
        self.children = {}
        self.elem = None
        self.lengths = []
        self.occ = 0
        self.types = Counter()
        self.values = Counter()  # str(value) -> count, capped
        self.num_min = None
        self.num_max = None
        self.samples = []

    def as_object(self):
        if self.kind is None:
            self.kind = "object"
        return self

    def as_array(self):
        if self.kind is None:
            self.kind = "array"
        if self.elem is None:
            self.elem = Node()
        return self

    def as_scalar(self):
        if self.kind is None:
            self.kind = "scalar"
        return self


MAX_DISTINCT_TRACKED = 200000  # safety cap for Counter growth (high so ratio math stays accurate)


def merge_value(node, value):
    """Merge a single JSON value (already known to occupy this node) in."""
    if isinstance(value, dict):
        node.as_object()
        for k, v in value.items():
            child = node.children.setdefault(k, Node())
            merge_value(child, v)
    elif isinstance(value, list):
        node.as_array()
        node.lengths.append(len(value))
        for el in value:
            merge_value(node.elem, el)
    else:
        node.as_scalar()
        if value is None:
            return
        node.occ += 1
        tname = type(value).__name__
        node.types[tname] += 1
        if isinstance(value, bool):
            pass
        elif isinstance(value, (int, float)):
            if node.num_min is None or value < node.num_min:
                node.num_min = value
            if node.num_max is None or value > node.num_max:
                node.num_max = value
        if len(node.values) < MAX_DISTINCT_TRACKED:
            node.values[value if isinstance(value, (str, bool)) else value] += 1
        if len(node.samples) < 5:
            node.samples.append(value)


def build_schema(records):
    root = Node()
    root.as_object()
    for r in records:
        if isinstance(r, dict):
            merge_value(root, r)
    return root


# --------------------------------------------------------------------------
# Date detection helpers
# --------------------------------------------------------------------------

DATE_RE = re.compile(r"^\d{4}-\d{2}-\d{2}$")
DATETIME_T_RE = re.compile(r"^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}")
DATETIME_SPACE_RE = re.compile(r"^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}")
URL_RE = re.compile(r"^https?://", re.IGNORECASE)
HEX_HASH_RE = re.compile(r"[0-9a-fA-F]{16,}")


def detect_string_date_kind(values):
    """values: list of strings. Returns 'date', 'datetime' or None."""
    if not values:
        return None
    sample = values[:50]
    if all(DATE_RE.match(v) for v in sample):
        return "date"
    if all(DATETIME_T_RE.match(v) or DATETIME_SPACE_RE.match(v) for v in sample):
        return "datetime"
    return None


def normalize_datetime_str(v):
    """Convert a variety of datetime string formats to 'YYYY-MM-DDTHH:MM:SS'."""
    v = v.strip()
    if v.endswith("Z"):
        v = v[:-1]
    v = v.replace(" ", "T")
    # Truncate fractional seconds / timezone offsets, keep date+time only
    m = re.match(r"(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2})", v)
    if m:
        return m.group(1)
    return v


def is_epoch_ms(values):
    """Heuristic: nearly all values are large in magnitude (>~1.6 years worth
    of milliseconds, i.e. real epoch-ms timestamps have 11-13 digits) AND all
    fall in a plausible calendar range (years 1900-2100). This intentionally
    requires a magnitude check (not just a min/max bounds check) because a
    small-value integer field (e.g. a price or count) can trivially satisfy
    a wide min<=x<=max bounds test without actually being a timestamp."""
    if not values:
        return False
    MAGNITUDE_THRESHOLD = 5e10  # ~1.6 years in ms
    lo = -2208988800000  # 1900-01-01
    hi = 4102444800000  # 2100-01-01
    large = [v for v in values if abs(v) > MAGNITUDE_THRESHOLD]
    if len(large) / len(values) < 0.95:
        return False
    return all(lo <= v <= hi for v in large)


def epoch_ms_to_iso(ms):
    dt = datetime.fromtimestamp(ms / 1000.0, tz=timezone.utc)
    return dt.strftime("%Y-%m-%dT%H:%M:%S")


# --------------------------------------------------------------------------
# Field classification
# --------------------------------------------------------------------------


class Field:
    """A flattened scalar field at a given schema 'level' (object or array-element)."""

    def __init__(self, path, node):
        self.path = path  # dotted path relative to this level
        self.node = node
        self.classification = None  # set by classify()
        self.csv_value = None  # the literal / @DIRECTIVE to emit (for non-categorical)
        self.categorical_values = None  # Counter of value(str) -> count (for categorical)
        self.notes = []


def classify_field(path, node, cutoff, min_prevalence, level_record_count, report):
    occ = node.occ
    prevalence = occ / level_record_count if level_record_count else 0
    f = Field(path, node)

    if occ == 0:
        f.classification = "NULL_ONLY"
        report.skipped_null.append(path)
        return f

    if prevalence < min_prevalence:
        f.classification = "LOW_PREVALENCE"
        report.skipped_low_prevalence.append((path, prevalence, occ, level_record_count))
        return f

    non_null_types = node.types
    is_bool = non_null_types.get("bool", 0) == occ
    is_numeric = (
        non_null_types.get("int", 0) + non_null_types.get("float", 0) + non_null_types.get("bool", 0) == occ
        and non_null_types.get("bool", 0) == 0
    )
    is_string = non_null_types.get("str", 0) == occ

    distinct = len(node.values)

    if is_bool:
        f.classification = "CATEGORICAL"
        f.categorical_values = Counter({("true" if k else "false"): v for k, v in node.values.items()})
        return f

    # --- Date / datetime detection -------------------------------------
    if is_string:
        str_values = [k for k in node.values if isinstance(k, str)]
        date_kind = detect_string_date_kind(str_values)
        if date_kind == "date":
            f.classification = "DATE"
            f.csv_value = "@DATE({},{})".format(min(str_values), max(str_values))
            return f
        if date_kind == "datetime":
            norm = [normalize_datetime_str(v) for v in str_values]
            f.classification = "DATETIME"
            f.csv_value = "@DATETIME({},{})".format(min(norm), max(norm))
            return f
    elif is_numeric and non_null_types.get("int", 0) == occ:
        if is_epoch_ms(list(node.values.keys())):
            f.classification = "DATETIME"
            f.csv_value = "@DATETIME({},{})".format(
                epoch_ms_to_iso(node.num_min), epoch_ms_to_iso(node.num_max)
            )
            return f

    # --- Numeric ----------------------------------------------------------
    if is_numeric:
        if distinct <= cutoff:
            f.classification = "CATEGORICAL"
            f.categorical_values = Counter({str(k): v for k, v in node.values.items()})
            return f
        all_int = non_null_types.get("int", 0) == occ
        rng = (node.num_max - node.num_min) if (node.num_max is not None and node.num_min is not None) else 0
        sparse = rng > (occ * 5)
        if all_int and distinct == occ and occ > 50 and sparse:
            f.classification = "UNIQUE_ID"
            f.csv_value = "@ONEUP"
            report.questionable.append((path, "unique integer id-like field -> @ONEUP", distinct, occ))
            return f
        # @INTEGER's args are parsed with Integer.parseInt (32-bit) in the
        # tool, so values outside that range (e.g. millisecond timestamps
        # that weren't caught by the epoch-ms date heuristic) must fall back
        # to @DOUBLE, which uses Double.parseDouble and has no such limit.
        INT32_LIMIT = 2_000_000_000
        fits_int32 = all_int and -INT32_LIMIT < node.num_min and node.num_max < INT32_LIMIT
        if fits_int32:
            f.classification = "NUMERIC_SYNTH_INT"
            f.csv_value = "@INTEGER({},{})".format(int(node.num_min), int(node.num_max))
        else:
            f.classification = "NUMERIC_SYNTH_DOUBLE"
            # NOTE: tool's @DOUBLE takes (max,min) - verified in ValueMaker.java
            f.csv_value = "@DOUBLE({},{})".format(node.num_max, node.num_min)
            if all_int:
                report.questionable.append(
                    (path, "integer values exceed 32-bit range for @INTEGER -> using @DOUBLE instead (values will be generated as floating point, not exact integers)", distinct, occ)
                )
        report.questionable.append(
            (path, "high-cardinality numeric ({} distinct) -> synthesized range, exact joint distribution with other fields is lost".format(distinct), distinct, occ)
        )
        return f

    # --- String -------------------------------------------------------------
    if is_string:
        if distinct <= cutoff:
            f.classification = "CATEGORICAL"
            f.categorical_values = Counter({str(k): v for k, v in node.values.items()})
            return f

        ratio = distinct / occ if occ else 0
        str_values = list(node.values.keys())
        looks_url = any(URL_RE.match(v) for v in str_values[:20])
        looks_hash = any(HEX_HASH_RE.search(v) for v in str_values[:20])
        avg_len = statistics.mean(len(v) for v in str_values[:200]) if str_values else 0

        if ratio > 0.9 and occ > 50:
            most_common_val, most_common_n = node.values.most_common(1)[0]
            f.classification = "FREE_TEXT_OR_DERIVED"
            f.csv_value = json.dumps(str(most_common_val))[1:-1]  # will be csv-quoted by writer
            f.csv_value = str(most_common_val)
            reason = "near-unique string field ({}/{} distinct)".format(distinct, occ)
            if looks_url:
                reason += "; looks like a URL (often embeds ids/lat-long/signatures) - cannot be regenerated faithfully"
            elif looks_hash:
                reason += "; contains long hex/hash-like tokens - looks random"
            elif avg_len > 60:
                reason += "; long free-text (e.g. description) - cannot be synthesized in a similar pattern with this tool"
            else:
                reason += "; likely a unique identifier/address string"
            reason += " -> emitting most frequent observed value as a constant placeholder"
            report.questionable.append((path, reason, distinct, occ))
            return f

        # High cardinality but repeating (e.g. city, county names) -> keep as categorical
        f.classification = "CATEGORICAL"
        f.categorical_values = Counter({str(k): v for k, v in node.values.items()})
        if distinct > cutoff:
            report.high_card_categorical.append((path, distinct, occ))
        # Detect zero-padded numeric-looking codes that Long.parseLong would mangle
        if any(re.match(r"^0\d+$", v) for v in str_values[:50]):
            report.zero_padded.append(path)
        return f

    # Fallback (mixed types) - stringify everything and treat as categorical/free text
    all_values = Counter({str(k): v for k, v in node.values.items()})
    distinct = len(all_values)
    if distinct <= cutoff:
        f.classification = "CATEGORICAL"
        f.categorical_values = all_values
    else:
        ratio = distinct / occ if occ else 0
        if ratio > 0.9 and occ > 50:
            most_common_val, _ = all_values.most_common(1)[0]
            f.classification = "FREE_TEXT_OR_DERIVED"
            f.csv_value = most_common_val
            report.questionable.append(
                (path, "mixed-type near-unique field ({}/{} distinct) -> constant placeholder".format(distinct, occ), distinct, occ)
            )
        else:
            f.classification = "CATEGORICAL"
            f.categorical_values = all_values
            report.high_card_categorical.append((path, distinct, occ))
    return f


# --------------------------------------------------------------------------
# Report
# --------------------------------------------------------------------------


class Report:
    def __init__(self):
        self.skipped_null = []
        self.skipped_low_prevalence = []
        self.questionable = []
        self.high_card_categorical = []
        self.zero_padded = []
        self.lost_correlations = []
        self.unsupported_scalar_arrays = []
        self.always_empty_arrays = []
        self.deep_arrays_skipped = []
        self.files_written = []
        self.correlations_found = []  # (level_name, [(a,b) or (a,b,'exact-duplicate'), ...])

    def println(self, *a):
        print(*a)

    def print_report(self):
        p = self.println
        p("\n" + "=" * 78)
        p("DATA GENERATION CSV REPORT")
        p("=" * 78)

        p("\n--- Files written ({}) ---".format(len(self.files_written)))
        for f in self.files_written:
            p("  " + f)

        p("\n--- Questionable cardinality / fields needing human review ({}) ---".format(len(self.questionable)))
        for path, reason, distinct, occ in self.questionable:
            p("  [{}] {}: {}".format(distinct, path, reason))

        p("\n--- High-cardinality fields kept as enumerated categorical ({}) ---".format(len(self.high_card_categorical)))
        for path, distinct, occ in self.high_card_categorical:
            p("  [{} distinct / {} occ] {}".format(distinct, occ, path))

        p("\n--- Fields skipped: always null ({}) ---".format(len(self.skipped_null)))
        for path in self.skipped_null:
            p("  " + path)

        p("\n--- Fields skipped: low prevalence (< threshold) ({}) ---".format(len(self.skipped_low_prevalence)))
        for path, prevalence, occ, total in self.skipped_low_prevalence:
            p("  {} : {:.1%} ({}/{})".format(path, prevalence, occ, total))

        p("\n--- Zero-padded string codes (tool will strip leading zeros via Long.parseLong) ({}) ---".format(len(self.zero_padded)))
        for path in self.zero_padded:
            p("  " + path)

        p("\n--- Unsupported scalar arrays (skipped - tool's @ARRAY only emits arrays of objects) ({}) ---".format(len(self.unsupported_scalar_arrays)))
        for path in self.unsupported_scalar_arrays:
            p("  " + path)

        p("\n--- Always-empty arrays (skipped) ({}) ---".format(len(self.always_empty_arrays)))
        for path in self.always_empty_arrays:
            p("  " + path)

        p("\n--- Arrays skipped: nesting deeper than --max-array-depth ({}) ---".format(len(self.deep_arrays_skipped)))
        for path, depth in self.deep_arrays_skipped:
            p("  {} (depth {})".format(path, depth))

        total_corr = sum(len(pairs) for _, pairs in self.correlations_found)
        p("\n--- Correlations detected & preserved (grouped into one CSV) ({}) ---".format(total_corr))
        for level_name, pairs in self.correlations_found:
            if not pairs:
                continue
            p("  level [{}]:".format(level_name or "root"))
            for pair in pairs:
                if len(pair) == 3:
                    p("    {} <-> {}  ({})".format(pair[0], pair[1], pair[2]))
                else:
                    p("    {} <-> {}".format(pair[0], pair[1]))

        p("\n--- Lost correlations (fields observed to correlate but not grouped, usually because one side is high-cardinality numeric/synthetic) ({}) ---".format(len(self.lost_correlations)))
        for a, b in self.lost_correlations:
            p("  {}  <->  {}".format(a, b))

        p("\n" + "=" * 78)


# --------------------------------------------------------------------------
# Flatten a level (object node, stopping at arrays) into scalar Fields + array paths
# --------------------------------------------------------------------------


def flatten_level(node, prefix=""):
    """Given an object-kind Node, return (scalar_paths, array_paths) as
    lists of (dotted_path, child_node), not descending into arrays."""
    scalars = []
    arrays = []
    for name, child in node.children.items():
        path = "{}.{}".format(prefix, name) if prefix else name
        if child.kind == "scalar" or child.kind is None:
            scalars.append((path, child))
        elif child.kind == "array":
            arrays.append((path, child))
        elif child.kind == "object":
            s, a = flatten_level(child, path)
            scalars.extend(s)
            arrays.extend(a)
    return scalars, arrays


# --------------------------------------------------------------------------
# Correlation detection (union-find over categorical fields)
# --------------------------------------------------------------------------


def conditional_entropy_ratio(pair_counts, marginal_counts):
    """Return H(B|A) normalized by H(B), using pair_counts: Counter[(a,b)] and
    marginal_counts: Counter[b]. Lower = more deterministic given A."""
    total = sum(pair_counts.values())
    if total == 0:
        return 1.0
    h_b = entropy(marginal_counts.values())
    if h_b == 0:
        return 0.0
    # H(B|A) = sum_a P(a) * H(B|A=a)
    by_a = defaultdict(Counter)
    a_totals = Counter()
    for (a, b), c in pair_counts.items():
        by_a[a][b] += c
        a_totals[a] += c
    h_b_given_a = 0.0
    for a, bcounts in by_a.items():
        pa = a_totals[a] / total
        h_b_given_a += pa * entropy(bcounts.values())
    return h_b_given_a / h_b


def entropy(counts):
    total = sum(counts)
    if total == 0:
        return 0.0
    h = 0.0
    for c in counts:
        if c == 0:
            continue
        p = c / total
        h -= p * math.log2(p)
    return h


MIN_AVG_SUPPORT_FOR_CORRELATION = 3  # avg occurrences per distinct value required to test a field for correlation
MIN_PAIR_SAMPLE = 30  # minimum co-occurring non-null pairs required to test a pair
CORRELATION_ENTROPY_THRESHOLD = 0.1


def detect_correlated_groups(categorical_fields, records_values):
    """categorical_fields: list of Field (classification CATEGORICAL).
    records_values: dict[path] -> list of str(value) per record (aligned across
    fields, None where absent).
    Returns list of groups (lists of paths); union-find on strong bidirectional
    determinism.

    To avoid spurious "correlations" that are just an artifact of sparse,
    high-cardinality fields (e.g. two fields that each have ~1 occurrence per
    distinct value will trivially look "deterministic" against each other),
    only fields with reasonable average support per distinct value are
    considered eligible for correlation testing. Fields excluded here are
    still generated correctly - they simply stay in their own CSV/group
    rather than being (incorrectly) merged with unrelated fields."""
    all_paths = [f.path for f in categorical_fields]
    eligible_paths = [
        f.path
        for f in categorical_fields
        if len(f.categorical_values) > 0
        and (f.node.occ / len(f.categorical_values)) >= MIN_AVG_SUPPORT_FOR_CORRELATION
    ]
    parent = {p: p for p in all_paths}

    def find(x):
        while parent[x] != x:
            x = parent[x]
        return x

    def union(x, y):
        rx, ry = find(x), find(y)
        if rx != ry:
            parent[rx] = ry

    n = len(eligible_paths)
    correlated_pairs = []
    for i in range(n):
        for j in range(i + 1, n):
            pa, pb = eligible_paths[i], eligible_paths[j]
            va, vb = records_values[pa], records_values[pb]
            pairs = Counter()
            marg_a = Counter()
            marg_b = Counter()
            for a, b in zip(va, vb):
                if a is None or b is None:
                    continue
                pairs[(a, b)] += 1
                marg_a[a] += 1
                marg_b[b] += 1
            if sum(pairs.values()) < MIN_PAIR_SAMPLE:
                continue
            r_b_given_a = conditional_entropy_ratio(pairs, marg_b)
            swapped = Counter({(b, a): c for (a, b), c in pairs.items()})
            r_a_given_b = conditional_entropy_ratio(swapped, marg_a)
            if r_b_given_a < CORRELATION_ENTROPY_THRESHOLD and r_a_given_b < CORRELATION_ENTROPY_THRESHOLD:
                union(pa, pb)
                correlated_pairs.append((pa, pb))

    # Second pass: exact-duplicate detection. This runs on ALL categorical
    # fields (bypassing the sparse-field eligibility filter above) because
    # verbatim-copy fields (e.g. top-level "city" duplicated at
    # "address.city") are a distinct, much more reliable signal than the
    # general entropy-based correlation test and are common in denormalized
    # JSON documents (Zillow-style data in particular).
    m = len(all_paths)
    for i in range(m):
        for j in range(i + 1, m):
            pa, pb = all_paths[i], all_paths[j]
            if find(pa) == find(pb):
                continue
            va, vb = records_values[pa], records_values[pb]
            common = 0
            matches = 0
            for a, b in zip(va, vb):
                if a is None or b is None:
                    continue
                common += 1
                if a == b:
                    matches += 1
            if common >= 20 and (matches / common) > 0.98:
                union(pa, pb)
                correlated_pairs.append((pa, pb, "exact-duplicate"))

    groups = defaultdict(list)
    for p in all_paths:
        groups[find(p)].append(p)
    return list(groups.values()), correlated_pairs


# --------------------------------------------------------------------------
# CSV writing helpers
# --------------------------------------------------------------------------


def sanitize_name(name, used):
    base = re.sub(r"[^A-Za-z0-9_]+", "_", name).strip("_") or "field"
    candidate = base
    i = 2
    while candidate in used:
        candidate = "{}_{}".format(base, i)
        i += 1
    used.add(candidate)
    return candidate


def write_csv_gz(out_dir, filename, header, rows, report):
    os.makedirs(out_dir, exist_ok=True)
    full_path = os.path.join(out_dir, filename)
    buf = io.StringIO()
    writer = csv.writer(buf, quoting=csv.QUOTE_MINIMAL)
    writer.writerow(header)
    for row in rows:
        writer.writerow(row)
    with gzip.open(full_path, "wt", encoding="utf-8", newline="") as gz:
        gz.write(buf.getvalue())
    report.files_written.append(full_path)


# --------------------------------------------------------------------------
# Level processing (recursive)
# --------------------------------------------------------------------------


def get_record_values_for_paths(node, paths):
    """Given an object-kind Node whose .values Counters were merged at the
    document/element level, we actually need PER-RECORD aligned values to
    detect correlation. Schema tree doesn't retain per-record alignment, so
    this function is unused; see process_records_for_level instead."""
    raise NotImplementedError


def collect_scalar_value(record, path):
    """Walk a dict `record` along dotted path (no arrays expected) and return
    the scalar value or None if missing/None."""
    cur = record
    for part in path.split("."):
        if not isinstance(cur, dict) or part not in cur:
            return None
        cur = cur[part]
    if isinstance(cur, (dict, list)):
        return None
    return cur


def stringify(v):
    if v is None:
        return None
    if isinstance(v, bool):
        return "true" if v else "false"
    return str(v)


def process_level(node, level_records, out_dir, cutoff, min_prevalence, max_array_depth, depth, report, level_name):
    """node: object-kind schema Node for this level.
    level_records: list of dict records that exist at this level (root docs,
    or flattened array elements).
    out_dir: directory to write this level's CSVs into.
    depth: current array-nesting depth (0 at root)."""

    scalar_paths, array_paths = flatten_level(node)
    level_record_count = len(level_records)

    used_names = set()

    fields = []
    for path, child in scalar_paths:
        f = classify_field(path, child, cutoff, min_prevalence, level_record_count, report)
        fields.append(f)

    categorical_fields = [f for f in fields if f.classification == "CATEGORICAL"]
    other_fields = [f for f in fields if f.classification not in ("CATEGORICAL", "NULL_ONLY", "LOW_PREVALENCE")]
    constant_fields = [f for f in categorical_fields if len(f.categorical_values) <= 1]
    categorical_fields = [f for f in categorical_fields if len(f.categorical_values) > 1]

    # --- correlation detection among non-constant categorical fields --------
    groups = []
    correlated_pairs_all = []
    if categorical_fields:
        records_values = {}
        for f in categorical_fields:
            records_values[f.path] = [stringify(collect_scalar_value(r, f.path)) for r in level_records]
        groups, pairs = detect_correlated_groups(categorical_fields, records_values)
        correlated_pairs_all.extend(pairs)
        if pairs:
            report.correlations_found.append((level_name, pairs))

    field_by_path = {f.path: f for f in categorical_fields}

    # --- report lost correlations: high-card synth fields that correlate with
    #     something but couldn't be grouped (best-effort: only check numeric
    #     synth fields against grouped categorical fields sharing a common
    #     object prefix, e.g. city vs zipcode) --------------------------------
    synth_like = [f for f in other_fields if f.classification.startswith("NUMERIC_SYNTH")]
    for sf in synth_like:
        prefix = sf.path.rsplit(".", 1)[0] if "." in sf.path else ""
        for cf in categorical_fields:
            cprefix = cf.path.rsplit(".", 1)[0] if "." in cf.path else ""
            if prefix and prefix == cprefix and prefix in ("address",):
                report.lost_correlations.append((sf.path, cf.path))

    # --- write categorical groups -------------------------------------------
    for group_paths in groups:
        if len(group_paths) == 1:
            f = field_by_path[group_paths[0]]
            name = sanitize_name(f.path.rsplit(".", 1)[-1], used_names) + ".csv.gz"
            rows = [[v, c] for v, c in f.categorical_values.items()]
            write_csv_gz(out_dir, name, [f.path, "probability"], rows, report)
        else:
            gfields = [field_by_path[p] for p in group_paths]
            records_values = {f.path: [stringify(collect_scalar_value(r, f.path)) for r in level_records] for f in gfields}
            combo_counts = Counter()
            for i in range(level_record_count):
                key = tuple(records_values[f.path][i] for f in gfields)
                if any(v is None for v in key):
                    continue
                combo_counts[key] += 1
            leaf_names = [p.rsplit(".", 1)[-1] for p in group_paths]
            name = sanitize_name("_".join(leaf_names)[:60], used_names) + ".csv.gz"
            header = list(group_paths) + ["probability"]
            rows = [list(key) + [c] for key, c in combo_counts.items()]
            write_csv_gz(out_dir, name, header, rows, report)

    # --- write constants (bundled into one row/file) ------------------------
    if constant_fields:
        header = [f.path for f in constant_fields] + ["probability"]
        row = [next(iter(f.categorical_values.keys())) if f.categorical_values else "" for f in constant_fields]
        max_occ = max((f.node.occ for f in constant_fields), default=1)
        write_csv_gz(out_dir, "constants.csv.gz", header, [row + [max_occ]], report)

    # --- write other (synthetic/date/id/free-text) fields, one per file -----
    for f in other_fields:
        name = sanitize_name(f.path.rsplit(".", 1)[-1], used_names) + ".csv.gz"
        write_csv_gz(out_dir, name, [f.path, "probability"], [[f.csv_value, f.node.occ]], report)

    # --- arrays --------------------------------------------------------------
    for path, arr_node in array_paths:
        lengths = arr_node.lengths
        if not lengths or max(lengths) == 0:
            report.always_empty_arrays.append(path)
            continue

        elem_node = arr_node.elem
        elem_scalars, elem_arrays = flatten_level(elem_node)
        is_scalar_array = elem_node.kind == "scalar" or (elem_node.kind is None)

        if is_scalar_array:
            report.unsupported_scalar_arrays.append(path)
            continue

        if depth + 1 > max_array_depth:
            report.deep_arrays_skipped.append((path, depth + 1))
            continue

        subdir_name = sanitize_name(path.rsplit(".", 1)[-1], used_names)
        length_counts = Counter(lengths)
        rows = [["@ARRAY({},{})".format(subdir_name, L), c] for L, c in length_counts.items()]
        colname = sanitize_name("arr_" + path.rsplit(".", 1)[-1], used_names) + ".csv.gz"
        write_csv_gz(out_dir, colname, [path, "probability"], rows, report)

        # Build the flattened list of element records for the sub-level.
        elem_records = []
        for r in level_records:
            cur = r
            ok = True
            for part in path.split("."):
                if not isinstance(cur, dict) or part not in cur or cur[part] is None:
                    ok = False
                    break
                cur = cur[part]
            if ok and isinstance(cur, list):
                for el in cur:
                    if isinstance(el, dict):
                        elem_records.append(el)

        sub_out_dir = os.path.join(out_dir, subdir_name)
        # The @ARRAY directive requires this directory to exist even if the
        # element schema ends up producing zero CSV files (e.g. all element
        # fields skipped for low prevalence) - the Java tool calls
        # File.listFiles() on it and exits if the directory is missing.
        os.makedirs(sub_out_dir, exist_ok=True)
        process_level(
            elem_node,
            elem_records,
            sub_out_dir,
            cutoff,
            min_prevalence,
            max_array_depth,
            depth + 1,
            report,
            (level_name + "/" if level_name else "") + subdir_name,
        )


# --------------------------------------------------------------------------
# Main
# --------------------------------------------------------------------------


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("input", help="Input .jsonl or .json file")
    ap.add_argument("out_dir", nargs="?", default="Zillow", help="Output directory for generated CSVs")
    ap.add_argument("--cutoff", type=int, default=100, help="Distinct-value cutoff for categorical vs synthetic")
    ap.add_argument("--min-prevalence", type=float, default=0.05, help="Skip fields present in fewer than this fraction of records at their level")
    ap.add_argument("--max-array-depth", type=int, default=4, help="Max array nesting depth to generate (deeper arrays are skipped+reported)")
    args = ap.parse_args()

    print("Loading records from {} ...".format(args.input))
    records = load_records(args.input)
    print("Loaded {} records".format(len(records)))

    print("Building schema tree ...")
    root = build_schema(records)

    report = Report()

    if os.path.exists(args.out_dir):
        print("Output directory {} already exists; files will be added/overwritten.".format(args.out_dir))

    process_level(
        root,
        [r for r in records if isinstance(r, dict)],
        args.out_dir,
        args.cutoff,
        args.min_prevalence,
        args.max_array_depth,
        0,
        report,
        "",
    )

    report.print_report()
    print("\nDone. {} CSV files written under {}".format(len(report.files_written), args.out_dir))


if __name__ == "__main__":
    main()
