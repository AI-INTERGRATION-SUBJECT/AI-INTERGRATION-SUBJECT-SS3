# BÀI TẬP 1: TỐI ƯU PROMPT — NGĂN LỖI ĐỊNH DẠNG BEANOUTPUTCONVERTER

---

## 1. VẤN ĐỀ KỸ THUẬT & BỐI CẢNH

Trong Spring AI, `BeanOutputConverter` tạo ra đoạn hướng dẫn `{formatInstructions}` dựa trên Java DTO/Record để yêu cầu LLM trả về dữ liệu chuẩn JSON Schema.

uy nhiên, các LLM nhỏ (đặc biệt là mô hình local như **Qwen2.5 7B** hoặc **Llama 3 8B**) thường mắc các lỗi định dạng:
1. Tự động bọc chuỗi JSON trong thẻ Markdown code blocks: ` ```json ... ``` `.
2. Thêm các câu thoại giao tiếp như: *"Dưới đây là kết quả bóc tách JSON bạn yêu cầu:"* hoặc *"Hy vọng thông tin này giúp ích cho bạn!"*.

Khi phương thức `.convert(response)` của Spring AI truyền chuỗi này vào Jackson `ObjectMapper`, Jackson sẽ gặp lỗi `JsonParseException` hoặc `UnrecognizedPropertyException` do không parse được các ký tự thừa (Markdown ticks hoặc text ngoài JSON), dẫn đến crash ứng dụng Java.

---

## 2. PROMPT NÂNG CAO HOÀN CHỈNH (ADVANCED STRUCTURED PROMPT)

Đoạn Prompt đã được thiết kế lại theo chuẩn kỹ thuật Prompt Engineering chuyên sâu (Role - Goal - Context - Constraints - Output Format):

```text
[VAI TRÒ - ROLE]
Bạn là một hệ thống trích xuất dữ liệu tự động có độ chính xác tuyệt đối, làm nhiệm vụ phân tích văn bản và trả về kết quả định dạng RAW JSON cho trình giải tuần tự hóa (Jackson Serializer) của ứng dụng Java Backend.

[MỤC TIÊU - GOAL]
Bóc tách thông tin tên khách hàng và số điện thoại từ nội dung email được cung cấp.

[NGỮ CẢNH - CONTEXT]
Nội dung email cần bóc tách dữ liệu:
--- BEGIN EMAIL ---
{email}
--- END EMAIL ---

[RÀNG BUỘC NGHIÊM NGẶT - STRICT CONSTRAINTS]
1. CHỈ TRẢ VỀ CHUỖI JSON THUẦN (RAW JSON STRING ONLY).
2. TUYỆT ĐỐI KHÔNG bọc khối JSON trong bất kỳ thẻ Markdown Code Block nào (KHÔNG sử dụng ```json hoặc ```).
3. TUYỆT ĐỐI KHÔNG thêm bất kỳ câu chào, lời mở đầu (như "Dưới đây là kết quả..."), lời giải thích hay lời kết luận trước và sau chuỗi JSON.
4. Ký tự ĐẦU TIÊN của phản hồi PHẢI LÀ dấu mở ngoặc nhọn `{` và ký tự CUỐI CÙNG của phản hồi PHẢI LÀ dấu đóng ngoặc nhọn `}`.
5. Nếu không tìm thấy thông tin tên hoặc số điện thoại trong email, hãy để giá trị của trường đó là null, tuyệt đối không tự bịa đặt dữ liệu.

[ĐỊNH DẠNG ĐẦU RA - OUTPUT FORMAT INSTRUCTIONS]
Hãy tuân thủ nghiêm ngặt cấu trúc JSON Schema sau đây:
{formatInstructions}
```

---

## 3. PHÂN TÍCH CÁC CÂU LỆNH RÀNG BUỘC MẠNH MẼ (CONSTRAINTS ANALYSIS)

| Câu lệnh Ràng buộc | Mục đích Kỹ thuật & Tác động triệt tiêu lỗi |
| :--- | :--- |
| **`CHỈ TRẢ VỀ CHUỖI JSON THUẦN (RAW JSON STRING ONLY)`** | Định hình phạm vi phản hồi của AI chỉ tập trung duy nhất vào dữ liệu JSON, ngăn chặn bản năng giao tiếp của LLM. |
| **`TUYỆT ĐỐI KHÔNG bọc khối JSON trong bất kỳ thẻ Markdown Code Block nào...`** | Triệt tiêu hoàn toàn các ký tự ` ```json ` hoặc ` ``` ` mà Qwen/Llama thường tự động thêm vào theo thói quen định dạng text editor. |
| **`TUYỆT ĐỐI KHÔNG thêm bất kỳ câu chào, lời mở đầu...`** | Loại bỏ lời thoại conversational (như *"Dưới đây là dữ liệu:"*), giúp chuỗi trả về đạt độ sạch 100% khi đi vào Jackson `ObjectMapper`. |
| **`Ký tự ĐẦU TIÊN PHẢI LÀ '{' và ký tự CUỐI CÙNG PHẢI LÀ '}'`** | Ràng buộc ở cấp độ cú pháp ký tự (Character-level Constraint), giúp LLM ép chặt luồng sinh dữ liệu ngay từ token đầu tiên (`{`). |
| **`Nếu không tìm thấy... hãy để giá trị null, không tự bịa đặt`** | Ngăn chặn hiện tượng AI ảo giác (Hallucination) khi email thiếu dữ liệu. |

