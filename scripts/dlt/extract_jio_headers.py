#!/usr/bin/env python3
"""Convert Jio's public DLT header list (PDF) into data/dlt/jio-headers.csv.

Source: https://trueconnect.jio.com/#/listPeHeader -> "List of Headers associated with
Principal Entities" (direct: https://trueconnect.jio.com/tcapi/entity/v1/generate/headerpdf).

Usage: scripts/dlt/extract_jio_headers.py <headers.pdf> [out.csv]
Requires pdftotext (poppler). Header case is preserved on purpose: headers are case-sensitive
(INDIGO/IndiGo = InterGlobe Aviation, Indigo = Indigo Paints).
"""
import csv, re, subprocess, sys

ROW = re.compile(r'^\s*(\d+)\s+(\S+)\s+(.*?)\s+(Trans\S*|Promotional|Gov\S*|Service\S*)\s*$')


def parse(text):
    rows, last, max_serial = [], None, 0
    for line in text.split('\n'):
        m = ROW.match(line)
        if m:
            last = [m.group(2), m.group(3).strip(), m.group(4)]
            rows.append(last)
            max_serial = max(max_serial, int(m.group(1)))
        elif last and line.startswith(' ' * 15) and line.strip() and not re.match(r'^\s*\d+\s', line) \
                and 'List of Headers' not in line:
            last[1] = f"{last[1]} {line.strip()}"  # wrapped principal entity name
    return rows, max_serial


def main():
    pdf, out = sys.argv[1], (sys.argv[2] if len(sys.argv) > 2 else 'data/dlt/jio-headers.csv')
    text = subprocess.run(['pdftotext', '-layout', pdf, '-'], check=True, capture_output=True, text=True).stdout
    as_of = re.search(r'as on\s+(.+)', text)
    rows, max_serial = parse(text)
    if len(rows) != max_serial:
        sys.exit(f"parsed {len(rows)} rows but serials go to {max_serial}; layout changed?")
    rows.sort(key=lambda r: r[0])
    with open(out, 'w', newline='', encoding='utf-8') as f:
        w = csv.writer(f)
        w.writerow(['header', 'principal_entity', 'purpose'])
        w.writerows(rows)
    print(f"{len(rows)} headers (as on {as_of.group(1).strip() if as_of else '?'}) -> {out}")


if __name__ == '__main__':
    main()
