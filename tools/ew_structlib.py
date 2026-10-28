"""Utilidades para construir plantillas de estructura (.nbt) de Minecraft 1.21.1 desde Python.

Convenciones: norte = -z, sur = +z, oeste = -x, este = +x.
Los edificios se diseñan con la fachada mirando al OESTE (x = 0); el juego los rota al colocarlos.
"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
import nbt  # noqa: E402

NS = "evolvedwarfare"
DATA_VERSION = 3955  # Minecraft 1.21.1

DIRS = {"north": (0, -1), "south": (0, 1), "west": (-1, 0), "east": (1, 0)}
OPPOSITE = {"north": "south", "south": "north", "east": "west", "west": "east", "up": "down", "down": "up"}


def S(name, **props):
    """Estado de bloque: S('oak_stairs', facing='east')."""
    if ":" not in name:
        name = "minecraft:" + name
    return (name, {k: (str(v).lower() if isinstance(v, bool) else str(v)) for k, v in props.items()})


def state_str(st):
    name, props = st
    if not props:
        return name
    return name + "[" + ",".join(f"{k}={v}" for k, v in sorted(props.items())) + "]"


def short(st):
    return st[0].split(":", 1)[1]


# ---------------------------------------------------------------- clasificación de bloques
PARTIAL = ("stairs", "slab", "door", "pane", "torch", "lantern", "bed", "carpet", "sign", "potted", "fence",
           "_wall", "banner", "ladder", "trapdoor", "air", "jigsaw", "chest", "campfire", "anvil", "grindstone",
           "bars", "button", "pressure_plate", "flower_pot", "cauldron", "water", "lava", "wheat", "carrots",
           "potatoes", "beetroots", "composter", "rail", "chain", "bell", "lectern", "brewing", "candle",
           "head", "skull", "bush", "sapling", "grass", "fern", "flower", "tulip", "poppy", "dandelion", "portcullis")


def is_full(st):
    if st is None:
        return False
    n = short(st)
    if n in ("air", "structure_void", "water", "lava"):
        return False
    if n.endswith("_wall") or n.endswith("_fence") or "fence_gate" in n:
        return False
    if n in FULL_EXCEPTIONS:
        return True
    return not any(p in n for p in PARTIAL)


FULL_EXCEPTIONS = {"grass_block", "hay_block", "barrel", "crafting_table", "smithing_table", "fletching_table",
                   "cartography_table", "chiseled_stone_bricks"}


def is_fence(st):
    return st is not None and short(st).endswith("_fence") and "nether" not in short(st)


def is_pane(st):
    return st is not None and (short(st).endswith("glass_pane") or short(st) == "iron_bars")


def is_wall(st):
    n = short(st) if st else ""
    return n.endswith("_wall") and not any(k in n for k in ("torch", "sign", "banner", "head", "skull", "fan"))


# ---------------------------------------------------------------- plantilla
class Template:
    def __init__(self, sx, sy, sz):
        self.size = (sx, sy, sz)
        self.blocks = {}   # (x,y,z) -> (state, be_nbt)
        self.entities = []

    def inside(self, x, y, z):
        return 0 <= x < self.size[0] and 0 <= y < self.size[1] and 0 <= z < self.size[2]

    def put(self, x, y, z, st, be=None):
        if isinstance(st, str):
            st = S(st)
        assert self.inside(x, y, z), (x, y, z, st, self.size)
        self.blocks[(x, y, z)] = (st, be)

    def get(self, x, y, z):
        v = self.blocks.get((x, y, z))
        return v[0] if v else None

    def remove(self, x, y, z):
        self.blocks.pop((x, y, z), None)

    def fill(self, x0, y0, z0, x1, y1, z1, st, only_empty=False):
        for x in range(min(x0, x1), max(x0, x1) + 1):
            for y in range(min(y0, y1), max(y0, y1) + 1):
                for z in range(min(z0, z1), max(z0, z1) + 1):
                    if only_empty and (x, y, z) in self.blocks:
                        continue
                    self.put(x, y, z, st)

    # ---- piezas especiales
    def jigsaw(self, x, y, z, orientation, name, target, pool, final_state="minecraft:structure_void", joint=None):
        if joint is None:
            joint = "rollable" if orientation.startswith(("up", "down")) else "aligned"
        be = {
            "id": "minecraft:jigsaw",
            "name": name,
            "target": target,
            "pool": pool,
            "final_state": final_state,
            "joint": joint,
            "placement_priority": 0,
            "selection_priority": 0,
        }
        self.put(x, y, z, S("jigsaw", orientation=orientation), be)

    def npc_slot(self, x, y, z, pool=f"{NS}:npc/recruits"):
        """Sitio donde puede aparecer un NPC: (x,y,z) es el bloque de SUELO; el NPC aparece encima."""
        floor = self.get(x, y, z)
        assert floor is not None and is_full(floor), ("npc_slot necesita suelo macizo", x, y, z, floor)
        for dy in (1, 2):
            above = self.get(x, y + dy, z)
            assert above is None or short(above) == "air", ("npc_slot sin hueco", x, y + dy, z, above)
        self.jigsaw(x, y, z, "up_north", f"{NS}:npc", f"{NS}:npc", pool, state_str(floor), "rollable")

    def entity(self, data, x, y, z, yaw=0.0):
        data = dict(data)
        data["Rotation"] = nbt.List(nbt.FLOAT, [nbt.Float(float(yaw)), nbt.Float(0.0)])
        self.entities.append({
            "nbt": data,
            "blockPos": nbt.List(nbt.INT, [x, y, z]),
            "pos": nbt.List(nbt.DOUBLE, [x + 0.5, float(y), z + 0.5]),
        })

    # ---- conexiones de vallas, paneles y muros (el juego no las recalcula al generar)
    def connect(self):
        for (x, y, z), (st, be) in list(self.blocks.items()):
            name, props = st
            if is_fence(st) or is_pane(st):
                p = dict(props)
                for d, (dx, dz) in DIRS.items():
                    n = self.get(x + dx, y, z + dz)
                    if is_fence(st):
                        ok = is_fence(n) or is_full(n) or (n is not None and "fence_gate" in short(n))
                    else:
                        ok = is_pane(n) or is_full(n) or is_wall(n)
                    p[d] = "true" if ok else "false"
                p.setdefault("waterlogged", "false")
                self.blocks[(x, y, z)] = ((name, p), be)
            elif is_wall(st):
                p = dict(props)
                con = {}
                for d, (dx, dz) in DIRS.items():
                    n = self.get(x + dx, y, z + dz)
                    con[d] = is_wall(n) or is_full(n) or is_pane(n) or (n is not None and "fence_gate" in short(n))
                    p[d] = "low" if con[d] else "none"
                above = self.get(x, y + 1, z)
                straight = (con["north"] and con["south"] and not con["east"] and not con["west"]) or \
                           (con["east"] and con["west"] and not con["north"] and not con["south"])
                p["up"] = "false" if (straight and (above is None or short(above) == "air")) else "true"
                p.setdefault("waterlogged", "false")
                self.blocks[(x, y, z)] = ((name, p), be)

    # ---- guardado
    def to_nbt(self):
        self.connect()
        palette, index = [], {}

        def sid(st):
            name, props = st
            key = (name, tuple(sorted(props.items())))
            if key not in index:
                entry = {"Name": name}
                if props:
                    entry["Properties"] = dict(sorted(props.items()))
                index[key] = len(palette)
                palette.append(entry)
            return index[key]

        def order(item):
            pos, (st, be) = item
            group = 2 if be is not None else (0 if is_full(st) else 1)
            return (group, pos[1], pos[0], pos[2])

        block_list = []
        for pos, (st, be) in sorted(self.blocks.items(), key=order):
            entry = {"pos": nbt.List(nbt.INT, list(pos)), "state": sid(st)}
            if be is not None:
                entry["nbt"] = be
            block_list.append(entry)
        return {
            "DataVersion": DATA_VERSION,
            "size": nbt.List(nbt.INT, list(self.size)),
            "palette": nbt.List(nbt.COMPOUND, palette),
            "blocks": nbt.List(nbt.COMPOUND, block_list),
            "entities": nbt.List(nbt.COMPOUND, self.entities),
        }

    def save(self, path):
        os.makedirs(os.path.dirname(path), exist_ok=True)
        nbt.write_file(path, self.to_nbt())

    def jigsaws(self):
        out = []
        for pos, (st, be) in self.blocks.items():
            if short(st) == "jigsaw":
                out.append((pos, st[1]["orientation"], be))
        return out


# ---------------------------------------------------------------- piezas de construcción
def stairs(base, facing, half="bottom"):
    return S(base + "_stairs" if not base.endswith("_stairs") else base, facing=facing, half=half, shape="straight",
             waterlogged="false")


def slab(base, kind="bottom"):
    return S(base if base.endswith("_slab") else base + "_slab", type=kind, waterlogged="false")


def door(t, x, y, z, facing, kind="spruce_door", hinge="right"):
    t.put(x, y, z, S(kind, facing=facing, half="lower", hinge=hinge, open="false", powered="false"))
    t.put(x, y + 1, z, S(kind, facing=facing, half="upper", hinge=hinge, open="false", powered="false"))


def bed(t, x, y, z, facing, color="red"):
    """Pie en (x,y,z); la cabecera queda hacia 'facing'."""
    dx, dz = DIRS[facing]
    t.put(x, y, z, S(f"{color}_bed", facing=facing, occupied="false", part="foot"))
    t.put(x + dx, y, z + dz, S(f"{color}_bed", facing=facing, occupied="false", part="head"))


def lantern(hanging=False):
    return S("lantern", hanging=hanging, waterlogged="false")


def chest(facing, loot=None):
    be = {"id": "minecraft:chest"}
    if loot:
        be["LootTable"] = loot
    return S("chest", facing=facing, type="single", waterlogged="false"), be


def barrel(facing="up", loot=None):
    be = {"id": "minecraft:barrel"}
    if loot:
        be["LootTable"] = loot
    return S("barrel", facing=facing, open="false"), be


def text(t, **style):
    return json.dumps(dict(text=t, **style), ensure_ascii=False)


def wall_sign(kind, facing, lines, glowing=True, color="black"):
    msgs = [text(l) if isinstance(l, str) else text(l[0], **l[1]) for l in lines]
    while len(msgs) < 4:
        msgs.append('""')
    be = {
        "id": "minecraft:sign",
        "is_waxed": nbt.Byte(1),
        "front_text": {"messages": nbt.List(nbt.STRING, msgs), "color": color,
                       "has_glowing_text": nbt.Byte(1 if glowing else 0)},
        "back_text": {"messages": nbt.List(nbt.STRING, ['""'] * 4), "color": "black",
                      "has_glowing_text": nbt.Byte(0)},
    }
    return S(kind, facing=facing, waterlogged="false"), be


def item(name, count=1):
    if not name:
        return {}
    if ":" not in name:
        name = "minecraft:" + name
    return {"id": name, "count": count}


def armor_stand(head=None, chest_=None, legs=None, feet=None, main=None, off=None):
    return {
        "id": "minecraft:armor_stand",
        "ArmorItems": nbt.List(nbt.COMPOUND, [item(feet), item(legs), item(chest_), item(head)]),
        "HandItems": nbt.List(nbt.COMPOUND, [item(main), item(off)]),
        "ShowArms": nbt.Byte(1),
        "Invisible": nbt.Byte(0),
        "NoBasePlate": nbt.Byte(0),
    }


YAW = {"south": 0.0, "west": 90.0, "north": 180.0, "east": -90.0}


def gable_x(t, xa, xb, z0, z1, ybase, roof_stairs, roof_slab, gable_xs=(), gable_block=None):
    """Tejado a dos aguas con la cumbrera a lo largo de X (aleros en z0 y z1)."""
    w = z1 - z0 + 1
    half = w // 2
    for k in range(half):
        y = ybase + k
        for x in range(xa, xb + 1):
            t.put(x, y, z0 + k, stairs(roof_stairs, "south"))
            t.put(x, y, z1 - k, stairs(roof_stairs, "north"))
        if k >= 1 and gable_block is not None:
            for gx in gable_xs:
                for z in range(z0 + k + 1, z1 - k):
                    t.put(gx, y, z, gable_block)
    if w % 2 == 1:
        for x in range(xa, xb + 1):
            t.put(x, ybase + half, z0 + half, slab(roof_slab))


def gable_z(t, za, zb, x0, x1, ybase, roof_stairs, roof_slab, gable_zs=(), gable_block=None):
    """Tejado a dos aguas con la cumbrera a lo largo de Z (aleros en x0 y x1)."""
    w = x1 - x0 + 1
    half = w // 2
    for k in range(half):
        y = ybase + k
        for z in range(za, zb + 1):
            t.put(x0 + k, y, z, stairs(roof_stairs, "east"))
            t.put(x1 - k, y, z, stairs(roof_stairs, "west"))
        if k >= 1 and gable_block is not None:
            for gz in gable_zs:
                for x in range(x0 + k + 1, x1 - k):
                    t.put(x, y, gz, gable_block)
    if w % 2 == 1:
        for z in range(za, zb + 1):
            t.put(x0 + half, ybase + half, z, slab(roof_slab))
