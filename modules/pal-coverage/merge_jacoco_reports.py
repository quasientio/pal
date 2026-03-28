#!/usr/bin/env python3
#
# Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

"""
Merge JaCoCo XML coverage reports from unit tests and integration tests.

This script handles the case where unit tests run against unshaded classes
and integration tests run against shaded classes, resulting in checksum
mismatches that cause JaCoCo to drop coverage data.

The merge strategy:
- For classes appearing in both reports: take max(covered), min(missed)
- For classes appearing in only one report: include as-is
- Recalculate aggregate counters up the hierarchy

Usage:
    python merge_jacoco_reports.py <unit_test_xml> <it_xml> <output_dir> [--filter <prefix>]

Example:
    python merge_jacoco_reports.py \\
        target/site/jacoco-aggregate/jacoco.xml \\
        target/site/jacoco-aggregate-it/jacoco.xml \\
        target/site/jacoco-merged \\
        --filter io/quasient/pal
"""

import sys
import xml.etree.ElementTree as ET
from collections import defaultdict
from copy import deepcopy
from pathlib import Path
from html import escape


COUNTER_TYPES = ['INSTRUCTION', 'BRANCH', 'LINE', 'COMPLEXITY', 'METHOD', 'CLASS']


def parse_counters(element):
    """Extract counters from an XML element as a dict."""
    counters = {}
    for counter in element.findall('counter'):
        ctype = counter.get('type')
        counters[ctype] = {
            'missed': int(counter.get('missed', 0)),
            'covered': int(counter.get('covered', 0))
        }
    return counters


def merge_counters(counters1, counters2):
    """
    Merge two counter dicts by taking max(covered) and min(missed).

    This is a conservative merge - it may undercount coverage if both
    test suites cover different parts of the same method.
    """
    merged = {}
    all_types = set(counters1.keys()) | set(counters2.keys())

    for ctype in all_types:
        c1 = counters1.get(ctype, {'missed': 0, 'covered': 0})
        c2 = counters2.get(ctype, {'missed': 0, 'covered': 0})

        # If one report doesn't have this class, use the other's counters
        if ctype not in counters1:
            merged[ctype] = c2.copy()
        elif ctype not in counters2:
            merged[ctype] = c1.copy()
        else:
            # Both have counters - merge conservatively
            merged[ctype] = {
                'covered': max(c1['covered'], c2['covered']),
                'missed': min(c1['missed'], c2['missed'])
            }

    return merged


def sum_counters(counter_list):
    """Sum a list of counter dicts."""
    totals = defaultdict(lambda: {'missed': 0, 'covered': 0})
    for counters in counter_list:
        for ctype, values in counters.items():
            totals[ctype]['missed'] += values['missed']
            totals[ctype]['covered'] += values['covered']
    return dict(totals)


def set_counters(element, counters):
    """Set counters on an XML element, removing old ones first."""
    # Remove existing counters
    for counter in element.findall('counter'):
        element.remove(counter)

    # Add new counters in standard order
    for ctype in COUNTER_TYPES:
        if ctype in counters:
            ET.SubElement(element, 'counter', {
                'type': ctype,
                'missed': str(counters[ctype]['missed']),
                'covered': str(counters[ctype]['covered'])
            })


def parse_lines(sourcefile_elem):
    """Extract line-level coverage from a sourcefile element."""
    lines = {}
    for line in sourcefile_elem.findall('line'):
        nr = int(line.get('nr'))
        lines[nr] = {
            'mi': int(line.get('mi', 0)),  # missed instructions
            'ci': int(line.get('ci', 0)),  # covered instructions
            'mb': int(line.get('mb', 0)),  # missed branches
            'cb': int(line.get('cb', 0)),  # covered branches
        }
    return lines


def merge_lines(lines1, lines2):
    """
    Merge line-level coverage from two reports.

    For each line, if covered in either report, count as covered.
    This gives accurate union of coverage.
    """
    merged = {}
    all_line_nums = set(lines1.keys()) | set(lines2.keys())

    for nr in all_line_nums:
        l1 = lines1.get(nr, {'mi': 0, 'ci': 0, 'mb': 0, 'cb': 0})
        l2 = lines2.get(nr, {'mi': 0, 'ci': 0, 'mb': 0, 'cb': 0})

        if nr not in lines1:
            merged[nr] = l2.copy()
        elif nr not in lines2:
            merged[nr] = l1.copy()
        else:
            # Both have this line - take max covered, min missed
            merged[nr] = {
                'ci': max(l1['ci'], l2['ci']),
                'mi': min(l1['mi'], l2['mi']),
                'cb': max(l1['cb'], l2['cb']),
                'mb': min(l1['mb'], l2['mb']),
            }

    return merged


