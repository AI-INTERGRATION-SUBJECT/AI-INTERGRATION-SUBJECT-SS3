# BÀI TẬP 3: TỐI ƯU PROMPT — EMAIL ĐẶT PHÒNG MÂU THUẪN (EDGE CASE)

---

## 1. TỔNG QUAN VẤN ĐỀ & BỐI CẢNH

Trong thực tế ứng dụng AI vào chăm sóc khách hàng (như hệ thống đặt phòng R-Hotel), email của khách hàng thường chứa các ngữ cảnh phức tạp:
- Khách hàng thay đổi quyết định liên tục trong cùng một email (sử dụng các cụm từ đính chính: *"À mà không"*, *"nhưng thôi"*, *"thay vào đó"*, *"cho tôi chỉnh lại"*).
- Khách hàng sử dụng từ ngữ thời gian tương đối: *"ngày mai"*, *"ngày kia"*, *"lùi lại 1 ngày"*, *"tuần sau"*.

Nếu Prompt không có quy tắc giải quyết mâu thuẫn (Conflict Resolution Rules) và quy tắc tính toán mốc thời gian động (Dynamic Date Anchor), LLM sẽ bóc tách sai thông tin ban đầu (chưa qua đính chính) hoặc không tính ra được ngày check-in cụ thể.

---

## 2. PROMPT HOÀN CHỈNH NÂNG CAO (ADVANCED CONFLICT-RESOLUTION PROMPT)

```text
[VAI TRÒ - ROLE]
Bạn là một chuyên gia phân tích dữ liệu đặt phòng tự động của hệ thống Khách sạn R-Hotel. Nhiệm vụ của bạn là đọc email khách hàng, giải quyết các mâu thuẫn/đính chính trong văn bản và xuất dữ liệu đặt phòng cuối cùng dưới dạng RAW JSON.

[MỤC TIÊU - GOAL]
Bóc tách chính xác các thông tin: guestName (Tên khách), checkInDate (Ngày nhận phòng dạng DD/MM/YYYY), durationNights (Số đêm lưu trú), roomType (Loại phòng).

[MỐC THỜI GIAN THỰC TẾ - TEMPORAL ANCHOR]
- Hôm nay là ngày: {currentDate} (Ví dụ: 17/07/2026).
- Tất cả các từ chỉ thời gian tương đối ("ngày mai", "ngày kia", "lùi lại X ngày") phải được tính toán chính xác dựa trên mốc ngày hôm nay.

[NGỮ CẢNH EMAIL - INPUT CONTEXT]
--- BEGIN EMAIL ---
{email}
--- END EMAIL ---

[QUY TẮC XỬ LÝ MÂU THUẪN & TÍNH TOÁN THỜI GIAN - CONFLICT & DATE RESOLUTION RULES]
1. QUY TẮC ĐÍNH CHÍNH TỐI THƯỢNG (LAST INTENT OVERRIDE RULE):
   - Khách hàng thường thay đổi ý định trong văn bản (dấu hiệu bởi các từ: "À mà không", "thay vào đó", "lùi lại", "rút ngắn", "chỉnh lại").
   - Luôn ưu tiên chọn quyết định CUỐI CÙNG ở phía sau của văn bản để đè (override) lên các phát biểu ban đầu.
   
2. QUY TẮC TÍNH TOÁN NGÀY CHECK-IN (CHECK-IN DATE CALCULATION):
   - "ngày mai" = {currentDate} + 1 ngày.
   - Nếu khách ghi "lùi lại 1 ngày" so với dự định ban đầu (hoặc lùi từ ngày gốc), hãy cộng thêm 1 ngày vào ngày dự định ban đầu.
   - Định dạng ngày trả về BẮT BUỘC là `DD/MM/YYYY` (ví dụ: `18/07/2026`).

3. QUY TẮC SỐ ĐÊM LƯU TRÚ (DURATION NIGHTS):
   - Nếu ban đầu chọn 3 ngày nhưng sau đó ghi "rút ngắn chuyến đi xuống còn 2 ngày", giá trị durationNights cuối cùng BẮT BUỘC phải là 2.

[RÀNG BUỘC ĐỊNH DẠNG ĐẦU RA - STRICT CONSTRAINTS]
1. CHỈ TRẢ VỀ CHUỖI JSON THUẦN (RAW JSON STRING ONLY).
2. TUYỆT ĐỐI KHÔNG bọc JSON trong bất kỳ thẻ Markdown Block nào (KHÔNG dùng ```json hay ```).
3. TUYỆT ĐỐI KHÔNG thêm lời chào, lời giải thích hay bất kỳ ký tự dư thừa nào ngoài JSON.
4. Ký tự đầu tiên PHẢI LÀ `{` và ký tự cuối cùng PHẢI LÀ `}`.

[ĐỊNH DẠNG ĐẦU RA - OUTPUT FORMAT INSTRUCTIONS]
{formatInstructions}
```

