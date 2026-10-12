-- 더미 '기타' 챌린지에 세부 종류를 붙인다.
-- 세부 종류가 없는 '기타' 챌린지는 AI 가 사진 종류를 보지 않아서(어떤 사진이든 통과),
-- 시연할 때 "산책 챌린지에 컴퓨터 사진" 같은 것이 그대로 올라간다. 제목에 맞는 종류를 붙여 AI 가 판정하게 한다.
-- 여러 번 돌려도 결과가 같다. (실제 회원이 만든 챌린지는 건드리지 않는다: 더미 회원이 연 것만)

UPDATE challenges c
JOIN users u ON u.id = c.host_id AND u.email LIKE '%@dummy.godlife'
JOIN categories cat ON cat.id = c.category_id AND cat.ai_label = 'other'
JOIN category_sub_types s ON s.category_id = cat.id AND s.ai_label = CASE
    WHEN c.title LIKE '%기상%' OR c.title LIKE '%모닝%' THEN 'wake_up'
    WHEN c.title LIKE '%산책%' OR c.title LIKE '%한 바퀴%' THEN 'walk'
    WHEN c.title LIKE '%물 2L%' OR c.title LIKE '%물 8잔%' THEN 'water'
    WHEN c.title LIKE '%정리%' OR c.title LIKE '%청소%' THEN 'clean'
    WHEN c.title LIKE '%식물%' THEN 'plant'
END
SET c.sub_type_id = s.id
WHERE c.sub_type_id IS NULL;

SELECT c.id, c.title, s.name AS sub_type, s.ai_label
FROM challenges c
JOIN categories cat ON cat.id = c.category_id AND cat.ai_label = 'other'
LEFT JOIN category_sub_types s ON s.id = c.sub_type_id
ORDER BY c.id;
