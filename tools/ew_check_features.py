import json,glob,collections,sys
edges=collections.defaultdict(set)
for p in glob.glob(sys.argv[1]+'/data/*/worldgen/biome/*.json'):
    d=json.load(open(p))
    seq=[(i,x) for i,s in enumerate(d.get('features',[])) for x in s]
    for a,b in zip(seq,seq[1:]): edges[a].add(b)
# cycle detect
state={}
def dfs(n,path):
    state[n]=1
    for m in edges[n]:
        if state.get(m)==1: print("CICLO",path[path.index(m):] if m in path else m); return True
        if m not in state and dfs(m,path+[m]): return True
    state[n]=2
print(any(dfs(n,[n]) for n in list(edges) if n not in state))
