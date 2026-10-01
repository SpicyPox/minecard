# Minecard — Game Rules: Blackjack & Poker

> Game rule thiết kế cho Minecard Fabric 26.3.  
> Kiến trúc tổng: [ke-hoach.md](ke-hoach.md) · thứ tự mốc: [implement.md](implement.md) · lưu trữ/lịch sử: [data-and-history.md](data-and-history.md).

### Trạng thái triển khai

| Tag | Nghĩa |
|-----|--------|
| **DEMO** | Solo `/bj` đang chạy (RAM / `DemoBank`) |
| **ROOM** | Target khi có phòng + ví SavedData (mốc 2–4) |
| **PLANNED** | Luật giữ trong doc, chưa code |

### Đã ship (DEMO) vs target

| Chủ đề | DEMO hiện tại | Target (ROOM / PLANNED) |
|--------|----------------|-------------------------|
| Economy | `DemoBank` diamond in-RAM, bet cố định 10 | Ví SavedData + inventory escrow, item phòng |
| Shoe | 1×52, reshuffle khi `<15` lá | 6 deck (cấu hình), ngưỡng ~25% |
| Actions | Hit / Stand / Double / Split | + Insurance / Surrender (config) |
| Nhà cái / phòng | Không — solo vs shoe | Chủ phòng = nhà cái (BJ) |
| Timer UX | Stakes Time lúc mở dialog; đếm giây action bar; away → chat; không boss bar | Có thể thêm boss bar sau |
| Timeout | Dialog mở → Stand; away → forfeit lose | ROOM: hết ân hạn → Stand (theo ke-hoach) |
| Poker | Showcase glyph `/pk` only | Texas Hold'em đủ luật (PLANNED) |
| Persist | Không — restart mất data | SavedData + SQLite history |

Phần lớn §1 và §10–34 là **ROOM / PLANNED**. Các mục BJ core dưới đây ghi rõ chỗ DEMO lệch.

---

## 1. Nguyên tắc chung

> **ROOM** — chưa áp cho solo `DemoBank`.

### 1.1. Tiền cược

- Mỗi phòng chọn **một item duy nhất** làm vật phẩm cược.
- Không có quy đổi giữa item.
- Ví dụ:
  - Phòng dùng `minecraft:golden_apple` → mọi cược/thắng/thua đều là táo vàng.
  - Phòng dùng diamond → chỉ diamond.
- `balance` được lưu riêng theo từng item ID.
- Mỗi khoản cược được ghi lại nguồn:
  - `balance`
  - `inventory`
- Khi hoàn cược, ưu tiên trả đúng nguồn ban đầu; nếu không thể trả vào inventory thì chuyển phần đó về `balance`.

### 1.2. Escrow

Khi một khoản cược được xác nhận:

1. Kiểm tra số dư `balance`.
2. Nếu thiếu, lấy phần còn thiếu từ inventory.
3. Nếu tổng hai nguồn không đủ → từ chối toàn bộ thao tác.
4. Chuyển toàn bộ cược vào escrow của bàn.
5. Ghi transaction trước khi chuyển sang state tiếp theo.

Không được trừ một phần rồi để trạng thái ván tiếp tục.

### 1.3. Nhà cái

Ở phòng casino:

- Chủ phòng là **nhà cái**.
- Nhà cái không tham gia chia bài như một người chơi.
- Người chơi cược đối đầu với nhà cái.
- Server không thu house fee.
- Nhà cái phải có đủ quỹ để trả mức thắng tối đa trước khi cho phép khóa cược.

Với Blackjack:

- Nhà cái phải đủ khả năng trả blackjack tự nhiên `3:2`.

Với Poker:

- Nhà cái không chịu kết quả Poker.
- Poker sử dụng **pot do người chơi đóng góp**; chủ phòng chỉ là host/bàn.
- Nếu muốn dùng mô hình casino đối đầu nhà cái cho Poker, đó là game rule khác và không dùng Texas Hold'em chuẩn.

---

# 2. Blackjack

## 2.1. Mục tiêu

Người chơi cố đạt tổng điểm gần `21` hơn nhà cái mà không vượt quá `21`.

- `21` tự nhiên từ đúng 2 lá `A + 10/J/Q/K` là Blackjack.
- Nếu người chơi vượt `21` → Bust → thua ngay.
- Nếu nhà cái vượt `21` → các người chơi còn lại thắng.
- Nếu hai bên có cùng điểm → Push.

---

## 2.2. Bộ bài

