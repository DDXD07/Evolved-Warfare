"""Busca fragmentos del código de Minecraft (fuentes descompiladas por Loom) y los publica como anotaciones."""
import glob, os, re, sys, zipfile

jars = [j for j in glob.glob(os.path.expanduser("~/.gradle/**/*sources.jar"), recursive=True) +
        glob.glob(".gradle/**/*sources.jar", recursive=True) if "minecraft" in os.path.basename(j)]
print("jars:", jars)
src = {}
for j in jars:
    with zipfile.ZipFile(j) as z:
        for n in z.namelist():
            if n.endswith(".java"):
                src.setdefault(n, z.read(n).decode("utf-8", "replace"))
print("clases:", len(src))

def method(cls, name, n=40, nth=0):
    text = src.get(cls)
    if text is None:
        return f"NO ENCONTRADA {cls}"
    lines = text.split("\n")
    hits = [i for i, l in enumerate(lines) if re.search(r"\b" + re.escape(name) + r"\s*\(", l) and ("public" in l or "private" in l or "protected" in l or "static" in l or l.strip().startswith(("void", "boolean")))]
    if len(hits) <= nth:
        return f"SIN {name} en {cls}"
    i = hits[nth]
    return "\n".join(f"{k+1}: {lines[k]}" for k in range(max(0, i - 2), min(len(lines), i + n)))

def usages(pattern, limit=40):
    out = []
    for n, t in src.items():
        for k, l in enumerate(t.split("\n")):
            if re.search(pattern, l):
                out.append(f"{n}:{k+1}: {l.strip()}")
    return "\n".join(out[:limit]) or "NINGUNO"

def note(kind, title, msg):
    msg = msg[:3800].replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")
    print(f"::{kind} title={title}::{msg}")

group = sys.argv[1]
W = "net/minecraft/world/gen/StructureWeightSampler.java"
if group == "1":
    note("notice", "weight", method(W, "createStructureWeightSampler", 45))
    note("notice", "intersectsChunk-uses", usages(r"intersectsChunk\("))
    note("notice", "intersectsChunk-def", method("net/minecraft/structure/StructurePiece.java", "intersectsChunk", 8))
    note("notice", "single-placement", method("net/minecraft/structure/pool/SinglePoolElement.java", "createPlacementData", 22))
    note("notice", "spawnEntities", method("net/minecraft/structure/StructureTemplate.java", "spawnEntities", 32))
    note("notice", "start-place", method("net/minecraft/structure/StructureStart.java", "place", 24))
    note("notice", "blockbox-chunk", method("net/minecraft/world/gen/chunk/ChunkGenerator.java", "getBlockBoxForChunk", 10))
    note("notice", "gen-return", usages(r"new Structure\.StructurePosition\(|StructurePosition\(BlockPos", 10))
    note("notice", "empty-elem", method("net/minecraft/structure/pool/EmptyPoolElement.java", "EmptyPoolElement", 6))
if group == "2":
    t = src.get("net/minecraft/structure/StructureTemplate.java", "")
    i = t.find("shouldUpdateNeighbors()")
    note("notice", "place-updateNeighbors", t[max(0, i - 1500): i + 1800] if i >= 0 else "SIN shouldUpdateNeighbors")
    note("notice", "features-heightmaps", usages(r"populateHeightmaps|POST_FEATURES", 20))
    note("notice", "piecetype-simple", method("net/minecraft/structure/StructurePieceType.java", "load", 6))
    note("notice", "pieceslist-fromNbt", method("net/minecraft/structure/StructurePiecesList.java", "fromNbt", 30))
