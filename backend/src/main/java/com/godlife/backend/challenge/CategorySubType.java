package com.godlife.backend.challenge;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 카테고리의 세부 종류 (지금은 '기타'만: 일찍 일어나기 · 산책 …). 기준 데이터라 db/02-seed.sql 로만 넣는다.
 * 기타는 사진 모양이 제각각이라, 개설자가 세부 종류를 고르면 AI 가 그 라벨로 인증 사진을 판정한다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "category_sub_types")
public class CategorySubType {

    @Id
    private Integer id;

    @Column(name = "category_id", nullable = false)
    private Integer categoryId;

    @Column(nullable = false)
    private String name;

    /** ai-server 분류 모델의 클래스 라벨과 1:1 */
    @Column(name = "ai_label", nullable = false)
    private String aiLabel;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "is_active", nullable = false)
    private boolean active;
}