**ROOM target** mặc định:

- 6 bộ bài.
- Mỗi bộ có 52 lá.
- Tổng cộng `312` lá.
- Không dùng Joker.
- Joker chỉ được dùng khi một game khác hoặc cấu hình riêng bật.

**DEMO:** 1 bộ 52 lá, không Joker (xem §2.3).

Giá trị:

| Lá | Giá trị |
|---|---:|
| A | 1 hoặc 11 |
| 2–10 | Giá trị trên lá |
| J | 10 |
| Q | 10 |
| K | 10 |

### Ace

Server phải chọn tổng điểm hợp lệ cao nhất không vượt quá 21.

Ví dụ:

- `A + 7 = 18`
- `A + 7 + 9 = 17`
- `A + A + 9 = 21`

Nếu mọi cách tính đều vượt 21 thì Ace được tính là `1`.

---

## 2.3. Xào bài

**ROOM / config target:**

- Dùng shoe 6 deck (cấu hình 1–8).
- Khi số lá còn lại xuống dưới ngưỡng cấu hình, server xào lại.
- Không xào giữa một hand đang diễn ra.
- Các lá đã chia trong hand hiện tại vẫn thuộc shoe hiện tại.

```text
Decks: 6
Reshuffle threshold: 25% shoe
```

**DEMO hiện tại:**

```text
Decks: 1 (52 lá)
Reshuffle when remaining < 15
```

---

## 2.4. Bắt đầu ván

### DEMO (solo `/bj`)

1. Trừ `DemoBank.DEFAULT_BET` (10) diamond từ balance in-RAM → `held`.
2. Phase `DEALING`: animate chia từng lá (`TableReveal`, ~0.5s/lá).
3. Player 2 lá ngửa; Dealer 1 ngửa + 1 úp.
4. Nếu natural BJ (player và/hoặc dealer) → lật lỗ, settle ngay (không cửa Insurance).
5. Không thì `PLAYER_TURN`.

Không chọn mức cược, không ghế, không kiểm tra nhà cái.

### ROOM (target)

1. Người chơi ngồi tại ghế.
2. Chọn mức cược.
3. Server kiểm tra người chơi đủ item (balance rồi túi).
4. Server kiểm tra quỹ nhà cái (max BJ 3:2).
5. Khóa cược vào escrow.
6. Chia: Player 2 ngửa; Dealer 1 ngửa + 1 úp.
7. Kiểm tra Blackjack / dealer up-card (và Insurance nếu bật).
8. Bắt đầu lượt người chơi.

Mỗi người chơi xử lý độc lập.

---

# 3. Blackjack — Dealer Rule

## 3.1. Dealer đứng

Mặc định:

```text
Dealer stands on all 17.
```

Tức là:

- Hard 17 → Stand.
- Soft 17 → Stand.

Có config:

```text
dealer_hits_soft_17 = false
```

Nếu bật:

```text
dealer_hits_soft_17 = true
```

thì Dealer sẽ Hit trên Soft 17.

---

## 3.2. Hole card

Dealer có:

```text
[up card] [hole card]
```

Người chơi chỉ thấy:

- lá ngửa của Dealer
- bài của chính mình

Không thấy hole card của Dealer.

Sau khi tất cả người chơi hoàn thành lượt:

1. Dealer lật hole card.
2. Tính điểm.
3. Hit/Stand theo dealer rule.
4. Resolve tất cả cửa.

---

# 4. Blackjack — Natural Blackjack

Blackjack chỉ xảy ra khi:

```text
2 cards
+
A + 10/J/Q/K
```

Blackjack không được tính nếu đạt 21 bằng 3 lá trở lên.

Ví dụ:

```text
A + K       = Blackjack
A + 10      = Blackjack
7 + 7 + 7   = 21, không phải Blackjack
```

## 4.1. Payout

Mặc định:

```text
Blackjack = 3:2
```

Ví dụ cược 10:

```text
Original bet: 10
Profit: 15
Returned total: 25
```

Phần `15` là tiền thắng; `10` là tiền cược gốc.

Nếu cần số nguyên:

```text
payout = floor(bet * 3 / 2)
```

---

## 4.2. Dealer Blackjack

Nếu Dealer có Blackjack:

- Người chơi có Blackjack → Push.
- Người chơi không có Blackjack → Lose.
- Không thực hiện Hit/Stand tiếp.

---

# 5. Blackjack — Player Actions

## 5.1. Hit

Người chơi nhận thêm một lá.

Nếu:

```text
score > 21
```