def lines_to_counters(lines):
    """Calculate aggregate counters from line-level data."""
    total_ci = sum(l['ci'] for l in lines.values())
    total_mi = sum(l['mi'] for l in lines.values())
    total_cb = sum(l['cb'] for l in lines.values())
    total_mb = sum(l['mb'] for l in lines.values())

    # Count lines with any coverage
    lines_covered = sum(1 for l in lines.values() if l['ci'] > 0)
    lines_missed = sum(1 for l in lines.values() if l['ci'] == 0 and l['mi'] > 0)

    counters = {
        'INSTRUCTION': {'covered': total_ci, 'missed': total_mi},
        'LINE': {'covered': lines_covered, 'missed': lines_missed},
    }

    if total_cb > 0 or total_mb > 0:
        counters['BRANCH'] = {'covered': total_cb, 'missed': total_mb}

    return counters


def extract_classes_and_sourcefiles(root):
    """
    Extract all classes and sourcefiles from a JaCoCo XML report.

    Returns:
        classes: {package_name: {class_name: {method_key: counters, '_class': counters}}}
        sourcefiles: {package_name: {sourcefile_name: {line_nr: line_data}}}

    Handles both grouped (report-aggregate) and flat (CLI report) formats.
    """
    classes = defaultdict(lambda: defaultdict(dict))
    sourcefiles = defaultdict(lambda: defaultdict(dict))

    # Find all packages (may be nested in groups or at top level)
    for package in root.iter('package'):
        pkg_name = package.get('name')

        # Extract sourcefile line data (separate from classes)
        for sf in package.findall('sourcefile'):
            sf_name = sf.get('name')
            sourcefiles[pkg_name][sf_name] = {
                'lines': parse_lines(sf),
                'counters': parse_counters(sf),
            }

        for cls in package.findall('class'):
            cls_name = cls.get('name')
            sf_name = cls.get('sourcefilename', '')

            # Store class-level counters (NOT line data - that belongs to sourcefile)
            classes[pkg_name][cls_name]['_class'] = parse_counters(cls)
            classes[pkg_name][cls_name]['_sourcefilename'] = sf_name

            # Store method-level counters
            for method in cls.findall('method'):
                method_key = f"{method.get('name')}:{method.get('desc')}:{method.get('line', '')}"
                classes[pkg_name][cls_name][method_key] = {
                    'counters': parse_counters(method),
                    'name': method.get('name'),
                    'desc': method.get('desc'),
                    'line': method.get('line', '')
                }

    return classes, sourcefiles


def merge_class_data(class1, class2):
    """Merge data for a single class from two reports using counter-level merge."""
    merged = {}
    all_keys = set(class1.keys()) | set(class2.keys())

    for key in all_keys:
        if key == '_class':
            # Use counter-level merge for class counters
            merged['_class'] = merge_counters(
                class1.get('_class', {}),
                class2.get('_class', {})
            )
        elif key == '_sourcefilename':
            merged['_sourcefilename'] = class1.get('_sourcefilename') or class2.get('_sourcefilename', '')
        elif key in class1 and key in class2:
            # Method exists in both - merge counters
            merged[key] = {
                'counters': merge_counters(
                    class1[key]['counters'],
                    class2[key]['counters']
                ),
                'name': class1[key]['name'],
                'desc': class1[key]['desc'],
                'line': class1[key].get('line', class2[key].get('line', ''))
            }
        elif key in class1:
            merged[key] = deepcopy(class1[key])
        else:
            merged[key] = deepcopy(class2[key])

    return merged


def merge_sourcefile_data(sf1, sf2):
    """Merge sourcefile data from two reports using line-level merge."""
    merged_lines = merge_lines(sf1.get('lines', {}), sf2.get('lines', {}))
    merged_counters = lines_to_counters(merged_lines)

    # Preserve METHOD/CLASS/COMPLEXITY from counter merge if available
    counter_merge = merge_counters(sf1.get('counters', {}), sf2.get('counters', {}))
    for ctype in ['METHOD', 'CLASS', 'COMPLEXITY']:
        if ctype in counter_merge:
            merged_counters[ctype] = counter_merge[ctype]

    return {
        'lines': merged_lines,
        'counters': merged_counters,
    }


