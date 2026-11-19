import sys, os, re
sys.path.insert(0,'/tmp')
from dep import *
L, Lf = index(LOCAL_ROOTS)
U, Uf = index(UP_ROOTS)
print('local pkgs', len(L), 'files', len(Lf), '| upstream files', len(Uf), file=sys.stderr)
SET = AT + '/app/src/main/kotlin/moe/rukamori/archivetune/ui/screens/settings/'
B = AT + '/app/src/main/kotlin/moe/rukamori/archivetune/'
seeds = [SET + f + '.kt' for f in sys.argv[1].split(',')] + [B + x for x in os.environ.get('COPY','').split(',') if x]
EXCL = set(B + x for x in os.environ.get('EXCL','').split(',') if x)
# map: which local file corresponds to upstream (same relative path)
def local_path(up):
    for r in UP_ROOTS:
        if up.startswith(r + '/'):
            rel = up[len(r)+1:]
            for lr in LOCAL_ROOTS:
                if os.path.exists(os.path.join(lr, rel)): return os.path.join(lr, rel)
    return None
DEPTH=int(os.environ.get('DEPTH','1')); depth={x:0 for x in seeds}
todo = list(seeds); seen = set(); need = {}  # upfile -> reasons
unres_ext = collections.defaultdict(set)
partial = collections.defaultdict(set); shim = collections.defaultdict(set)
while todo:
    f = todo.pop()
    if f in seen: continue
    seen.add(f)
    if depth.get(f,0) >= DEPTH and f not in seeds: continue
    s = open(f, encoding='utf-8', errors='ignore').read()
    pkg = Uf.get(f, '')
    body = strip(s)
    for imp in IMP.findall(s):
        if not imp.startswith('moe.rukamori') : continue
        if resolves(L, imp): continue
        cands = find_files(U, imp)
        if not cands:
            unres_ext[imp].add(os.path.basename(f)); continue
        for c in cands:
            if c in EXCL: shim[imp].add(os.path.basename(f)); continue
            if local_path(c): partial[local_path(c)].add(imp.split('.')[-1]); continue
            need.setdefault(c, set()).add(imp.split('.')[-1] + ' <- ' + os.path.basename(f))
            depth.setdefault(c, depth.get(f,0)+1); todo.append(c)
    # same-package refs
    idents = set(IDENT.findall(body))
    for pk in [pkg] + wildcard_pkgs(s):
        if pk not in U: continue
        for name in idents:
            if name in U[pk] and not (pk in L and name in L[pk]):
                for c in U[pk][name]:
                    if c in EXCL: shim[pk+'.'+name].add(os.path.basename(f)); continue
                    if c == f: continue
                    if local_path(c): partial[local_path(c)].add(name); continue
                    need.setdefault(c, set()).add(name + ' <- ' + os.path.basename(f) + ' (pkg)')
                    depth.setdefault(c, depth.get(f,0)+1); todo.append(c)
rel = lambda p: p.replace(AT + '/', '')
tot = 0
for c in sorted(need):
    if c in seeds: continue
    n = sum(1 for _ in open(c, errors='ignore')); tot += n
    print(f'{n:6d} {rel(c)}  :: ' + '; '.join(sorted(need[c]))[:300])
print('TOTAL new lines', tot, 'files', len(need))
for k, v in sorted(unres_ext.items()):
    print('UNRESOLVED', k, sorted(v)[:3])

for k,v in sorted(partial.items()):
    print('PARTIAL', k.replace(WS+'/',''), sorted(v))
for k,v in sorted(shim.items()):
    print('SHIM', k, sorted(v)[:4])
