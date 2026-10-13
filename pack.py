import sys, zipfile, struct, os

src, dexdir, out = sys.argv[1], sys.argv[2], sys.argv[3]
ALIGN = 4

zin = zipfile.ZipFile(src, 'r')
entries = []
for info in zin.infolist():
    entries.append((info, zin.read(info.filename)))
zin.close()

for f in sorted(os.listdir(dexdir)):
    if f.endswith('.dex'):
        with open(os.path.join(dexdir, f), 'rb') as fh:
            zi = zipfile.ZipInfo(f)
            zi.compress_type = zipfile.ZIP_DEFLATED
            zi.date_time = (2024, 1, 1, 0, 0, 0)
            zi.external_attr = 0o644 << 16
            entries.append((zi, fh.read()))

zout = zipfile.ZipFile(out, 'w')
for info, data in entries:
    zi = zipfile.ZipInfo(info.filename)
    zi.date_time = (2024, 1, 1, 0, 0, 0)
    zi.external_attr = info.external_attr or (0o644 << 16)
    # resources.arsc 必須未壓縮且 4-byte 對齊 (targetSdk >= 30)
    if info.filename.endswith('.arsc') or info.filename.endswith('.so'):
        zi.compress_type = zipfile.ZIP_STORED
        offset = zout.fp.tell() + 30 + len(zi.filename.encode('utf-8'))
        pad = (-offset) % ALIGN
        if pad:
            pad += ALIGN  # 需要 4 bytes 的 extra field header
            while pad % ALIGN != ((-offset) % ALIGN) % ALIGN or (offset + pad) % ALIGN != 0:
                pad += 1
            zi.extra = struct.pack('<HH', 0xd935, pad - 4) + b'\x00' * (pad - 4)
    else:
        zi.compress_type = zipfile.ZIP_DEFLATED
    zout.writestr(zi, data)
zout.close()

# 驗證對齊
z = zipfile.ZipFile(out)
with open(out, 'rb') as f:
    for i in z.infolist():
        if i.compress_type == zipfile.ZIP_STORED:
            f.seek(i.header_offset)
            h = f.read(30)
            n, m = struct.unpack('<HH', h[26:30])
            data_off = i.header_offset + 30 + n + m
            print('  STORED %-20s data@%d align4=%s' % (i.filename, data_off, data_off % 4 == 0))
print('  entries:', len(z.infolist()))