def merge_reports(classes1, classes2, sourcefiles1, sourcefiles2):
    """Merge class and sourcefile dictionaries from two reports."""
    merged_classes = defaultdict(lambda: defaultdict(dict))
    merged_sourcefiles = defaultdict(lambda: defaultdict(dict))

    all_packages = set(classes1.keys()) | set(classes2.keys()) | \
                   set(sourcefiles1.keys()) | set(sourcefiles2.keys())

    for pkg_name in all_packages:
        # Merge classes
        pkg1 = classes1.get(pkg_name, {})
        pkg2 = classes2.get(pkg_name, {})
        all_classes = set(pkg1.keys()) | set(pkg2.keys())

        for cls_name in all_classes:
            if cls_name in pkg1 and cls_name in pkg2:
                merged_classes[pkg_name][cls_name] = merge_class_data(pkg1[cls_name], pkg2[cls_name])
            elif cls_name in pkg1:
                merged_classes[pkg_name][cls_name] = deepcopy(pkg1[cls_name])
            else:
                merged_classes[pkg_name][cls_name] = deepcopy(pkg2[cls_name])

        # Merge sourcefiles (for accurate line-level coverage)
        sf1 = sourcefiles1.get(pkg_name, {})
        sf2 = sourcefiles2.get(pkg_name, {})
        all_sourcefiles = set(sf1.keys()) | set(sf2.keys())

        for sf_name in all_sourcefiles:
            if sf_name in sf1 and sf_name in sf2:
                merged_sourcefiles[pkg_name][sf_name] = merge_sourcefile_data(sf1[sf_name], sf2[sf_name])
            elif sf_name in sf1:
                merged_sourcefiles[pkg_name][sf_name] = deepcopy(sf1[sf_name])
            else:
                merged_sourcefiles[pkg_name][sf_name] = deepcopy(sf2[sf_name])

    return merged_classes, merged_sourcefiles


def verify_line_consistency(sourcefiles1, sourcefiles2):
    """
    Verify that sourcefiles appearing in both reports have consistent line numbers.

    This is a safety check to ensure that shading hasn't affected line mappings.
    Returns True if consistent, False if mismatches found.
    """
    mismatches = []

    # Find common sourcefiles
    all_packages = set(sourcefiles1.keys()) & set(sourcefiles2.keys())

    for pkg_name in all_packages:
        sf1 = sourcefiles1.get(pkg_name, {})
        sf2 = sourcefiles2.get(pkg_name, {})
        common_files = set(sf1.keys()) & set(sf2.keys())

        for sf_name in common_files:
            lines1 = set(sf1[sf_name].get('lines', {}).keys())
            lines2 = set(sf2[sf_name].get('lines', {}).keys())

            only_in_1 = lines1 - lines2
            only_in_2 = lines2 - lines1

            if only_in_1 or only_in_2:
                mismatches.append({
                    'package': pkg_name,
                    'sourcefile': sf_name,
                    'range1': (min(lines1), max(lines1)) if lines1 else (0, 0),
                    'range2': (min(lines2), max(lines2)) if lines2 else (0, 0),
                    'only_in_1': len(only_in_1),
                    'only_in_2': len(only_in_2),
                })

    if mismatches:
        print(f"\n[WARNING] Found {len(mismatches)} sourcefiles with inconsistent line numbers!")
        print("  This may indicate bytecode/source mismatch between shaded and unshaded classes.")
        for m in mismatches[:5]:  # Show first 5
            print(f"  - {m['package']}/{m['sourcefile']}:")
            print(f"      Report 1 range: {m['range1'][0]}-{m['range1'][1]}, "
                  f"Report 2 range: {m['range2'][0]}-{m['range2'][1]}")
            print(f"      Lines only in report 1: {m['only_in_1']}, "
                  f"only in report 2: {m['only_in_2']}")
        if len(mismatches) > 5:
            print(f"  ... and {len(mismatches) - 5} more")
        return False

    return True