→ Bust.

Cửa kết thúc ngay.

---

## 5.2. Stand

Người chơi kết thúc lượt.

Không nhận thêm lá.

---

## 5.3. Double Down

Chỉ được:

- sau đúng 2 lá đầu;
- chưa Hit;
- người chơi đủ tiền/item cho thêm một lần cược bằng cược ban đầu.

Khi Double:

1. Khóa thêm `original_bet`.
2. Tổng cược = `2 × original_bet`.
3. Chia đúng **1 lá**.
4. Tự động Stand.

Ví dụ:

```text
Bet = 10
Double = +10
Total bet = 20
```

---

## 5.4. Split

Cho phép khi hai lá đầu có cùng rank/value theo cấu hình.

Mặc định:

```text
8 + 8 → Split
K + Q → Split
```

Không cho split lại:

```text
split_limit = 1
```

Sau Split:

- Hai hand độc lập.
- Mỗi hand có cược bằng cược ban đầu.
- Cần thêm item để khóa hand thứ hai.
- Mỗi hand có lượt riêng.

### Split Ace

Mặc định:

- Aces được split.
- Mỗi Ace chỉ nhận thêm 1 lá.
- Sau đó tự động Stand.
- Không Hit thêm.

Ví dụ:

```text
A A
↓
A 7
A K
```

`A + K` sau split **không được tính là natural Blackjack**, mà là 21 thông thường.

---

# 6. Blackjack — Insurance

> **DEMO solo:** phase `INSURANCE` + nút dialog khi cái ngửa Át và `insuranceEnabled` (mặc định true trong `config/minecard.json`). Bàn phòng: chưa có UI insurance.

Insurance chỉ xuất hiện khi Dealer có:

```text
up card = A
```

Giá:

```text
insurance bet = 1/2 original bet
```

Insurance payout:

```text
2:1
```

Ví dụ:

```text
Original bet = 20
Insurance = 10
Insurance profit = 20
```

Nếu Dealer không có Blackjack:

- Insurance bet thua.
- Main hand tiếp tục.

Nếu Dealer có Blackjack:

- Insurance thắng 2:1.
- Main hand xử lý theo Dealer Blackjack rule.

Mặc định:

```text
insurance = enabled
```

---

# 7. Blackjack — Surrender

> **DEMO solo:** nút khi `surrenderEnabled` (mặc định **false** trong config). Late surrender, hoàn nửa cửa.

Mặc định:

```text
surrender = disabled
```

Nếu bật:

```text
late surrender
```

Người chơi được surrender sau khi Dealer kiểm tra Blackjack và trước khi Hit.

Payout:

```text
Player receives 50% of bet.
```

Ví dụ:

```text
Bet = 20
Surrender return = 10
```

Phần còn lại của cược được chuyển cho nhà cái.

---

# 8. Blackjack — Resolution

Sau khi tất cả hand kết thúc:

| Player | Dealer | Kết quả |
|---|---|---|
| Bust | bất kỳ | Lose |
| ≤21 | Bust | Win |
| > Dealer | ≤21 | Win |
| < Dealer | ≤21 | Lose |
| = Dealer | ≤21 | Push |
| Blackjack | không Blackjack | 3:2 |
| Blackjack | Blackjack | Push |
| thường 21 | Blackjack | Lose |

---

# 9. Blackjack — Timer

Mặc định:

```text
Turn timer = 25 seconds
```

### DEMO (đã ship)

Hiển thị:

- Dialog stakes: `Time: Ns` **lúc mở / refresh dialog** (không `openDialog` mỗi giây — tránh reset scroll).
- Action bar: đếm giây còn lại mỗi ~1s khi dialog đang mở (`minecard.bj.timer.actionbar`).
- Away (Leave/ESC giữa ván): countdown trong **chat**; `/bj` mở lại bàn.
- Không dùng boss bar.

Hết giờ:

| Trạng thái | Hành vi DEMO |
|------------|----------------|
| Dialog đang mở | `Stand` |
| Dialog away | Forfeit lose (mất cược đang giữ) |

Server quyết định timeout; client không tự resolve.

### ROOM (target, ke-hoach)

- Có thể thêm boss bar đếm ngược.
- Hết ân hạn disconnect → Stand, ván chơi nốt (không forfeit ngay như DEMO away).

---

# 10. Poker — Texas Hold'em

> **PLANNED** — hiện chỉ showcase glyph (`/pk`, `/minecard cards poker`). Không phải ván chơi, không pot, không blind.

