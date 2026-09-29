package com.godlife.backend.challenge;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 챌린지 카테고리 (운동 · 공부 · 독서). 기준 데이터라 db/02-seed.sql 로만 넣는다. */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "categories")
public class Category {

    @Id
    private Integer id;

    @Column(nullable = false)
    private String name;

    /** ai-server 분류 모델의 클래스 라벨과 1:1 */
    @Column(name = "ai_label", nullable = false)
    private String aiLabel;

    @Column(name = "is_active", nullable = false)
    private boolean active;
}
