package com.rikkei.hr.dto;

import java.util.List;

/**
 * Java Record bóc tách thông tin ứng viên từ CV thô.
 */
public record CandidateExtraction(
    String fullName,
    String phone,
    String email,
    List<String> skills,
    int yearsExperience
) {}