Poker sử dụng Texas Hold'em chuẩn.

## 10.1. Số người chơi

Mặc định:

```text
Minimum: 2
Maximum: 4
```

Kiến trúc hiện tại cho phép tối đa 4 người chơi Poker trong một bàn.

Dealer button luân chuyển giữa các người chơi.

---

# 11. Poker — Blind

Mỗi bàn cấu hình:

```text
Small Blind
Big Blind
```

Blind sử dụng **đúng item của phòng**.

Ví dụ phòng dùng táo vàng:

```text
Small Blind = 1 golden apple
Big Blind   = 2 golden apples
```

Không dùng coin/chip ảo.

---

## 11.1. Dealer Button

Sau mỗi hand:

```text
Dealer Button → người chơi tiếp theo
```

Vị trí blind:

```text
Heads-up:
Dealer = Small Blind
Opponent = Big Blind
```

Từ 3 người trở lên:

```text
Dealer
↓
Small Blind
↓
Big Blind
```

---

# 12. Poker — Buy-in

Người chơi phải có đủ số item để tham gia.

Mỗi bàn có:

```text
min_buy_in
max_buy_in
```

Ví dụ:

```text
Small Blind = 1
Big Blind = 2

Min buy-in = 40
Max buy-in = 200
```

Khi ngồi vào bàn:

- Buy-in được chuyển vào poker stack/escrow.
- Item không còn nằm trong inventory.
- Stack được biểu diễn bằng số lượng của **đúng item phòng**.

Không có chip conversion.

---

# 13. Poker — Hole Cards

Mỗi người chơi nhận:

```text
2 hole cards
```

Chỉ người đó nhìn thấy 2 lá của mình.

Server tuyệt đối không gửi hole card của người chơi A cho dialog của B.

---

# 14. Poker — Community Cards

Texas Hold'em dùng:

```text
Flop  = 3 cards
Turn  = 1 card
River = 1 card
```

Tổng cộng:

```text
5 community cards
```

Bài cuối cùng của người chơi được tạo từ:

```text
2 hole cards
+
5 community cards
```

Chọn **5 lá mạnh nhất** trong 7 lá.

---

# 15. Poker — Betting Streets

Một hand có tối đa 4 betting rounds:

```text
Pre-Flop
Flop
Turn
River
```

## Pre-Flop

1. Dealer button xác định vị trí.
2. Small Blind đặt cược.
3. Big Blind đặt cược.
4. Mỗi người hành động theo thứ tự.

## Flop

Dealer mở 3 community cards.

Betting round mới.

## Turn

Mở lá community thứ 4.

Betting round mới.

## River

Mở lá community thứ 5.

Betting round cuối.

---

# 16. Poker — Actions

## Check

Chỉ được phép khi:

```text
current_bet == player_contribution
```

Không thêm cược.

---

## Bet

Dùng khi betting round chưa có cược chủ động.

Ví dụ:

```text
Current bet = 0
Player Bet = 5
```

Contribution của player tăng thêm 5.

---

## Call

Khớp mức cược hiện tại.

Ví dụ:

```text
Current bet = 10
Player đã bỏ = 4

Call = 6
```

---

## Raise

Tăng mức cược hiện tại.

Ví dụ:

```text
Current bet = 10
Player raise to = 20
```

Các người chơi còn lại phải:

- Fold
- Call 20
- Raise tiếp

---

## Fold

Người chơi bỏ hand.

- Không được lấy lại contribution.
- Tiền đã đóng vẫn nằm trong pot.
- Không được hành động tiếp trong hand.

---

# 17. Poker — Minimum Raise

Mặc định dùng No-Limit Texas Hold'em.

Minimum raise phải ít nhất bằng kích thước raise trước đó.

Ví dụ:

```text
Current bet = 10
Previous raise = 5

Minimum raise-to = 15
```

Nếu người chơi raise:

```text
10 → 20
```

thì raise size = 10.

Lượt sau minimum raise phải ít nhất:

```text
20 + 10 = 30
```

All-in nhỏ hơn minimum raise:

- vẫn hợp lệ;
- không reset minimum raise nếu mức all-in không đủ thành một full raise.

---

# 18. Poker — All-in

Người chơi có thể All-in nếu không đủ item để call/raise đầy đủ.

Ví dụ:

```text
Player stack = 7
Current bet = 10
```

Player có thể:

```text
All-in 7
```

Không được ép player bỏ hand chỉ vì stack nhỏ hơn current bet.

---

