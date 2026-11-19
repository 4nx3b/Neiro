import os, re, sys, json, collections
WS = '/home/user/workspace'
AT = '/tmp/AT'
LOCAL_ROOTS = [WS + '/archivetuneplayer/src/main/kotlin', WS + '/archivetuneplayer/src/main/java'] + \
    [os.path.join(WS, 'archivetune', m, 'src/main', d) for m in os.listdir(WS + '/archivetune') for d in ('kotlin', 'java')]
UP_ROOTS = [AT + '/app/src/main/kotlin', AT + '/app/src/main/java'] + \
    [os.path.join(AT, m, 'src/main', d) for m in ['core', 'canvas', 'lastfm', 'spotifycore', 'jiosaavn', 'musixmatch', 'shazamkit', 'morideobfuscator', 'moriextractor', 'lyrics/kugou', 'lyrics/lrclib', 'lyrics/betterlyrics', 'lyrics/unison', 'lyrics/youlyplus'] for d in ('kotlin', 'java')]

DECL = re.compile(r'^(?:@\S+\s+)*(?:(?:public|internal|private|protected|data|sealed|enum|abstract|open|inline|value|annotation|const|suspend|operator|infix|tailrec|fun interface|expect|actual|override|lateinit)\s+)*(class|object|interface|fun|val|var|typealias)\s+(?:<(?:[^<>]|<(?:[^<>]|<[^<>]*>)*>)*>\s*)?(?:([A-Za-z_][\w.]*(?:<(?:[^<>]|<[^<>]*>)*>)?\??)\.)?`?([A-Za-z_]\w*)`?', re.M)
PKG = re.compile(r'^package\s+([\w.]+)', re.M)
IMP = re.compile(r'^import\s+([\w.]+)(?:\.\*)?(?:\s+as\s+\w+)?', re.M)

def index(roots):
    decl = collections.defaultdict(lambda: collections.defaultdict(set))  # pkg -> name -> files
    files = {}
    for r in roots:
        if not os.path.isdir(r): continue
        for dp, _, fs in os.walk(r):
            for f in fs:
                if not f.endswith(('.kt', '.java')): continue
                p = os.path.join(dp, f)
                try: s = open(p, encoding='utf-8', errors='ignore').read()
                except: continue
                m = PKG.search(s); pkg = m.group(1) if m else ''
                files[p] = pkg
                if f.endswith('.java'):
                    for mm in re.finditer(r'(?:class|interface|enum)\s+(\w+)', s):
                        decl[pkg][mm.group(1)].add(p)
                    continue
                # top-level-ish: lines with no indentation or nested classes (indent any) for class names
                for mm in DECL.finditer(s):
                    line_start = s.rfind('\n', 0, mm.start()) + 1
                    indent = mm.start() - line_start
                    kind, recv, name = mm.group(1), mm.group(2), mm.group(3)
                    if indent == 0:
                        if 'private ' in mm.group(0): continue
                        decl[pkg][name].add(p)
                    elif kind in ('class', 'object', 'interface'):
                        decl[pkg]['#nested#' + name].add(p)
    return decl, files

def resolves(decl, imp):
    parts = imp.split('.')
    # package import a.b.c.Name
    for cut in range(len(parts) - 1, 0, -1):
        pkg = '.'.join(parts[:cut]); rest = parts[cut:]
        if pkg in decl:
            if rest[0] in decl[pkg]:
                return True
    return False

def find_files(decl, imp):
    parts = imp.split('.')
    for cut in range(len(parts) - 1, 0, -1):
        pkg = '.'.join(parts[:cut]); rest = parts[cut:]
        if pkg in decl and rest[0] in decl[pkg]:
            return decl[pkg][rest[0]]
    return set()

def wildcard_pkgs(s):
    return re.findall(r'^import\s+([\w.]+)\.\*', s, re.M)

IDENT = re.compile(r'\b([A-Za-z_]\w*)\b')

def strip(s):
    s = re.sub(r'/\*.*?\*/', ' ', s, flags=re.S)
    s = re.sub(r'//[^\n]*', ' ', s)
    s = re.sub(r'"(?:\\.|[^"\\])*"', '""', s)
    return s
