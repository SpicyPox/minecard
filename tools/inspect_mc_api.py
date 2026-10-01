import struct
import zipfile
from pathlib import Path

jar = Path(r".gradle/loom-cache/minecraftMaven/net/minecraft/minecraft-merged-ee81c4ed90/26.3/minecraft-merged-ee81c4ed90-26.3.jar")
z = zipfile.ZipFile(jar)


def parse_methods(class_path: str) -> None:
	data = z.read(class_path)
	cp_count = struct.unpack(">H", data[8:10])[0]
	i = 10
	cp = [None]
	idx = 1
	while idx < cp_count:
		tag = data[i]
		i += 1
		if tag == 1:
			ln = struct.unpack(">H", data[i : i + 2])[0]
			i += 2
			s = data[i : i + ln].decode("utf-8", "replace")
			i += ln
			cp.append(("Utf8", s))
		elif tag in (7, 8, 16, 19, 20):
			cp.append((tag, struct.unpack(">H", data[i : i + 2])[0]))
			i += 2
		elif tag in (3, 4, 9, 10, 11, 12, 17, 18):
			cp.append((tag, struct.unpack(">I", data[i : i + 4])[0]))
			i += 4
		elif tag in (5, 6):
			cp.append((tag, struct.unpack(">Q", data[i : i + 8])[0]))
			i += 8
			idx += 1
			cp.append(None)
		elif tag == 15:
			cp.append((tag, data[i], struct.unpack(">H", data[i + 1 : i + 3])[0]))
			i += 3
		else:
			raise SystemExit(f"unknown tag {tag} at {i} in {class_path}")
		idx += 1
	i += 6
	ifc_count = struct.unpack(">H", data[i : i + 2])[0]
	i += 2 + 2 * ifc_count
	fields_count = struct.unpack(">H", data[i : i + 2])[0]
	i += 2
	for _ in range(fields_count):
		i += 6
		attr = struct.unpack(">H", data[i : i + 2])[0]
		i += 2
		for _ in range(attr):
			i += 2
			alen = struct.unpack(">I", data[i : i + 4])[0]
			i += 4 + alen
	methods_count = struct.unpack(">H", data[i : i + 2])[0]
	i += 2
	print("==", class_path)
	for _ in range(methods_count):
		_acc, name_i, desc_i = struct.unpack(">HHH", data[i : i + 6])
		i += 6
		name = cp[name_i][1]
		desc = cp[desc_i][1]
		if name in ("<init>", "withFont") or "Font" in name or name == "values":
			print(f"  {name}{desc}")
		attr = struct.unpack(">H", data[i : i + 2])[0]
		i += 2
		for _ in range(attr):
			i += 2
			alen = struct.unpack(">I", data[i : i + 4])[0]
			i += 4 + alen


for p in [
	"net/minecraft/server/dialog/ActionButton.class",
	"net/minecraft/server/dialog/CommonButtonData.class",
	"net/minecraft/network/chat/FontDescription$Resource.class",
	"net/minecraft/network/chat/FontDescription.class",
	"net/minecraft/network/chat/Style.class",
	"net/minecraft/server/dialog/body/PlainMessage.class",
	"net/minecraft/server/dialog/NoticeDialog.class",
	"net/minecraft/server/dialog/DialogAction.class",
]:
	parse_methods(p)