# 19. Poker — Side Pot

Side pot bắt buộc vì người chơi có thể All-in với stack khác nhau.

Ví dụ:

```text
Player A = 100
Player B = 60
Player C = 30
```

Nếu cả ba All-in:

### Main Pot

Mỗi người đóng tối đa 30:

```text
30 × 3 = 90
```

### Side Pot 1

A và B còn thêm 30:

```text
30 × 2 = 60
```

A còn 40 không có đối thủ đủ stack → phần không thể match được xử lý theo contribution/all-in accounting.

Server phải tạo pot theo từng mức contribution thay vì chỉ có một số `pot`.

---

# 20. Poker — Pot Construction

Sau mỗi betting action, server có thể tính:

```text
totalContribution[player]
```

Khi showdown:

1. Lấy tất cả mức contribution khác nhau.
2. Tạo pot từ từng interval.
3. Xác định eligible players.
4. Chỉ người còn trong hand mới eligible thắng pot.
5. Người Fold không thể thắng pot dù đã đóng tiền.

Mỗi pot phải lưu:

```text
amount
eligiblePlayers[]
```

---

# 21. Poker — Betting Round End

Betting round kết thúc khi:

- Tất cả người còn trong hand đã:
  - Call mức cược hiện tại, hoặc
  - Check khi không có cược, hoặc
  - All-in.
- Hoặc chỉ còn một người chưa Fold.

Nếu chỉ còn một người:

```text
→ award pot
→ không cần showdown
```

---

# 22. Poker — Showdown

Nếu sau River còn từ 2 người trở lên:

1. Tất cả hole cards được server reveal.
2. Tính hand ranking.
3. So sánh từ mạnh đến yếu.
4. Chia từng pot cho eligible winner.

Không reveal bài của người đã Fold.

---

# 23. Poker — Hand Ranking

Thứ tự chuẩn từ mạnh đến yếu:

### 1. Royal Flush

```text
A K Q J 10
```

Cùng một suit.

---

### 2. Straight Flush

5 lá liên tiếp cùng suit.

Ví dụ:

```text
9 8 7 6 5
```

---

### 3. Four of a Kind

4 lá cùng rank.

```text
K K K K + kicker
```

---

### 4. Full House

Three of a kind + Pair.

```text
Q Q Q + 8 8
```

---

### 5. Flush

5 lá cùng suit nhưng không liên tiếp.

---

### 6. Straight

5 lá liên tiếp khác suit.

Ace có thể thấp:

```text
A 2 3 4 5
```

Đây là straight thấp nhất.

---

### 7. Three of a Kind

3 lá cùng rank.

---

### 8. Two Pair

2 cặp khác nhau.

---

### 9. One Pair

Một cặp.

---

### 10. High Card

Không có combination nào ở trên.

---

# 24. Poker — Tie Breaker

Nếu cùng loại hand:

1. So sánh rank chính.
2. So sánh rank phụ.
3. So sánh kicker theo thứ tự giảm dần.

Ví dụ:

```text
A A K 8 3
A A Q 8 3
```

→ hand đầu thắng vì `K > Q`.

Nếu toàn bộ 5 lá bằng nhau:

```text
Tie
```

Pot được chia đều.

---

# 25. Poker — Split Pot

Nếu có nhiều winner bằng hand:

```text
pot / number_of_winners
```

Nếu số lượng item không chia hết:

- Phần dư phải được xử lý deterministic.
- Không tạo hoặc mất item.
- Rule đề xuất: chia phần nguyên trước, phần dư phân phối theo thứ tự seat từ Dealer button.

Ví dụ:

```text
10 items
3 winners

→ 3 / 3 / 4
```

---

# 26. Poker — Uncalled Bet

Nếu người chơi bet/raise một lượng mà không có đối thủ đủ contribution để match:

```text
uncalled excess
```

không được đưa vào pot.

Phần không được call phải trả lại cho chính người chơi.

Ví dụ:

```text
A all-in = 100
B all-in = 60
C all-in = 60
```

Không thể biến toàn bộ 100 của A thành contribution đối đầu.

Phần excess tương ứng phải được trả lại theo pot accounting.

---

# 27. Poker — Winner Payout

Sau showdown:

1. Xác định main pot.
2. Xác định side pots.
3. Xác định winner của từng pot.
4. Chia pot.
5. Cộng winnings vào poker stack.
6. Kết thúc hand.
7. Dealer button chuyển vị trí.
8. Người chơi tiếp tục hand mới nếu còn stack.

