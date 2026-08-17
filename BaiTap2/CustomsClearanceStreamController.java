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
 * Đã khắc phục lỗi blocking bằng cách khai báo produces = MediaType.TEXT_EVENT_STREAM_VALUE.
 */
@RestController
@RequestMapping("/api/v1/ai")
@CrossOrigin(origins = "*")
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
