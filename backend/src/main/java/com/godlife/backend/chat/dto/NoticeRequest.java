package com.godlife.backend.chat.dto;

import jakarta.validation.constraints.Size;

/** 빈 내용(또는 null)이면 공지를 내린다. */
public record NoticeRequest(
        @Size(max = 300, message = "공지는 300자까지 쓸 수 있어요.")
        String content) {
}