Tiền thắng không trả thẳng vào inventory.

---

# 28. Poker — Leave Table

### Trước hand

Người chơi có thể Leave.

Stack được trả:

```text
stack → balance
```

Không cần nhét trực tiếp vào inventory.

### Trong hand

Không được Leave để hủy cược.

Nếu disconnect:

- Giữ seat.
- Giữ stack/escrow.
- Áp dụng disconnect grace period.
- Người chơi reconnect → tiếp tục hand.

Nếu grace period hết:

- Người chơi bị xử lý như mất kết nối.
- Không tự ý xóa stack.
- Phần còn lại được đưa vào balance theo rule recovery.

---

# 29. Poker — Disconnect

Mặc định sử dụng cùng hệ thống với Blackjack:

```text
disconnect_grace_period
```

Trong thời gian grace:

```text
seat = occupied
stack = locked
hand = active
```

Reconnect:

```text
/minecard
→ mở lại game dialog
→ khôi phục state
```

Hết grace:

- Không được tiếp tục hành động.
- Hand xử lý theo disconnect policy.
- Stack còn lại được bảo toàn và hoàn vào balance.

---

# 30. Poker — Timeout

Mỗi action có timer.

Mặc định:

```text
turn_time = 25 seconds
```

Hết giờ:

### Nếu có thể Check

```text
Timeout → Check
```

### Nếu đang đối mặt với Bet

```text
Timeout → Fold
```

Lý do:

- Không cho timeout tự động Call và làm mất item.
- Check là action không gây thêm chi phí.
- Khi phải trả tiền, timeout được xem như Fold.

---

# 31. Poker — Dialog UI

Trong dialog của từng người:

```text
┌─────────────────────────────┐
│          MINECARD            │
├─────────────────────────────┤
│ Pot: 24 Golden Apples       │
│ Your Stack: 80              │
│ Bet: 8                      │
│                             │
│ [Card] [Card]               │
│                             │
│ [Flop Card] [Card] [Card]  │
│                             │
│ [Check] [Call 8]            │
│ [Bet/Raise] [Fold]          │
│ [All-in]                    │
│                             │
│ Time: 17s                   │
│                             │
│ [Rời bàn]                   │
└─────────────────────────────┘
```

Các action không hợp lệ phải:

- bị disable, hoặc
- không xuất hiện.

Server vẫn phải validate lại action khi nhận `custom_click`.

---

# 32. Poker — Card Visibility

### Người chơi

Thấy:

- 2 hole cards của mình.
- Toàn bộ community cards.
- Pot.
- Stack của mình.
- Các thông tin public của bàn.

### Không thấy

- Hole cards của người khác trước showdown.
- Lá chưa được reveal.
- Private state của player khác.

Server không gửi dữ liệu private rồi chỉ che bằng UI.

---

# 33. Poker — Card Asset

Dùng cùng hệ thống 52 lá của Minecard:

```text
4 suits
×
13 ranks
=
52 cards
```

Mỗi card được render bằng glyph trong server resource pack.

Poker và Blackjack dùng cùng card asset.

---

# 34. Poker — Deck Rule

Poker dùng một bộ:

```text
52 cards
```

Không dùng Joker.

Khi bắt đầu hand:

- Lấy card từ shoe/deck.
- Không card nào được chia hai lần trong cùng hand.
- Burn card có thể được dùng để mô phỏng Texas Hold'em chuẩn.

Đề xuất:

```text
Pre-flop: burn 1 → flop 3
Turn: burn 1 → turn 1
River: burn 1 → river 1
```

Burn cards không được hiển thị cho người chơi.

---

# 35. Game State Machine

## Blackjack — DEMO (đã ship)

Phases trong `BlackjackSession`:

```text
DEALING          (TableReveal deal pulses)
  ↓
PLAYER_TURN      (hit / stand / double / split)
  ↓
DEALER_TURN      (hole flip → hits animated → settle pause)
  ↓
RESOLVED
  ↓
COLLECTING       (play again: thu bài rồi DEALING)
```

Natural BJ sau deal có thể nhảy thẳng `RESOLVED` (bỏ `PLAYER_TURN` / `DEALER_TURN`).

Split: tối đa 2 hand trong cùng session; `activeHand` chuyển sau khi hand hiện tại xong.

## Blackjack — ROOM (target)

```text
WAITING
  ↓
BETTING
  ↓
DEAL
  ↓
INSURANCE_CHECK   (PLANNED nếu insurance bật)
  ↓
PLAYER_TURN
  ↓
DEALER_TURN
  ↓
RESOLVE
  ↓
PAYOUT
  ↓
WAITING
```

