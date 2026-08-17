package com.rikkei.hr.service;

import com.rikkei.hr.dto.CandidateExtraction;
import com.rikkei.hr.entity.Candidate;
import com.rikkei.hr.repository.CandidateRepository;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * Service triển khai quy trình ETL Resume Parser.
 */
@Service
public class CandidateETLService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@(.+)$");

    private final ChatModel chatModel;
    private final CandidateRepository candidateRepository;
    private final BeanOutputConverter<CandidateExtraction> outputConverter;

    public CandidateETLService(ChatModel chatModel, CandidateRepository candidateRepository) {
        this.chatModel = chatModel;
        this.candidateRepository = candidateRepository;
        this.outputConverter = new BeanOutputConverter<>(CandidateExtraction.class);
    }

    /**
     * Quy trình ETL chính: KHÔNG đánh dấu @Transactional ở phương thức này
     * để tránh chiếm dụng kết nối DB Connection Pool trong lúc chờ API LLM (Extract & Transform).
     */
    public Candidate processResume(String resumeText) {
        // 1. EXTRACT & TRANSFORM: Gọi LLM trích xuất dữ liệu (Bên ngoài Transaction)
        CandidateExtraction extraction = extractAndTransform(resumeText);

        // 2. VALIDATE: Kiểm tra hợp lệ dữ liệu nghiệp vụ
        validateCandidate(extraction);

        // 3. LOAD: Lưu xuống DB thông qua phương thức Transaction riêng biệt
        return saveCandidateToDatabase(extraction);
    }

    private CandidateExtraction extractAndTransform(String resumeText) {
        String promptString = """
            [VAI TRÒ - ROLE]
            Bạn là một hệ thống trích xuất dữ liệu hồ sơ ứng viên (ETL Resume Parser) tự động.
            
            [NGỮ CẢNH CV]
            --- BEGIN RESUME ---
            {resumeText}
            --- END RESUME ---
            
            [RÀNG BUỘC NGHIÊM NGẶT]
            1. CHỈ TRẢ VỀ CHUỖI JSON THUẦN (RAW JSON).
            2. TUYỆT ĐỐI KHÔNG bọc JSON trong bất kỳ thẻ Markdown fence nào.
            3. Ký tự đầu tiên PHẢI LÀ '{' và ký tự cuối cùng PHẢI LÀ '}'.
            
            [ĐỊNH DẠNG ĐẦU RA]
            {formatInstructions}
            """;

        PromptTemplate promptTemplate = new PromptTemplate(promptString);
        Prompt prompt = promptTemplate.create(Map.of(
            "resumeText", resumeText,
            "formatInstructions", outputConverter.getFormatInstructions()
        ));

        String rawResponse = chatModel.call(prompt).getResult().getOutput().getText();
        return outputConverter.convert(rawResponse);
    }

    private void validateCandidate(CandidateExtraction extraction) {
        if (extraction == null) {
            throw new IllegalArgumentException("Dữ liệu bóc tách từ CV không được để trống (null)");
        }

        // Validation Rule 1: Họ tên không được để trống
        if (extraction.fullName() == null || extraction.fullName().trim().isEmpty()) {
            throw new IllegalArgumentException("Validation Failed: Họ và tên ứng viên không được để trống");
        }

        // Validation Rule 2: Email không được trống và phải đúng định dạng Regex
        if (extraction.email() == null || !EMAIL_PATTERN.matcher(extraction.email()).matches()) {
            throw new IllegalArgumentException("Validation Failed: Email ứng viên không hợp lệ (" + extraction.email() + ")");
        }

        // Validation Rule 3: Số năm kinh nghiệm phải >= 0
        if (extraction.yearsExperience() < 0) {
            throw new IllegalArgumentException("Validation Failed: Số năm kinh nghiệm phải lớn hơn hoặc bằng 0");
        }

        // Validation Rule 4: Kiểm tra email đã tồn tại trong DB chưa
        if (candidateRepository.existsByEmail(extraction.email())) {
            throw new IllegalStateException("Validation Failed: Email " + extraction.email() + " đã tồn tại trong hệ thống");
        }
    }

    /**
     * Giai đoạn LOAD: Đánh dấu @Transactional ngắn gọn cho thao tác DB
     */
    @Transactional
    public Candidate saveCandidateToDatabase(CandidateExtraction extraction) {
        Candidate candidate = new Candidate(
            extraction.fullName().trim(),
            extraction.phone(),
            extraction.email().trim().toLowerCase(),
            extraction.skills(),
            extraction.yearsExperience()
        );
        return candidateRepository.save(candidate);
    }
}
