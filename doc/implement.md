# Minecard: thứ tự implement

Thiết kế nằm ở [ke-hoach.md](ke-hoach.md). File này chỉ nói làm theo mốc nào và Cursor phải giữ những ràng buộc nào.

Làm đúng một mốc rồi mới sang mốc sau. Mốc 1 chặn mọi thứ khác: chưa thấy đủ 52 quân trên GUI thì không viết ví, phòng, Blackjack, Poker hay Liar’s Bar.

## Mốc

1. **52 quân trên GUI.** Dựng project Fabric 26.3 vừa đủ để mở dialog. Model 4 chất × 13 rank. Hình lá lấy từ bộ trong `assets/` (test trước: Kenney large, CC0) qua `python tools/import_deck.py kenney_large`, cộng mặt sau. Resource pack font bitmap, mỗi quân một glyph; server mời pack lúc join (không force). Layout: hàng ngang `CardGrid` — Poker/Blackjack 5 hàng (1 cái hoặc bài chung + 4 người). `/minecard cards [poker|blackjack]` showcase. Client nhận pack phải nhìn ra mặt lá đúng chất/rank. Hai joker chỉ là asset phụ, không tính vào điều kiện xong mốc này. **Mốc này đã xong.**
2. **Ví và escrow.** `WalletSavedData` + `Escrow` (balance rồi túi), menu nạp/rút, SQLite `ledger`/`player_stats` khi settle, solo session persist (`SessionSavedData`). Chi tiết [data-and-history.md](data-and-history.md). **Đang hoạt động (cốt lõi).**
3. **Phòng.** Wizard tạo phòng (game → luật BJ → cược), chat mã + `[Vào bàn]`, menu nhập mã, sảnh Ready, host Start — `Rooms` / `BjRoom`. **Đang hoạt động (MVP).**
4. **Blackjack.** Chơi multiplayer qua phòng (`TableBlackjack`); menu không còn solo. Hit/stand/double/split/insurance, timer, 3:2, glyph, shoe/config. Nhà cái (host) bấm Hit/Stand theo luật. Hết ván ở lại bàn: Play again → Confirm → host Deal. Bank qua sgui: nạp = staging → Confirm; rút nhiều item. Mid-hand không resume — hoàn escrow, giữ sảnh (`RoomSavedData`). Pack: `packHost`/`packPort` trong config; `/minecard pack` gửi lại lời mời.
5. **LuckPerms, hồ sơ, lệnh admin.** `/minecard admin stats|ledger|end` (OP); dialog Hồ sơ + Bank. **Đang hoạt động (MVP).**
6. **Poker NLHE (slice 1 đang làm / đã có lõi).** `PokerHandEval`, `TablePoker`, `PokerRoom`/`PokerRooms`, wizard tạo phòng Poker, `TablePokerDialog` (2 cột + raise input). Chưa: side pot đầy đủ, persist mid-hand, Liar’s Bar.

Khi bắt đầu code, ghi mục «Ràng buộc cho Cursor» bên dưới vào [`.cursor/rules/minecard.mdc`](../.cursor/rules/minecard.mdc) với `alwaysApply: true`, rồi mới viết mốc 1.

## Ràng buộc cho Cursor

- Đọc [ke-hoach.md](ke-hoach.md) trước khi đổi luật chơi hoặc giao diện. Đọc file này trước khi chọn việc sẽ code.
- Không bắt đầu mốc sau khi mốc trước chưa chạy được. Mốc 1 xong chỉ khi dialog hiện đủ 52 quân, đúng hình, đúng chất và rank.
- Không đăng ký item, block, entity, menu hay packet riêng. Không entrypoint client. Client vanilla phải vào được.
- Mặt lá trên GUI là glyph của bộ 52. Không dùng len màu, tên vật phẩm, hay ký tự chữ làm mặt lá.
- Không quy đổi vật phẩm, không chip, không PolyWallet, không Vault.
- LuckPerms chỉ là plugin server chủ cài thêm. Không có plugin quyền thì người thường dùng lệnh chơi, OP có quyền admin.
- Trừ cược: balance trước, túi sau. Thiếu thì từ chối cả khoản. Không xóa đồ khi túi đầy, người thoát, hoặc server tắt.
- Blackjack không chia joker. Poker và Liar’s Bar không nằm trong cùng đợt với mốc 1.
- Chuỗi người chơi thấy đi qua Server Translations API (`vi_vn`, `en_us`).
- Sửa hành vi thì sửa [ke-hoach.md](ke-hoach.md). Sửa thứ tự làm hoặc ràng buộc thì sửa file này.

## Kiểm tra

Mốc 1, trước mọi tính năng khác: `/minecard cards` trên client vanilla đã nhận resource pack. Đếm đủ 52 mặt, mỗi chất 13 quân, A đến K không trùng hình. `gradlew build` phải qua.

Các mốc sau: logic điểm bài, trả thưởng 3:2, thứ tự balance rồi túi, từ chối khi không đủ, hoàn khi túi đầy, và hoàn cả bàn khi snapshot hỏng có unit test không cần chạy game. Chơi thử trên `runServer`: tạo phòng bằng táo vàng đang cầm, người thứ hai vào bằng balance, lá trên bàn là glyph mốc 1, rút đồ về túi đầy để thấy phần dư rơi xuống đất.
