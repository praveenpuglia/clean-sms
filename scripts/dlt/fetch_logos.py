#!/usr/bin/env python3
"""Maintainer tool: fetch each brand's icon from its OFFICIAL website into the app's drawables.

Uses the brand's Play Store app icon when `play` is set, else the site's declared apple-touch-icon / largest <link rel=icon>, falls back to
/apple-touch-icon.png. Normalizes to a 192x192 WebP (ImageMagick + cwebp) and records the exact
source URL in data/logo-sources.json. Review every logo by eye before committing.

Usage: scripts/dlt/fetch_logos.py [brand_id ...]   (default: brands without a logo yet)
"""
import json, os, re, subprocess, sys, tempfile
from urllib.parse import urljoin

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
BRANDS = os.path.join(ROOT, 'data/brands.json')
SOURCES = os.path.join(ROOT, 'data/logo-sources.json')
LOGOS = os.path.join(ROOT, 'app/src/main/res/drawable-nodpi')
UA = {'User-Agent': 'Mozilla/5.0 (Macintosh; Intel Mac OS X 14_0) AppleWebKit/605.1.15 Safari/605.1.15'}


def get(url):
    """curl uses the system trust store and is blocked less often than urllib."""
    with tempfile.NamedTemporaryFile() as f:
        r = subprocess.run(['curl', '-sSL', '--compressed', '-m', '30', '-A', UA['User-Agent'], '-o', f.name,
                            '-w', '%{http_code} %{url_effective}', url.replace(' ', '%20')], capture_output=True, text=True)
        code, _, final = r.stdout.partition(' ')
        if r.returncode != 0 or not code.startswith('2'):
            raise IOError(f"HTTP {code or r.stderr.strip()}")
        return final, open(f.name, 'rb').read()


def play_icon(package):
    """The brand's own Play Store app icon (check the developer is the brand when adding)."""
    _, html = get(f"https://play.google.com/store/apps/details?id={package}&hl=en_IN")
    m = re.search(r'<meta property="og:image" content="([^"]+)"', html.decode('utf-8', 'ignore'))
    return [re.sub(r'=[swh]\d+.*$', '', m.group(1)) + '=s512'] if m else []


def candidates(brand):
    if brand.get('play'):
        return play_icon(brand['play'])
    domain = brand['domain']
    base, html = get(f"https://{domain}/")
    page = html.decode('utf-8', 'ignore')
    found = []
    for tag in re.findall(r'<link[^>]+>', page, re.I):
        rel = re.search(r'rel=["\']([^"\']+)', tag, re.I)
        href = re.search(r'href=["\']([^"\']+)', tag, re.I)
        if not rel or not href or 'icon' not in rel.group(1).lower():
            continue
        size = re.search(r'sizes=["\'](\d+)x\d+', tag, re.I)
        apple = 'apple' in rel.group(1).lower()
        found.append((apple, int(size.group(1)) if size else 0, urljoin(base, href.group(1))))
    found.sort(reverse=True)  # apple-touch-icon first, then largest declared size
    return [u for *_, u in found] + [urljoin(base, '/apple-touch-icon.png')]


def width(path):
    out = subprocess.run(['magick', 'identify', '-format', '%w %h\n', path], capture_output=True, text=True)
    sizes = [tuple(map(int, l.split())) for l in out.stdout.split('\n') if l.strip()]
    return max((min(w, h) for w, h in sizes), default=0)


def fetch(brand, sources):
    for url in candidates(brand):
        if url.lower().endswith('.svg'):
            continue
        try:
            _, data = get(url)
        except Exception as e:  # maintainer tool: try the next candidate
            print(f"  skip {url}: {e.__class__.__name__}")
            continue
        with tempfile.TemporaryDirectory() as d:
            raw = os.path.join(d, 'raw')
            open(raw, 'wb').write(data)
            if width(raw) < 96:
                print(f"  skip {url}: too small ({width(raw)}px)")
                continue
            png = os.path.join(d, 'logo.png')
            # Largest frame (for .ico), fit into 192x192, pad transparent to square.
            subprocess.run(['magick', f"{raw}", '-background', 'none', '-flatten' if not url.lower().endswith('.ico') else '+repage',
                            '-resize', '192x192', '-gravity', 'center', '-extent', '192x192', png], check=True)
            out = os.path.join(LOGOS, f"brand_{brand['id']}.webp")
            subprocess.run(['cwebp', '-quiet', '-q', '90', png, '-o', out], check=True)
            sources[brand['id']] = url
            print(f"  ok {url} ({width(raw)}px) -> {os.path.relpath(out, ROOT)}")
            return True
    return False


def main():
    os.makedirs(LOGOS, exist_ok=True)
    brands = json.load(open(BRANDS, encoding='utf-8'))['brands']
    sources = json.load(open(SOURCES)) if os.path.exists(SOURCES) else {}
    wanted = set(sys.argv[1:]) or {b['id'] for b in brands if not os.path.exists(os.path.join(LOGOS, f"brand_{b['id']}.webp"))}
    failed = []
    for b in (b for b in brands if b['id'] in wanted):
        print(b['id'])
        try:
            if not fetch(b, sources):
                failed.append(b['id'])
        except Exception as e:
            print(f"  failed: {e.__class__.__name__}: {e}")
            failed.append(b['id'])
    json.dump(dict(sorted(sources.items())), open(SOURCES, 'w'), indent=2)
    print("failed:", failed or "none")


if __name__ == '__main__':
    main()
