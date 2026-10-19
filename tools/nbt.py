"""Minimal NBT reader/writer (Java Edition, big-endian, gzip) for structure files."""
import gzip
import io
import struct

END, BYTE, SHORT, INT, LONG, FLOAT, DOUBLE, BYTE_ARRAY, STRING, LIST, COMPOUND, INT_ARRAY, LONG_ARRAY = range(13)


class Tag:
    """Explicitly typed value used when writing."""
    def __init__(self, tag_type, value, elem_type=None):
        self.type = tag_type
        self.value = value
        self.elem_type = elem_type

    def __repr__(self):
        return f"Tag({self.type},{self.value!r})"


def Byte(v): return Tag(BYTE, v)
def Short(v): return Tag(SHORT, v)
def Int(v): return Tag(INT, v)
def Long(v): return Tag(LONG, v)
def Float(v): return Tag(FLOAT, v)
def Double(v): return Tag(DOUBLE, v)
def String(v): return Tag(STRING, v)
def List(elem_type, items): return Tag(LIST, items, elem_type)
def Compound(d): return Tag(COMPOUND, d)
def IntArray(v): return Tag(INT_ARRAY, v)


# ---------------------------------------------------------------- reading
def _read(buf, t):
    if t == BYTE: return struct.unpack(">b", buf.read(1))[0]
    if t == SHORT: return struct.unpack(">h", buf.read(2))[0]
    if t == INT: return struct.unpack(">i", buf.read(4))[0]
    if t == LONG: return struct.unpack(">q", buf.read(8))[0]
    if t == FLOAT: return struct.unpack(">f", buf.read(4))[0]
    if t == DOUBLE: return struct.unpack(">d", buf.read(8))[0]
    if t == BYTE_ARRAY:
        n = struct.unpack(">i", buf.read(4))[0]
        return list(buf.read(n))
    if t == STRING:
        n = struct.unpack(">H", buf.read(2))[0]
        return buf.read(n).decode("utf-8")
    if t == LIST:
        et = buf.read(1)[0]
        n = struct.unpack(">i", buf.read(4))[0]
        return [_read(buf, et) for _ in range(n)]
    if t == COMPOUND:
        d = {}
        while True:
            tt = buf.read(1)[0]
            if tt == END:
                return d
            n = struct.unpack(">H", buf.read(2))[0]
            name = buf.read(n).decode("utf-8")
            d[name] = _read(buf, tt)
    if t == INT_ARRAY:
        n = struct.unpack(">i", buf.read(4))[0]
        return list(struct.unpack(f">{n}i", buf.read(4 * n)))
    if t == LONG_ARRAY:
        n = struct.unpack(">i", buf.read(4))[0]
        return list(struct.unpack(f">{n}q", buf.read(8 * n)))
    raise ValueError(t)


def read_file(path):
    with gzip.open(path, "rb") as f:
        buf = io.BytesIO(f.read())
    t = buf.read(1)[0]
    n = struct.unpack(">H", buf.read(2))[0]
    buf.read(n)
    return _read(buf, t)


# ---------------------------------------------------------------- writing
def _infer(v):
    if isinstance(v, Tag): return v
    if isinstance(v, bool): return Byte(int(v))
    if isinstance(v, int): return Int(v)
    if isinstance(v, float): return Double(v)
    if isinstance(v, str): return String(v)
    if isinstance(v, dict): return Compound(v)
    raise TypeError(f"Cannot infer NBT type for {v!r}")


def _write(out, tag):
    t, v = tag.type, tag.value
    if t == BYTE: out.write(struct.pack(">b", v))
    elif t == SHORT: out.write(struct.pack(">h", v))
    elif t == INT: out.write(struct.pack(">i", v))
    elif t == LONG: out.write(struct.pack(">q", v))
    elif t == FLOAT: out.write(struct.pack(">f", v))
    elif t == DOUBLE: out.write(struct.pack(">d", v))
    elif t == STRING:
        b = v.encode("utf-8")
        out.write(struct.pack(">H", len(b)) + b)
    elif t == LIST:
        items = [_infer(i) for i in v]
        et = tag.elem_type if tag.elem_type is not None else (items[0].type if items else END)
        out.write(bytes([et]) + struct.pack(">i", len(items)))
        for i in items:
            assert i.type == et, (i, et)
            _write(out, i)
    elif t == COMPOUND:
        for k, val in v.items():
            val = _infer(val)
            kb = k.encode("utf-8")
            out.write(bytes([val.type]) + struct.pack(">H", len(kb)) + kb)
            _write(out, val)
        out.write(bytes([END]))
    elif t == INT_ARRAY:
        out.write(struct.pack(">i", len(v)) + struct.pack(f">{len(v)}i", *v))
    else:
        raise ValueError(t)


def write_file(path, root: dict):
    out = io.BytesIO()
    out.write(bytes([COMPOUND]) + struct.pack(">H", 0))
    _write(out, Compound(root))
    # mtime=0: el mismo contenido produce siempre el mismo archivo
    with open(path, "wb") as raw, gzip.GzipFile(filename="", mode="wb", fileobj=raw, mtime=0) as f:
        f.write(out.getvalue())
