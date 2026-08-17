package com.rhotel.booking.dto;

/**
 * Java Record lưu trữ thông tin bóc tách đặt phòng từ Email khách hàng.
 */
public record BookingExtraction(
    String guestName,
    String checkInDate,
    int durationNights,
    String roomType
) {}