def build_xml(merged_classes, merged_sourcefiles, report_name="Merged Coverage Report"):
    """Build a JaCoCo XML report from merged class and sourcefile data."""
    root = ET.Element('report', {'name': report_name})

    # Add a session info placeholder
    ET.SubElement(root, 'sessioninfo', {
        'id': 'merged-report',
        'start': '0',
        'dump': '0'
    })

    report_counters = []
    all_packages = set(merged_classes.keys()) | set(merged_sourcefiles.keys())

    for pkg_name in sorted(all_packages):
        pkg_element = ET.SubElement(root, 'package', {'name': pkg_name})
        class_counters = []

        # Add classes
        for cls_name in sorted(merged_classes.get(pkg_name, {}).keys()):
            cls_data = merged_classes[pkg_name][cls_name]
            sourcefilename = cls_data.get('_sourcefilename', '')

            cls_element = ET.SubElement(pkg_element, 'class', {
                'name': cls_name,
                'sourcefilename': sourcefilename
            })

            method_counters = []

            # Add methods
            for key, method_data in sorted(cls_data.items()):
                if key.startswith('_'):
                    continue

                method_attrs = {
                    'name': method_data['name'],
                    'desc': method_data['desc']
                }
                if method_data.get('line'):
                    method_attrs['line'] = method_data['line']

                method_element = ET.SubElement(cls_element, 'method', method_attrs)
                set_counters(method_element, method_data['counters'])
                method_counters.append(method_data['counters'])

            # Set class counters from methods (excluding LINE - that comes from sourcefiles)
            if method_counters:
                recalc_class_counters = sum_counters(method_counters)
                # CLASS counter is special - it's 1 if any method is covered
                if recalc_class_counters.get('METHOD', {}).get('covered', 0) > 0:
                    recalc_class_counters['CLASS'] = {'missed': 0, 'covered': 1}
                else:
                    recalc_class_counters['CLASS'] = {'missed': 1, 'covered': 0}
                set_counters(cls_element, recalc_class_counters)
                class_counters.append(recalc_class_counters)
            elif '_class' in cls_data:
                set_counters(cls_element, cls_data['_class'])
                class_counters.append(cls_data['_class'])

        # Add sourcefile elements with line data
        for sf_name in sorted(merged_sourcefiles.get(pkg_name, {}).keys()):
            sf_data = merged_sourcefiles[pkg_name][sf_name]
            sf_element = ET.SubElement(pkg_element, 'sourcefile', {'name': sf_name})

            # Add line elements
            for nr in sorted(sf_data.get('lines', {}).keys()):
                line_data = sf_data['lines'][nr]
                ET.SubElement(sf_element, 'line', {
                    'nr': str(nr),
                    'mi': str(line_data['mi']),
                    'ci': str(line_data['ci']),
                    'mb': str(line_data['mb']),
                    'cb': str(line_data['cb']),
                })

            # Set sourcefile counters
            if 'counters' in sf_data:
                set_counters(sf_element, sf_data['counters'])

        # Calculate package counters:
        # - INSTRUCTION, BRANCH, METHOD, COMPLEXITY, CLASS from classes
        # - LINE from sourcefiles (to avoid double-counting inner classes)
        pkg_counters = sum_counters(class_counters) if class_counters else {}

        # Get LINE counter from sourcefiles
        sf_line_counters = []
        for sf_data in merged_sourcefiles.get(pkg_name, {}).values():
            if 'counters' in sf_data and 'LINE' in sf_data['counters']:
                sf_line_counters.append({'LINE': sf_data['counters']['LINE']})
        if sf_line_counters:
            pkg_counters['LINE'] = sum_counters(sf_line_counters).get('LINE', {'missed': 0, 'covered': 0})

        if pkg_counters:
            set_counters(pkg_element, pkg_counters)
            report_counters.append(pkg_counters)

    # Set report-level counters
    if report_counters:
        set_counters(root, sum_counters(report_counters))

    return root


def indent_xml(elem, level=0):
    """Add indentation to XML for readability."""
    indent = "\n" + "  " * level
    if len(elem):
        if not elem.text or not elem.text.strip():
            elem.text = indent + "  "
        if not elem.tail or not elem.tail.strip():
            elem.tail = indent
        for child in elem:
            indent_xml(child, level + 1)
        if not child.tail or not child.tail.strip():
            child.tail = indent
    else:
        if level and (not elem.tail or not elem.tail.strip()):
            elem.tail = indent


def coverage_bar(covered, missed, width=120, res_prefix=''):
    """Generate an HTML coverage bar."""
    total = covered + missed
    if total == 0:
        return '<td class="bar">n/a</td>'

    pct = covered / total
    red_width = int(width * (1 - pct))
    green_width = int(width * pct)

    bar = '<td class="bar">'
    if red_width > 0:
        bar += f'<img src="{res_prefix}jacoco-resources/redbar.gif" width="{red_width}" height="10" title="{missed}"/>'
    if green_width > 0:
        bar += f'<img src="{res_prefix}jacoco-resources/greenbar.gif" width="{green_width}" height="10" title="{covered}"/>'
    bar += '</td>'
    return bar