---

## Poker

> **PLANNED**

```text
WAITING
  ↓
BUY_IN
  ↓
POST_BLINDS
  ↓
DEAL_HOLE
  ↓
PRE_FLOP
  ↓
FLOP
  ↓
TURN
  ↓
RIVER
  ↓
SHOWDOWN
  ↓
POT_RESOLUTION
  ↓
PAYOUT
  ↓
NEXT_HAND
```

Nếu chỉ còn một người:

```text
any betting state
→ AWARD_POT
→ NEXT_HAND
```

---

# 36. Server Authority

Client chỉ gửi:

```text
action id
+
NBT payload
```

### DEMO — id thật (Identifier namespace `minecard`)

```text
minecard:bj/hit
minecard:bj/stand
minecard:bj/double
minecard:bj/split
minecard:bj/again
minecard:bj/leave
minecard:bj/wait
```

### ROOM / PLANNED (thêm sau)

```text
minecard:bj/insurance
minecard:bj/surrender

minecard:pk/check
minecard:pk/call
minecard:pk/bet
minecard:pk/raise
minecard:pk/fold
minecard:pk/all_in
```

Server phải kiểm tra:

- player có đúng session không;
- đúng turn không;
- action có hợp lệ với state hiện tại không;
- timer còn không;
- player đủ item/stack không;
- payload có hợp lệ không;
- action có bị gửi lại không.

Không tin state từ client.

---

# 37. Atomic Transaction

Mọi thay đổi item phải atomic.

Ví dụ Poker raise:

```text
Validate action
↓
Validate stack
↓
Calculate contribution
↓
Write escrow/transaction state
↓
Subtract stack
↓
Update pot
↓
Advance turn
↓
Save state
↓
Render dialog
```

Nếu bất kỳ bước nào thất bại:

```text
Rollback
```

Không được có trạng thái:

```text
item đã mất
nhưng stack chưa tăng
```

hoặc:

```text
pot đã tăng
nhưng inventory chưa bị trừ
```

---

# 38. Crash / Server Shutdown

Mỗi thay đổi quan trọng phải được lưu vào `SavedData`.

Bao gồm:

- game state;
- phase;
- deck state;
- cards;
- turn;
- timer timestamp;
- player seats;
- contributions;
- pots;
- balances;
- escrow;
- transaction log.

Server restart:

### Snapshot đầy đủ

```text
restore session
→ player reconnect
→ continue
```

### Snapshot thiếu

```text
stop game
→ refund escrow to balance
→ close affected session
```

Không refund bằng cách spawn item trên mặt đất trong quá trình shutdown.

---

# 39. Admin Recovery

Node:

```text
minecard.admin
```

Các thao tác dự kiến:

```text
/minecard admin end
/minecard admin refund
```

Dùng khi:

- bàn bị kẹt;
- session không thể tiếp tục;
- cần hoàn escrow;
- cần đóng phòng.

Admin action phải ghi transaction log.

---

# 40. Anti-Duplicate / Idempotency

Mỗi action có:

```text
sessionId
turnId
actionId
```

Nếu cùng action được gửi hai lần:

```text
first → execute
second → ignore
```

Không được:

```text
double click
→ double bet
→ double payout
```

---

# 41. Transaction Log

Chi tiết schema ledger / events / soi lịch sử: [data-and-history.md](data-and-history.md).

Mỗi dòng tiền (SQLite `ledger`) tối thiểu:

```text
timestamp
player UUID
session / hand id
itemId
delta
balance_after
source   (BALANCE | INVENTORY | ESCROW | PAYOUT | ADMIN)
reason
idempotency_key
```

Ví dụ khái niệm:

```text
player=Steve
game=BLACKJACK
action=BET
item=minecraft:golden_apple
amount=10
source=inventory
result=locked
```

**DEMO:** chưa ghi đĩa — chỉ đổi `DemoBank` in-RAM.

---

# 42. Config đề xuất

### DEMO — giá trị hardcode hiện tại (chưa có `config/minecard.json` runtime)

```text
stake item: minecraft:diamond
starting balance: 1000
default bet: 10
decks: 1
reshuffle: remaining < 15
dealerHitsSoft17: false (stand all 17)
insurance / surrender: off (không có UI)
max split hands: 2
split aces: one card then stand
turnSeconds: 25
TableReveal STEP_TICKS: 10 (~0.5s)
```

