package com.example.musicplayer.network

/**
 * Cấu hình chung cho Jamendo API.
 *
 * Base URL: https://api.jamendo.com/v3.0/
 * MỌI request tới Jamendo đều bắt buộc có query param `client_id`
 * (không cần thẻ tín dụng — chỉ cần đăng ký tài khoản miễn phí).
 *
 * 📌 Cách lấy client_id:
 *   1. Vào https://devportal.jamendo.com/ → tạo tài khoản (chỉ cần email).
 *   2. Vào "Applications" → "Create Application" → điền tên/mô tả.
 *   3. Copy client_id được cấp về dán vào hằng số CLIENT_ID bên dưới.
 *   Plan mặc định "read only" là đủ cho app phát nhạc (không cần OAuth2).
 */
object JamendoConfig {

    /** Base URL của Jamendo API v3.0 */
    const val BASE_URL = "https://api.jamendo.com/v3.0/"

    /** Client_id do user đăng ký — dùng cho MỌI request tới Jamendo. */
    const val CLIENT_ID = "b0c1fcd9"
}