def coverage_pct(covered, missed):
    """Calculate coverage percentage."""
    total = covered + missed
    if total == 0:
        return 'n/a'
    return f'{covered / total * 100:.0f}%'


def generate_html_index(merged_classes, merged_sourcefiles, output_dir, report_name,
                        pkg_filter=None, source_resources_dir=None):
    """Generate HTML index page listing all packages."""
    output_dir = Path(output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)

    # Copy JaCoCo resources from source report if available, otherwise create minimal versions
    resources_dir = output_dir / 'jacoco-resources'
    resources_dir.mkdir(exist_ok=True)

    if source_resources_dir and Path(source_resources_dir).exists():
        # Copy resources from existing JaCoCo report
        import shutil
        src_resources = Path(source_resources_dir)
        for item in src_resources.iterdir():
            if item.is_file():
                shutil.copy2(item, resources_dir / item.name)
    else:
        # Create minimal CSS and bar images
        css = """
body { font-family: sans-serif; font-size: 13px; }
table.coverage { border-collapse: collapse; width: 100%; }
table.coverage th, table.coverage td { padding: 4px 8px; text-align: left; border-bottom: 1px solid #ddd; }
table.coverage th { background: #f5f5f5; cursor: pointer; }
table.coverage tr:hover { background: #f9f9f9; }
td.bar { width: 140px; }
td.bar img { height: 10px; vertical-align: middle; }
td.ctr1, td.ctr2 { text-align: right; }
.el_package, .el_class { color: #0066cc; text-decoration: none; }
.el_package:hover, .el_class:hover { text-decoration: underline; }
h1 { font-size: 18px; margin-bottom: 20px; }
.breadcrumb { margin-bottom: 15px; color: #666; }
.footer { margin-top: 20px; font-size: 11px; color: #999; }
tfoot td { font-weight: bold; background: #f5f5f5; }
"""
        (resources_dir / 'report.css').write_text(css)

        # Create colored bar images (1x1 pixel GIFs)
        # Red bar (RGB 255,0,0)
        (resources_dir / 'redbar.gif').write_bytes(
            b'GIF89a\x01\x00\x01\x00\x80\x00\x00\xff\x00\x00\x00\x00\x00!\xf9\x04\x01\x00\x00\x00\x00,\x00\x00\x00\x00\x01\x00\x01\x00\x00\x02\x02D\x01\x00;'
        )
        # Green bar (RGB 0,128,0)
        (resources_dir / 'greenbar.gif').write_bytes(
            b'GIF89a\x01\x00\x01\x00\x80\x00\x00\x00\x80\x00\x00\x00\x00!\xf9\x04\x01\x00\x00\x00\x00,\x00\x00\x00\x00\x01\x00\x01\x00\x00\x02\x02D\x01\x00;'
        )

    # Filter packages if requested
    if pkg_filter:
        # Also exclude shaded packages (io/quasient/pal/shd/)
        filtered_classes = {k: v for k, v in merged_classes.items()
                           if k.startswith(pkg_filter) and '/shd/' not in k}
        filtered_sourcefiles = {k: v for k, v in merged_sourcefiles.items()
                                if k.startswith(pkg_filter) and '/shd/' not in k}
    else:
        filtered_classes = merged_classes
        filtered_sourcefiles = merged_sourcefiles

    # Calculate package-level summaries
    pkg_summaries = []
    for pkg_name in sorted(filtered_classes.keys()):
        classes = filtered_classes[pkg_name]
        sourcefiles = filtered_sourcefiles.get(pkg_name, {})

        # INSTRUCTION, BRANCH, METHOD from classes
        instr_covered = sum(c.get('_class', {}).get('INSTRUCTION', {}).get('covered', 0) for c in classes.values())
        instr_missed = sum(c.get('_class', {}).get('INSTRUCTION', {}).get('missed', 0) for c in classes.values())
        branch_covered = sum(c.get('_class', {}).get('BRANCH', {}).get('covered', 0) for c in classes.values())
        branch_missed = sum(c.get('_class', {}).get('BRANCH', {}).get('missed', 0) for c in classes.values())
        method_covered = sum(c.get('_class', {}).get('METHOD', {}).get('covered', 0) for c in classes.values())
        method_missed = sum(c.get('_class', {}).get('METHOD', {}).get('missed', 0) for c in classes.values())

        # LINE from sourcefiles (to avoid double-counting inner classes sharing same source file)
        line_covered = sum(sf.get('counters', {}).get('LINE', {}).get('covered', 0) for sf in sourcefiles.values())
        line_missed = sum(sf.get('counters', {}).get('LINE', {}).get('missed', 0) for sf in sourcefiles.values())

        class_covered = sum(1 for c in classes.values() if c.get('_class', {}).get('METHOD', {}).get('covered', 0) > 0)
        class_missed = len(classes) - class_covered

        pkg_summaries.append({
            'name': pkg_name,
            'instr_covered': instr_covered,
            'instr_missed': instr_missed,
            'branch_covered': branch_covered,
            'branch_missed': branch_missed,
            'line_covered': line_covered,
            'line_missed': line_missed,
            'method_covered': method_covered,
            'method_missed': method_missed,
            'class_covered': class_covered,
            'class_missed': class_missed,
        })

    # Calculate totals
    totals = {
        'instr_covered': sum(p['instr_covered'] for p in pkg_summaries),
        'instr_missed': sum(p['instr_missed'] for p in pkg_summaries),
        'branch_covered': sum(p['branch_covered'] for p in pkg_summaries),
        'branch_missed': sum(p['branch_missed'] for p in pkg_summaries),
        'line_covered': sum(p['line_covered'] for p in pkg_summaries),
        'line_missed': sum(p['line_missed'] for p in pkg_summaries),
        'method_covered': sum(p['method_covered'] for p in pkg_summaries),
        'method_missed': sum(p['method_missed'] for p in pkg_summaries),
        'class_covered': sum(p['class_covered'] for p in pkg_summaries),
        'class_missed': sum(p['class_missed'] for p in pkg_summaries),
    }

    # Generate index.html
    html = f'''<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8">
<title>{escape(report_name)}</title>
<link rel="stylesheet" href="jacoco-resources/report.css" type="text/css">
</head>
<body>
<h1>{escape(report_name)}</h1>
<table class="coverage" id="coveragetable">
<thead>
<tr>
<th>Package</th>
<th>Missed Instructions</th>
<th>Cov.</th>
<th>Missed Branches</th>
<th>Cov.</th>
<th>Missed Lines</th>
<th>Lines</th>
<th>Missed Methods</th>
<th>Methods</th>
<th>Missed Classes</th>
<th>Classes</th>
</tr>
</thead>
<tfoot>
<tr>
<td>Total ({len(pkg_summaries)} packages)</td>
{coverage_bar(totals['instr_covered'], totals['instr_missed'])}
<td class="ctr2">{coverage_pct(totals['instr_covered'], totals['instr_missed'])}</td>
{coverage_bar(totals['branch_covered'], totals['branch_missed'])}
<td class="ctr2">{coverage_pct(totals['branch_covered'], totals['branch_missed'])}</td>
<td class="ctr1">{totals['line_missed']}</td>
<td class="ctr2">{totals['line_covered'] + totals['line_missed']}</td>
<td class="ctr1">{totals['method_missed']}</td>
<td class="ctr2">{totals['method_covered'] + totals['method_missed']}</td>
<td class="ctr1">{totals['class_missed']}</td>
<td class="ctr2">{totals['class_covered'] + totals['class_missed']}</td>
</tr>
</tfoot>
<tbody>
'''

    # Sort by missed instructions (descending) to show worst coverage first
    for pkg in sorted(pkg_summaries, key=lambda p: p['instr_missed'], reverse=True):
        pkg_link = pkg['name'].replace('/', '.') + '/index.html'
        html += f'''<tr>
<td><a href="{pkg_link}" class="el_package">{pkg['name'].replace('/', '.')}</a></td>
{coverage_bar(pkg['instr_covered'], pkg['instr_missed'])}
<td class="ctr2">{coverage_pct(pkg['instr_covered'], pkg['instr_missed'])}</td>
{coverage_bar(pkg['branch_covered'], pkg['branch_missed'])}
<td class="ctr2">{coverage_pct(pkg['branch_covered'], pkg['branch_missed'])}</td>
<td class="ctr1">{pkg['line_missed']}</td>
<td class="ctr2">{pkg['line_covered'] + pkg['line_missed']}</td>
<td class="ctr1">{pkg['method_missed']}</td>
<td class="ctr2">{pkg['method_covered'] + pkg['method_missed']}</td>
<td class="ctr1">{pkg['class_missed']}</td>
<td class="ctr2">{pkg['class_covered'] + pkg['class_missed']}</td>
</tr>
'''

    html += '''</tbody>
</table>
<div class="footer">Generated by merge_jacoco_reports.py</div>
</body>
</html>
'''

    (output_dir / 'index.html').write_text(html)

    # Generate per-package pages
    for pkg in pkg_summaries:
        generate_package_html(filtered_classes, filtered_sourcefiles, pkg, output_dir, report_name)

    print(f"Generated HTML report in: {output_dir}")
    return totals


