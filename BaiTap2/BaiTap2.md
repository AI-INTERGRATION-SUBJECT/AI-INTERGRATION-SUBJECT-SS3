# BÀI TẬP 2: ĐỌC HIỂU & DÒ LỖI — ENDPOINT STREAM "GIẢ SSE"

---

## 1. NGUYÊN NHÂN LỖI CỐT LÕI (ROOT CAUSES ANALYSIS)

Đoạn mã Controller ban đầu:

```java
@GetMapping("/api/v1/ai/stream")
public Flux<String> getStreamResponse(@RequestParam String message) {
    return chatModel.stream(new Prompt(message))
            .map(response -> response.getResult().getOutput().getText());
}
```

Mặc dù phương thức trả về kiểu `Flux<String>` (một Publisher bất đồng bộ của Project Reactor), endpoint này vẫn hoạt động như một REST API đồng bộ chặn (blocking) thông thường do 2 nguyên nhân cốt lõi sau:

### **Lỗi 1: Thiếu cấu hình Header Content-Type `produces = MediaType.TEXT_EVENT_STREAM_VALUE` (Nguyên nhân chính)**
- Khi `@GetMapping` không khai báo `produces`, Spring WebFlux / Spring MVC sẽ mặc định gán Header `Content-Type: application/json` (hoặc `application/json;charset=UTF-8`).
- Theo chuẩn HTTP và cơ chế của Spring Message Converters (như `Jackson2JsonEncoder`): Khi Content-Type là `application/json`, Spring Boot sẽ **gom tất cả các phần tử của Flux vào một JSON Array** (dạng `["chunk1", "chunk2", "chunk3", ...]`).
- Do đó, Spring phải **đợi cho đến khi Flux hoàn tất (`onComplete`)** thì mới thu thập xong mảng JSON và bắt đầu ghi HTTP Response về Client. Điều này biến luồng stream thành luồng chờ đồng bộ (blocking 15-20s).

### **Lỗi 2: Nguy cơ văng lỗi NullPointerException ở từng Chunk dữ liệu**
- Trong Spring AI, khi streaming phản hồi từ các LLM (như Qwen hoặc Gemini), một số `ChatResponse` chunk đầu tiên (chứa metadata) hoặc chunk cuối cùng (chứa token usage stats) có thể trả về `getResult()` bằng `null`, hoặc `getOutput().getText()` bằng `null` / rỗng.
- Biểu thức lambda `.map(response -> response.getResult().getOutput().getText())` không kiểm tra `null` sẽ gây ra `NullPointerException` (NPE) làm đứt gãy luồng `Flux`.

---

## 2. GIẢI TRÌNH KỸ THUẬT VỀ CƠ CHẾ STREAM SSE TRONG SPRING WEBFLUX

### **2.1. Server-Sent Events (SSE) là gì?**
Server-Sent Events (SSE) là một chuẩn truyền tải dữ liệu theo thời gian thực (Real-time Streaming) qua giao thức HTTP đơn hướng (từ Server gửi về Client).

### **2.2. Điểm khác biệt giữa `application/json` và `text/event-stream`:**

```
+-----------------------------------------------------------------------------------+
| 1. HTTP Content-Type: application/json (LỖI Ở CODE CŨ)                             |
| Client (Browser) <======================= Waiting 20s ======================= Server|
| (Client chờ Server gửi xong toàn bộ JSON Array: ["Xin", " chào", " bạn"] mới render)  |
+-----------------------------------------------------------------------------------+

+-----------------------------------------------------------------------------------+
| 2. HTTP Content-Type: text/event-stream (CHUẨN SSE - ĐÃ SỬA)                       |
| Client (Browser) <--- t=0.1s: data: Xin ------------------------------------ Server|
| Client (Browser) <--- t=0.2s: data:  chào ----------------------------------- Server|
| Client (Browser) <--- t=0.3s: data:  bạn ------------------------------------ Server|
| (HTTP Connection được giữ mở; từng Chunk được đẩy vọt về ngay khi LLM sinh ra)    |
+-----------------------------------------------------------------------------------+
```

