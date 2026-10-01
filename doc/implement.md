# Minecard: thứ tự implement

Thiết kế nằm ở [ke-hoach.md](ke-hoach.md). File này chỉ nói làm theo mốc nào và Cursor phải giữ những ràng buộc nào.

Làm đúng một mốc rồi mới sang mốc sau. Mốc 1 chặn mọi thứ khác: chưa thấy đủ 52 quân trên GUI thì không viết ví, phòng, Blackjack, Poker hay Liar’s Bar.

## Mốc

1. **52 quân trên GUI.** Dựng project Fabric 26.3 vừa đủ để mở dialog. Model 4 chất × 13 rank. Hình lá lấy từ bộ trong `assets/` (test trước: Kenney large, CC0) qua `python tools/import_deck.py kenney_large`, cộng mặt sau. Resource pack font bitmap, mỗi quân một glyph; server mời pack lúc join (không force). Layout: hàng ngang `CardGrid` — Poker/Blackjack 5 hàng (1 cái hoặc bài chung + 4 người). `/minecard cards [poker|blackjack]` showcase. Client nhận pack phải nhìn ra mặt lá đúng chất/rank. Hai joker chỉ là asset phụ, không tính vào điều kiện xong mốc này.
2. **Ví và escrow.** Balance theo item, trừ balance rồi tới túi, hoàn khi ngắt kết nối, túi đầy, cả bàn thoát, server dừng. Chi tiết ở [ke-hoach.md](ke-hoach.md). Solo `/minecard bj` đang dùng `DemoBank` + `BlackjackRoundState` tạm; mốc này thay bằng SavedData thật.
3. **Phòng.** Tạo, mời, chat công khai, sảnh.
4. **Blackjack.** Luật đầy đủ (đã có hit/stand/double/split + animate trong demo solo), timer, trả thưởng, phòng nhiều ghế, dùng đúng glyph của mốc 1.
5. **LuckPerms, hồ sơ, lệnh admin.**
6. **Poker và Liar’s Bar.** Chưa làm trong các mốc trên.

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
