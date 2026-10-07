package com.godlife.backend.follow;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * follows 테이블 (follower_id → following_id, 둘을 묶은 기본키). 행 하나가 전부라 엔티티 없이 SQL 로 다룬다.
 * 팔로우는 한쪽이 누르기만 하면 된다 (요청·수락 없음).
 */
@Repository
public class FollowRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public FollowRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 이미 팔로우 중이면 그대로 (기본키라 두 번 눌러도 한 줄) */
    public void follow(Long followerId, Long followingId) {
        jdbc.update("INSERT IGNORE INTO follows (follower_id, following_id) VALUES (:a, :b)",
                new MapSqlParameterSource("a", followerId).addValue("b", followingId));
    }

    public void unfollow(Long followerId, Long followingId) {
        jdbc.update("DELETE FROM follows WHERE follower_id = :a AND following_id = :b",
                new MapSqlParameterSource("a", followerId).addValue("b", followingId));
    }

    /** 차단하면 서로의 팔로우를 모두 끊는다 */
    public void removeBoth(Long a, Long b) {
        jdbc.update("""
                DELETE FROM follows
                WHERE (follower_id = :a AND following_id = :b) OR (follower_id = :b AND following_id = :a)
                """, new MapSqlParameterSource("a", a).addValue("b", b));
    }

    public boolean exists(Long followerId, Long followingId) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM follows WHERE follower_id = :a AND following_id = :b",
                new MapSqlParameterSource("a", followerId).addValue("b", followingId), Integer.class);
        return n != null && n > 0;
    }

    /** 이 사람을 팔로우하는 (활동 중인) 회원 수 */
    public long followerCount(Long userId) {
        return count("""
                SELECT COUNT(*) FROM follows f JOIN users u ON u.id = f.follower_id
                WHERE f.following_id = :u AND u.status = 'ACTIVE'
                """, userId);
    }

    /** 이 사람이 팔로우하는 (활동 중인) 회원 수 */
    public long followingCount(Long userId) {
        return count("""
                SELECT COUNT(*) FROM follows f JOIN users u ON u.id = f.following_id
                WHERE f.follower_id = :u AND u.status = 'ACTIVE'
                """, userId);
    }

    /** 내가 팔로우하는 회원 id (친구 랭킹 범위) */
    public List<Long> followingIds(Long userId) {
        return jdbc.queryForList("SELECT following_id FROM follows WHERE follower_id = :u",
                new MapSqlParameterSource("u", userId), Long.class);
    }

    /** 한 줄에 보여 줄 회원. mutual = 서로 팔로우하는 사이인지 */
    public record FollowUser(Long userId, String nickname, String profileImageUrl, String bio, boolean mutual) {
    }

    /** 내가 팔로우하는 (활동 중인) 회원들. 닉네임 순 */
    public List<FollowUser> following(Long userId) {
        return list("""
                SELECT u.id, u.nickname, u.profile_image_url, u.bio,
                       EXISTS (SELECT 1 FROM follows b WHERE b.follower_id = u.id AND b.following_id = :u) AS mutual
                FROM follows f JOIN users u ON u.id = f.following_id
                WHERE f.follower_id = :u AND u.status = 'ACTIVE'
                ORDER BY u.nickname LIMIT 500
                """, userId);
    }

    /** 나를 팔로우하는 (활동 중인) 회원들. 닉네임 순 */
    public List<FollowUser> followers(Long userId) {
        return list("""
                SELECT u.id, u.nickname, u.profile_image_url, u.bio,
                       EXISTS (SELECT 1 FROM follows b WHERE b.follower_id = :u AND b.following_id = u.id) AS mutual
                FROM follows f JOIN users u ON u.id = f.follower_id
                WHERE f.following_id = :u AND u.status = 'ACTIVE'
                ORDER BY u.nickname LIMIT 500
                """, userId);
    }

    private List<FollowUser> list(String sql, Long userId) {
        return jdbc.query(sql, new MapSqlParameterSource("u", userId),
                (rs, i) -> new FollowUser(rs.getLong("id"), rs.getString("nickname"),
                        rs.getString("profile_image_url"), rs.getString("bio"), rs.getBoolean("mutual")));
    }

    private long count(String sql, Long userId) {
        Long n = jdbc.queryForObject(sql, new MapSqlParameterSource("u", userId), Long.class);
        return n == null ? 0 : n;
    }
}
