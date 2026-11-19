import os,re,sys,collections
sys.path.insert(0,'/tmp')
from dep import *
ANY = re.compile(r'\b(?:fun|val|var|class|object|interface|typealias)\s+(?:<(?:[^<>]|<[^<>]*>)*>\s*)?(?:[\w.]+(?:<(?:[^<>]|<[^<>]*>)*>)?\??\.)?`?(\w+)`?')
ENUMC = re.compile(r'enum class \w+[^{]*\{([^}]*)\}', re.S)
def names(roots):
    out=collections.defaultdict(set)
    for r in roots:
        if not os.path.isdir(r): continue
        for dp,_,fs in os.walk(r):
            for f in fs:
                if not f.endswith(('.kt','.java')): continue
                p=os.path.join(dp,f); s=strip(open(p,errors='ignore').read())
                for m in ANY.finditer(s): out[m.group(1)].add(p)
                for m in ENUMC.finditer(s):
                    for e in re.findall(r'^\s*([A-Z_][A-Z0-9_]*)\b', m.group(1), re.M): out[e].add(p)
                if f.endswith('.java'):
                    for m in re.finditer(r'\b(\w+)\s*\(', s): out[m.group(1)].add(p)
    return out
L=names(LOCAL_ROOTS); U=names(UP_ROOTS)
files=open('/tmp/copyfiles').read().split()
base=AT+'/app/src/main/kotlin/moe/rukamori/archivetune/'
res=collections.defaultdict(set)
for f in files:
    s=strip(open(base+f,errors='ignore').read())
    own=set(m.group(1) for m in ANY.finditer(s))
    for m in re.finditer(r'(?<![\w])\.?\b([A-Za-z_]\w*)\b', s):
        n=m.group(1)
        if n in own or n in L: continue
        if n in U: res[n].add(os.path.basename(f))
for n,v in sorted(res.items()):
    ups=sorted(set(x.replace(AT+'/','') for x in U[n]))[:2]
    print(n, sorted(v)[:3], ups)