def generate_package_html(merged_classes, merged_sourcefiles, pkg_summary, output_dir, report_name):
    """Generate HTML page for a single package.

    Note: Individual class rows show their class-level LINE counters, which may be
    duplicated for inner classes sharing the same source file. The package total
    (tfoot) uses pkg_summary which is calculated from sourcefiles to avoid
    double-counting.
    """
    pkg_name = pkg_summary['name']
    pkg_dir = output_dir / pkg_name.replace('/', '.')
    pkg_dir.mkdir(parents=True, exist_ok=True)

    classes = merged_classes[pkg_name]

    # Calculate class-level summaries
    # Note: LINE counters from classes may be duplicated for inner classes
    # The pkg_summary totals use sourcefiles to avoid this issue
    class_summaries = []
    for cls_name, cls_data in classes.items():
        counters = cls_data.get('_class', {})
        class_summaries.append({
            'name': cls_name.split('/')[-1],  # Just class name, not full path
            'full_name': cls_name,
            'instr_covered': counters.get('INSTRUCTION', {}).get('covered', 0),
            'instr_missed': counters.get('INSTRUCTION', {}).get('missed', 0),
            'branch_covered': counters.get('BRANCH', {}).get('covered', 0),
            'branch_missed': counters.get('BRANCH', {}).get('missed', 0),
            'line_covered': counters.get('LINE', {}).get('covered', 0),
            'line_missed': counters.get('LINE', {}).get('missed', 0),
            'method_covered': counters.get('METHOD', {}).get('covered', 0),
            'method_missed': counters.get('METHOD', {}).get('missed', 0),
        })

    html = f'''<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8">
<title>{escape(pkg_name.replace('/', '.'))}</title>
<link rel="stylesheet" href="../jacoco-resources/report.css" type="text/css">
</head>
<body>
<div class="breadcrumb">
<a href="../index.html">{escape(report_name)}</a> &gt; {escape(pkg_name.replace('/', '.'))}
</div>
<h1>{escape(pkg_name.replace('/', '.'))}</h1>
<table class="coverage" id="coveragetable">
<thead>
<tr>
<th>Class</th>
<th>Missed Instructions</th>
<th>Cov.</th>
<th>Missed Branches</th>
<th>Cov.</th>
<th>Missed Lines</th>
<th>Lines</th>
<th>Missed Methods</th>
<th>Methods</th>
</tr>
</thead>
<tfoot>
<tr>
<td>Total ({len(class_summaries)} classes)</td>
{coverage_bar(pkg_summary['instr_covered'], pkg_summary['instr_missed'], res_prefix='../')}
<td class="ctr2">{coverage_pct(pkg_summary['instr_covered'], pkg_summary['instr_missed'])}</td>
{coverage_bar(pkg_summary['branch_covered'], pkg_summary['branch_missed'], res_prefix='../')}
<td class="ctr2">{coverage_pct(pkg_summary['branch_covered'], pkg_summary['branch_missed'])}</td>
<td class="ctr1">{pkg_summary['line_missed']}</td>
<td class="ctr2">{pkg_summary['line_covered'] + pkg_summary['line_missed']}</td>
<td class="ctr1">{pkg_summary['method_missed']}</td>
<td class="ctr2">{pkg_summary['method_covered'] + pkg_summary['method_missed']}</td>
</tr>
</tfoot>
<tbody>
'''

    # Sort by missed instructions (descending)
    for cls in sorted(class_summaries, key=lambda c: c['instr_missed'], reverse=True):
        html += f'''<tr>
<td><span class="el_class">{escape(cls['name'])}</span></td>
{coverage_bar(cls['instr_covered'], cls['instr_missed'], res_prefix='../')}
<td class="ctr2">{coverage_pct(cls['instr_covered'], cls['instr_missed'])}</td>
{coverage_bar(cls['branch_covered'], cls['branch_missed'], res_prefix='../')}
<td class="ctr2">{coverage_pct(cls['branch_covered'], cls['branch_missed'])}</td>
<td class="ctr1">{cls['line_missed']}</td>
<td class="ctr2">{cls['line_covered'] + cls['line_missed']}</td>
<td class="ctr1">{cls['method_missed']}</td>
<td class="ctr2">{cls['method_covered'] + cls['method_missed']}</td>
</tr>
'''

    html += '''</tbody>
</table>
<div class="footer">Generated by merge_jacoco_reports.py</div>
</body>
</html>
'''

    (pkg_dir / 'index.html').write_text(html)