---

## 4. MINH CHỨNG CHẠY THỰC TẾ (TEXT LOG DEMONSTRATION)

### **4.1. Dữ liệu Java DTO & Format Instructions sinh bởi `BeanOutputConverter`:**

```java
public record CustomerDTO(String customerName, String phoneNumber) {}
```

**Giá trị của `{formatInstructions}`:**
```json
The output should be formatted as a JSON instance that conforms to the JSON Schema below.
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "title": "CustomerDTO",
  "type": "object",
  "properties": {
    "customerName": { "type": "string", "description": "Tên đầy đủ của khách hàng" },
    "phoneNumber": { "type": "string", "description": "Số điện thoại liên hệ của khách hàng" }
  },
  "required": ["customerName", "phoneNumber"]
}
```

---

### **4.2. Dữ liệu `{email}` đầu vào:**
```text
Kính gửi bộ phận Chăm sóc khách hàng Rikkei Academy,
Tôi tên là Trần Thị Mai. Tôi đang quan tâm đến khóa học AI Integration cho doanh nghiệp. 
Nhờ trung tâm tư vấn giúp tôi qua số điện thoại 0912345678 hoặc email mai.tran@gmail.com.
Xin cảm ơn!
```

---

### **4.3. Full Prompt gửi tới LLM (Qwen2.5-Coder:7B / Gemini 2.5 Flash):**

```text
[VAI TRÒ - ROLE]
Bạn là một hệ thống trích xuất dữ liệu tự động có độ chính xác tuyệt đối, làm nhiệm vụ phân tích văn bản và trả về kết quả định dạng RAW JSON cho trình giải tuần tự hóa (Jackson Serializer) của ứng dụng Java Backend.

[MỤC TIÊU - GOAL]
Bóc tách thông tin tên khách hàng và số điện thoại từ nội dung email được cung cấp.

[NGỮ CẢNH - CONTEXT]
Nội dung email cần bóc tách dữ liệu:
--- BEGIN EMAIL ---
Kính gửi bộ phận Chăm sóc khách hàng Rikkei Academy,
Tôi tên là Trần Thị Mai. Tôi đang quan tâm đến khóa học AI Integration cho doanh nghiệp. 
Nhờ trung tâm tư vấn giúp tôi qua số điện thoại 0912345678 hoặc email mai.tran@gmail.com.
Xin cảm ơn!
--- END EMAIL ---

[RÀNG BUỘC NGHIÊM NGẶT - STRICT CONSTRAINTS]
1. CHỈ TRẢ VỀ CHUỖI JSON THUẦN (RAW JSON STRING ONLY).
2. TUYỆT ĐỐI KHÔNG bọc khối JSON trong bất kỳ thẻ Markdown Code Block nào (KHÔNG sử dụng ```json hoặc ```).
3. TUYỆT ĐỐI KHÔNG thêm bất kỳ câu chào, lời mở đầu (như "Dưới đây là kết quả..."), lời giải thích hay lời kết luận trước và sau chuỗi JSON.
4. Ký tự ĐẦU TIÊN của phản hồi PHẢI LÀ dấu mở ngoặc nhọn `{` và ký tự CUỖI CÙNG của phản hồi PHẢI LÀ dấu đóng ngoặc nhọn `}`.
5. Nếu không tìm thấy thông tin tên hoặc số điện thoại trong email, hãy để giá trị của trường đó là null, tuyệt đối không tự bịa đặt dữ liệu.

[ĐỊNH DẠNG ĐẦU RA - OUTPUT FORMAT INSTRUCTIONS]
Hãy tuân thủ nghiêm ngặt cấu trúc JSON Schema sau đây:
The output should be formatted as a JSON instance that conforms to the JSON Schema below.
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "title": "CustomerDTO",
  "type": "object",
  "properties": {
    "customerName": { "type": "string", "description": "Tên đầy đủ của khách hàng" },
    "phoneNumber": { "type": "string", "description": "Số điện thoại liên hệ của khách hàng" }
  },
  "required": ["customerName", "phoneNumber"]
}
```

---

### **4.4. Phản hồi thực tế (Raw Response) từ AI:**

```json
{"customerName":"Trần Thị Mai","phoneNumber":"0912345678"}
```

> **Nhận xét kết quả:** Phản hồi hoàn toàn là chuỗi JSON sạch (Raw JSON), bắt đầu bằng `{` và kết thúc bằng `}`, không có thẻ markdown fence ```json hay bất kỳ văn bản giao tiếp nào. Phương thức `beanOutputConverter.convert(response)` thực thi giải tuần tự hóa sang `CustomerDTO` thành công 100% không gặp lỗi!
