import struct
import zipfile
from pathlib import Path

jar = Path(r".gradle/loom-cache/minecraftMaven/net/minecraft/minecraft-merged-ee81c4ed90/26.3/minecraft-merged-ee81c4ed90-26.3.jar")
z = zipfile.ZipFile(jar)


def utf8s(class_path: str) -> list[str]:
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
			cp.append(s)
		elif tag in (7, 8, 16, 19, 20):
			i += 2
			cp.append(None)
		elif tag in (3, 4, 9, 10, 11, 12, 17, 18):
			i += 4
			cp.append(None)
		elif tag in (5, 6):
			i += 8
			cp.append(None)
			idx += 1
			cp.append(None)
		elif tag == 15:
			i += 3
			cp.append(None)
		else:
			raise SystemExit(f"tag {tag}")
		idx += 1
	return [c for c in cp if isinstance(c, str)]


names = utf8s("net/minecraft/gametest/framework/GameTestHelper.class")
for s in names:
	if "mock" in s.lower() or "player" in s.lower() or "Player" in s or s.startswith("make") or s.startswith("succeed"):
		print(s)