def main():
    if len(sys.argv) < 4:
        print(__doc__)
        sys.exit(1)

    ut_xml_path = Path(sys.argv[1])
    it_xml_path = Path(sys.argv[2])
    output_dir = Path(sys.argv[3])

    # Parse optional filter
    pkg_filter = None
    if '--filter' in sys.argv:
        filter_idx = sys.argv.index('--filter')
        if filter_idx + 1 < len(sys.argv):
            pkg_filter = sys.argv[filter_idx + 1]
            print(f"Filtering packages with prefix: {pkg_filter}")

    print(f"Loading unit test report: {ut_xml_path}")
    ut_tree = ET.parse(ut_xml_path)
    ut_classes, ut_sourcefiles = extract_classes_and_sourcefiles(ut_tree.getroot())
    print(f"  Found {sum(len(c) for c in ut_classes.values())} classes in {len(ut_classes)} packages")

    print(f"Loading IT report: {it_xml_path}")
    it_tree = ET.parse(it_xml_path)
    it_classes, it_sourcefiles = extract_classes_and_sourcefiles(it_tree.getroot())
    print(f"  Found {sum(len(c) for c in it_classes.values())} classes in {len(it_classes)} packages")

    # Verify line number consistency between reports
    print("Verifying line number consistency...")
    if verify_line_consistency(ut_sourcefiles, it_sourcefiles):
        print("  Line numbers are consistent across reports")
    # Warning is printed by verify_line_consistency if mismatches found

    print("Merging reports...")
    merged_classes, merged_sourcefiles = merge_reports(ut_classes, it_classes, ut_sourcefiles, it_sourcefiles)
    print(f"  Merged result: {sum(len(c) for c in merged_classes.values())} classes in {len(merged_classes)} packages")

    # Count classes unique to each report
    ut_only = sum(1 for pkg in ut_classes for cls in ut_classes[pkg]
                  if cls not in it_classes.get(pkg, {}))
    it_only = sum(1 for pkg in it_classes for cls in it_classes[pkg]
                  if cls not in ut_classes.get(pkg, {}))
    both = sum(1 for pkg in ut_classes for cls in ut_classes[pkg]
               if cls in it_classes.get(pkg, {}))

    print(f"  Classes in UT only: {ut_only}")
    print(f"  Classes in IT only: {it_only}")
    print(f"  Classes in both: {both}")

    print("Building merged XML...")
    merged_root = build_xml(merged_classes, merged_sourcefiles, "PAL Combined Coverage (Unit + Integration Tests)")
    indent_xml(merged_root)

    # Write XML output
    output_dir.mkdir(parents=True, exist_ok=True)
    xml_path = output_dir / 'jacoco.xml'
    tree = ET.ElementTree(merged_root)
    tree.write(xml_path, encoding='UTF-8', xml_declaration=True)
    print(f"Wrote merged XML to: {xml_path}")

    # Generate HTML report
    print("Generating HTML report...")
    # Use resources from the unit test report (they have proper JaCoCo styling)
    source_resources = ut_xml_path.parent / 'jacoco-resources'
    totals = generate_html_index(
        merged_classes,
        merged_sourcefiles,
        output_dir,
        "PAL Combined Coverage (Unit + Integration Tests)",
        pkg_filter,
        source_resources_dir=source_resources
    )

    # Print summary
    total_instr = totals['instr_covered'] + totals['instr_missed']
    if total_instr > 0:
        print(f"\nOverall instruction coverage: {totals['instr_covered'] / total_instr * 100:.1f}%")
        print(f"  Covered: {totals['instr_covered']:,} instructions")
        print(f"  Missed:  {totals['instr_missed']:,} instructions")
        print("\nNote: Line-level merge combines coverage from both reports.")
        print("If UT covers lines 1-50 and IT covers lines 51-90, merged shows 90% (not max of individual %)")


if __name__ == '__main__':
    main()