### **2.3. Cơ chế hoạt động của WebFlux khi có `MediaType.TEXT_EVENT_STREAM_VALUE`:**
1. **Thiết lập HTTP Connection Streaming:** Ngay khi nhận Request, Spring WebFlux lập tức gửi Header `HTTP/1.1 200 OK` cùng `Content-Type: text/event-stream` và `Cache-Control: no-cache` về cho Client mà **không chờ Flux hoàn tất**.
2. **Push theo Sự kiện (Pushing Chunks):** Mỗi khi LLM sinh ra 1 token mới, `ChatModel.stream()` phát ra một phát sinh (emits element). WebFlux lập tức đóng gói phần tử này theo chuẩn định dạng SSE (`data: <nội dung>\n\n`) và xả (flush) ngay xuống Socket truyền về trình duyệt Client.
3. **Đóng kết nối (`onComplete`):** Khi LLM sinh xong toàn bộ văn bản, `Flux` phát tín hiệu `onComplete`, WebFlux đóng luồng HTTP stream một cách êm đẹp.

---

## 3. MÃ NGUỒN JAVA CONTROLLER HOÀN CHỈNH & TỐI ƯU

Dưới đây là mã nguồn `CustomsClearanceStreamController.java` đã được khắc phục triệt để các lỗi kỹ thuật, hỗ trợ cả `TEXT_EVENT_STREAM_VALUE` lẫn `ServerSentEvent<String>` wrapper chuẩn enterprise:

```java
package com.rlogistics.crm.controller;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.Optional;

/**
 * Controller xử lý Stream tra cứu quy trình thông quan cho R-Logistics.
 */
@RestController
@RequestMapping("/api/v1/ai")
@CrossOrigin(origins = "*") // Hỗ trợ CORS cho Frontend EventSource client
public class CustomsClearanceStreamController {

    private final ChatModel chatModel;

    public CustomsClearanceStreamController(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    /**
     * Endpoint Stream SSE chuẩn cho Client.
     * Khai báo MediaType.TEXT_EVENT_STREAM_VALUE để đẩy dữ liệu dạng real-time stream.
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamCustomsClearanceAdvice(@RequestParam String message) {
        return chatModel.stream(new Prompt(message))
                // Kiểm tra null-safe cho từng chunk phản hồi từ LLM
                .map(chatResponse -> Optional.ofNullable(chatResponse)
                        .map(response -> response.getResult())
                        .map(result -> result.getOutput())
                        .map(output -> output.getText())
                        .orElse("")
                )
                // Lọc bỏ các chunk chuỗi rỗng không có nội dung
                .filter(text -> !text.isEmpty());
    }

    /**
     * Phương án nâng cao: Sử dụng wrapper ServerSentEvent<String> giúp client dễ dàng bắt sự kiện (event & id).
     */
    @GetMapping(value = "/stream-sse-events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> streamCustomsClearanceEvents(@RequestParam String message) {
        return chatModel.stream(new Prompt(message))
                .map(chatResponse -> Optional.ofNullable(chatResponse)
                        .map(response -> response.getResult())
                        .map(result -> result.getOutput())
                        .map(output -> output.getText())
                        .orElse("")
                )
                .filter(text -> !text.isEmpty())
                .map(chunk -> ServerSentEvent.<String>builder()
                        .event("message")
                        .data(chunk)
                        .build());
    }
}
```

---

## 4. BẢNG TỔNG HỢP SO SÁNH TRƯỚC VÀ SAU KHẮC PHỤC

| Tiêu chí | Mã nguồn Cũ | Mã nguồn Sau khi Khắc phục |
| :--- | :--- | :--- |
| **Content-Type Header** | `application/json` (Mặc định) | `text/event-stream` (SSE chuẩn) |
| **Trải nghiệm Client** | ❌ Chờ 15-20s nhận toàn bộ mảng JSON |  Nhận từng chữ ngay khi LLM vừa sinh ra (Real-time) |
| **Độ an toàn Mã nguồn** | ❌ Nguy cơ `NullPointerException` cao |  Kiểm tra Null-safe bằng `Optional` + `filter()` |
| **Tối ưu Network Socket** | ❌ Buffer toàn bộ dữ liệu ở bộ nhớ Server |  Flush từng chunk qua HTTP Stream Socket |
| **Chuẩn Enterprise** | ❌ Không chuẩn streaming |  Đạt chuẩn SSE Server-Sent Events |
