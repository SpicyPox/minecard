import struct
import zipfile
from pathlib import Path

jar = next(Path(".gradle/loom-cache").rglob("minecraft-merged*26.3*.jar"))
z = zipfile.ZipFile(jar)


def strings(path: str) -> list[str]:
	data = z.read(path)
	i = 10
	cp_count = struct.unpack(">H", data[8:10])[0]
	idx = 1
	out: list[str] = []
	while idx < cp_count:
		tag = data[i]
		i += 1
		if tag == 1:
			ln = struct.unpack(">H", data[i : i + 2])[0]
			i += 2
			out.append(data[i : i + ln].decode("utf-8", "replace"))
			i += ln
		elif tag in (7, 8, 16, 19, 20):
			i += 2
		elif tag in (3, 4, 9, 10, 11, 12, 17, 18):
			i += 4
		elif tag in (5, 6):
			i += 8
			idx += 1
		elif tag == 15:
			i += 3
		else:
			raise SystemExit(tag)
		idx += 1
	return out


for path in [
	"net/minecraft/network/chat/ClickEvent$RunCommand.class",
	"net/minecraft/network/chat/ClickEvent$Custom.class",
	"net/minecraft/server/dialog/action/CommandTemplate.class",
]:
	print("===", path)
	for s in strings(path):
		if s.startswith("(") or "init" in s or "command" in s.lower() or "Identifier" in s or "Optional" in s or "template" in s.lower():
			print(" ", s)