### ROOM / global target (`config/minecard.json`)

```json
{
  "historyRetentionDays": 90,
  "blackjack": {
    "decks": 6,
    "dealerHitsSoft17": false,
    "blackjackPayoutNumerator": 3,
    "blackjackPayoutDenominator": 2,
    "insuranceEnabled": true,
    "surrenderEnabled": false,
    "splitEnabled": true,
    "maxSplitHands": 2,
    "splitAcesOneCard": true,
    "turnSeconds": 25
  },
  "poker": {
    "maxPlayers": 4,
    "turnSeconds": 25,
    "smallBlind": 1,
    "bigBlind": 2,
    "noLimit": true,
    "burnCards": true
  }
}
```

`smallBlind`, `bigBlind`, buy-in và giới hạn phòng thực tế nên thuộc **room configuration**, không phải global config.

---

# 43. Điểm cần giữ đúng với Minecard

### Đang dùng (DEMO)

- `BlackjackSession` / `BlackjackGames` / `BlackjackDialog`
- `TableReveal`, `CardGrid` / `CardLayer`, card glyph resource pack
- `DemoBank` + `BlackjackRoundState` (snapshot sẵn cho SavedData)
- Server Translations (`vi_vn`, `en_us`)
- Dialog vanilla `custom` click (`minecard:bj/…`)

### Target tái sử dụng (ROOM+)

- `GameSession` / Room / Escrow
- Balance SavedData + inventory lock
- Timer + disconnect grace
- SQLite ledger / history ([data-and-history.md](data-and-history.md))
- LuckPerms permission layer (`minecard.play` / `minecard.admin`)

Không tạo một hệ thống ví hoặc tiền mới cho từng game.

---

# 44. Phạm vi triển khai

Align [implement.md](implement.md):

| Mốc | Nội dung | Trạng thái |
|-----|----------|------------|
| **1** | 52 quân GUI, glyph, showcase | Xong |
| **2** | Ví SavedData + escrow; thay `DemoBank`; bắt đầu `ledger` / `player_stats` | Chưa |
| **3** | Phòng (tạo, mời, sảnh) | Chưa |
| **4** | Blackjack đủ trong phòng (timer, payout, multi-seat); insurance/surrender theo config | Demo solo đã có hit/stand/double/split + animate |
| **5** | LuckPerms, hồ sơ / history dialog, admin | Chưa |
| **6** | Poker + Liar’s Bar | PLANNED |

Insurance / Surrender / 6-deck / soft-17 config **không** tính là xong chỉ vì demo solo đã chơi được.

---

# 45. Acceptance Criteria

## Blackjack

### DEMO (solo) — đã đạt

- [x] Ace tính đúng 1/11.
- [x] Natural Blackjack trả 3:2.
- [x] Dealer Blackjack xử lý đúng (settle sau deal).
- [x] Push trả lại cược (vào `DemoBank` balance).
- [x] Hit/Stand hoạt động.
- [x] Double chỉ sau 2 lá (đủ balance).
- [x] Split hoạt động (một lần).
- [x] Split Ace chỉ nhận 1 lá.
- [x] Timeout: Stand khi dialog mở; forfeit khi away.
- [x] Không lộ hole card trong `PLAYER_TURN`.
- [x] Dealer stand all 17; hits animate từng lá.

### ROOM / PLANNED — chưa

- [ ] 6-deck shoe hoạt động.
- [ ] Insurance hoạt động.
- [ ] Surrender có thể bật/tắt.
- [ ] Dealer Soft 17 configurable.
- [ ] Payout / escrow không tạo/mất item (SavedData + inventory).
- [ ] Persist qua restart ([data-and-history.md](data-and-history.md)).

## Poker

- [ ] 2–4 người chơi.
- [ ] Dealer button xoay đúng.
- [ ] Small/Big Blind đúng.
- [ ] Buy-in đúng item.
- [ ] Hole card private.
- [ ] Flop/Turn/River đúng.
- [ ] Check/Bet/Call/Raise/Fold.
- [ ] All-in.
- [ ] Side pot.
- [ ] Uncalled bet trả lại.
- [ ] Hand ranking chuẩn.
- [ ] Ace-low straight hoạt động.
- [ ] Tie/split pot hoạt động.
- [ ] Không reveal bài người đã Fold.
- [ ] Timeout không tự động Call.
- [ ] Disconnect recovery.
- [ ] Crash recovery.
- [ ] Double-click không double charge.
- [ ] Không mất item khi server restart.