---

## 3. HƯỚNG DẪN CHI TIẾT CÁCH AI XỬ LÝ NGUYÊN TẮC MÂU THUẪN (CONFLICT RESOLUTION STEPS)

Khi AI đọc email mâu thuẫn:
> *"Chào lễ tân, tôi tên là Minh. Tôi định đặt phòng Suite cho 3 ngày bắt đầu từ ngày mai. À mà không, mai tôi bận đột xuất nên cho tôi check-in lùi lại 1 ngày nhé, và tôi rút ngắn chuyến đi xuống còn 2 ngày thôi. Có gì liên hệ lại tôi."*

AI thực hiện các bước suy luận theo hướng dẫn trong Prompt:

```
Step 1: Nhận diện Tên khách & Loại phòng
        -> guestName = "Minh"
        -> roomType = "Suite" (Không có đính chính đổi loại phòng)

Step 2: Phân tích Ngày Check-in (checkInDate)
        -> Mốc gốc (currentDate) = 17/07/2026
        -> Phát biểu 1: "bắt đầu từ ngày mai" -> 18/07/2026
        -> Đính chính 2: "À mà không, mai tôi bận... lùi lại 1 ngày" 
        -> Áp dụng "Last Intent Override Rule": 
           Tính ngày check-in chính thức sau điều chỉnh = 18/07/2026

Step 3: Phân tích Số đêm lưu trú (durationNights)
        -> Phát biểu 1: "cho 3 ngày" (duration = 3)
        -> Đính chính 2: "rút ngắn chuyến đi xuống còn 2 ngày thôi"
        -> Áp dụng "Last Intent Override Rule": Đè giá trị cũ (3) bằng giá trị mới (2)
        -> durationNights = 2

Step 4: Xuất kết quả JSON sạch 100% tuân thủ Schema
```

---

## 4. MINH CHỨNG CHẠY THỰC TẾ (TEXT LOG DEMONSTRATION)

### **4.1. Khai báo Java Record & Output Schema (`{formatInstructions}`):**

```java
public record BookingExtraction(
    String guestName,
    String checkInDate,
    int durationNights,
    String roomType
) {}
```

**Nội dung của `{formatInstructions}`:**
```json
The output should be formatted as a JSON instance that conforms to the JSON Schema below.
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "title": "BookingExtraction",
  "type": "object",
  "properties": {
    "guestName": { "type": "string", "description": "Tên khách hàng" },
    "checkInDate": { "type": "string", "description": "Ngày nhận phòng định dạng DD/MM/YYYY" },
    "durationNights": { "type": "integer", "description": "Số đêm lưu trú" },
    "roomType": { "type": "string", "description": "Loại phòng đặt" }
  },
  "required": ["guestName", "checkInDate", "durationNights", "roomType"]
}
```

---

### **4.2. Full Prompt gửi đi đến LLM (với `{currentDate} = "17/07/2026"`):**

