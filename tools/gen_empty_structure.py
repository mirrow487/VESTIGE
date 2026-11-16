"""Write a minimal empty structure template (gzipped NBT) used by GameTests.

Usage: python3 -I tools/gen_empty_structure.py <output.nbt> [size]
DataVersion 3955 = Minecraft 1.21.1.
"""
import gzip
import struct
import sys


def _s(x):
    b = x.encode()
    return struct.pack('>H', len(b)) + b


def _tag(t, name, payload):
    return bytes([t]) + _s(name) + payload


def _i(v):
    return struct.pack('>i', v)


def _list(t, items):
    return bytes([t]) + _i(len(items)) + b''.join(items)


def _compound(*tags):
    return b''.join(tags) + b'\x00'


def main():
    out = sys.argv[1]
    n = int(sys.argv[2]) if len(sys.argv) > 2 else 3
    root = _tag(10, '', _compound(
        _tag(3, 'DataVersion', _i(3955)),
        _tag(9, 'size', _list(3, [_i(n), _i(n), _i(n)])),
        _tag(9, 'palette', _list(10, [_compound(_tag(8, 'Name', _s('minecraft:air')))])),
        _tag(9, 'blocks', _list(10, [])),
        _tag(9, 'entities', _list(10, []))))
    with gzip.open(out, 'wb') as f:
        f.write(root)


if __name__ == '__main__':
    main()