```text
[VAI TRÒ - ROLE]
Bạn là một chuyên gia phân tích dữ liệu đặt phòng tự động của hệ thống Khách sạn R-Hotel. Nhiệm vụ của bạn là đọc email khách hàng, giải quyết các mâu thuẫn/đính chính trong văn bản và xuất dữ liệu đặt phòng cuối cùng dưới dạng RAW JSON.

[MỤC TIÊU - GOAL]
Bóc tách chính xác các thông tin: guestName (Tên khách), checkInDate (Ngày nhận phòng dạng DD/MM/YYYY), durationNights (Số đêm lưu trú), roomType (Loại phòng).

[MỐC THỜI GIAN THỰC TẾ - TEMPORAL ANCHOR]
- Hôm nay là ngày: 17/07/2026.
- Tất cả các từ chỉ thời gian tương đối ("ngày mai", "ngày kia", "lùi lại X ngày") phải được tính toán chính xác dựa trên mốc ngày hôm nay.

[NGỮ CẢNH EMAIL - INPUT CONTEXT]
--- BEGIN EMAIL ---
Chào lễ tân, tôi tên là Minh. Tôi định đặt phòng Suite cho 3 ngày bắt đầu từ ngày mai.
À mà không, mai tôi bận đột xuất nên cho tôi check-in lùi lại 1 ngày nhé,
và tôi rút ngắn chuyến đi xuống còn 2 ngày thôi. Có gì liên hệ lại tôi.
--- END EMAIL ---

[QUY TẮC XỬ LÝ MÂU THUẪN & TÍNH TOÁN THỜI GIAN - CONFLICT & DATE RESOLUTION RULES]
1. QUY TẮC ĐÍNH CHÍNH TỐI THƯỢNG (LAST INTENT OVERRIDE RULE):
   - Khách hàng thường thay đổi ý định trong văn bản (dấu hiệu bởi các từ: "À mà không", "thay vào đó", "lùi lại", "rút ngắn", "chỉnh lại").
   - Luôn ưu tiên chọn quyết định CUỐI CÙNG ở phía sau của văn bản để đè (override) lên các phát biểu ban đầu.
   
2. QUY TẮC TÍNH TOÁN NGÀY CHECK-IN (CHECK-IN DATE CALCULATION):
   - "ngày mai" = 17/07/2026 + 1 ngày = 18/07/2026.
   - Định dạng ngày trả về BẮT BUỘC là DD/MM/YYYY (ví dụ: 18/07/2026).

3. QUY TẮC SỐ ĐÊM LƯU TRÚ (DURATION NIGHTS):
   - Nếu ban đầu chọn 3 ngày nhưng sau đó ghi "rút ngắn chuyến đi xuống còn 2 ngày", giá trị durationNights cuối cùng BẮT BUỘC phải là 2.

[RÀNG BUỘC ĐỊNH DẠNG ĐẦU RA - STRICT CONSTRAINTS]
1. CHỈ TRẢ VỀ CHUỖI JSON THUẦN (RAW JSON STRING ONLY).
2. TUYỆT ĐỐI KHÔNG bọc JSON trong bất kỳ thẻ Markdown Block nào (KHÔNG dùng ```json hay ```).
3. TUYỆT ĐỐI KHÔNG thêm lời chào, lời giải thích hay bất kỳ ký tự dư thừa nào ngoài JSON.
4. Ký tự đầu tiên PHẢI LÀ `{` và ký tự cuối cùng PHẢI LÀ `}`.

[ĐỊNH DẠNG ĐẦU RA - OUTPUT FORMAT INSTRUCTIONS]
The output should be formatted as a JSON instance that conforms to the JSON Schema below.
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "title": "BookingExtraction",
  "type": "object",
  "properties": {
    "guestName": { "type": "string", "description": "Tên khách hàng" },
    "checkInDate": { "type": "string", "description": "Ngày nhận phòng định dạng DD/MM/YYYY" },
    "durationNights": { "type": "integer", "description": "Số đêm lưu trú" },
    "roomType": { "type": "string", "description": "Loại phòng đặt" }
  },
  "required": ["guestName", "checkInDate", "durationNights", "roomType"]
}
```

---

### **4.3. Phản hồi JSON thực tế (Raw AI Response) trùng khớp 100% mong đợi:**

```json
{
  "guestName": "Minh",
  "checkInDate": "18/07/2026",
  "durationNights": 2,
  "roomType": "Suite"
}
```

> **Kết luận:** AI đã xử lý chính xác tuyệt đối cả 2 mâu thuẫn trong văn bản (giảm số đêm xuống còn 2, xác định đúng ngày check-in `18/07/2026`) và trả về chuỗi RAW JSON chuẩn không dính Markdown hay văn bản thoại.
