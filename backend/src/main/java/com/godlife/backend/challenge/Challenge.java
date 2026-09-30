package com.godlife.backend.challenge;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "challenges")
public class Challenge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "host_id", nullable = false, updatable = false)
    private Long hostId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String description;

    /** 방장 공지 (없으면 null). 채팅방 맨 위에 고정된다. */
    private String notice;

    @Column(name = "notice_updated_at")
    private LocalDateTime noticeUpdatedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private ChallengeMode mode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private ChallengeVisibility visibility;

    /** 초대 링크 코드. 모든 챌린지가 가진다(공개 챌린지도 링크로 공유 가능). 새어 나가면 개설자가 재발급한다. */
    @Column(name = "invite_code", nullable = false, unique = true)
    private String inviteCode;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "frequency_type", nullable = false)
    private FrequencyType frequencyType;

    /** WEEKLY_N 일 때만 값이 있다. */
    @Column(name = "weekly_count")
    private Integer weeklyCount;

    /** 참가 포인트. FREE 는 0. */
    @Column(name = "entry_fee", nullable = false)
    private long entryFee;

    /**
     * 1회 최소/최대 예치 포인트. 지금은 참가 포인트를 고정으로 받으므로 entryFee 와 같게 둔다.
     * (참가자가 금액을 고르는 방식으로 바꿀 때 이 두 값을 따로 받는다)
     */
    @Column(name = "min_bet", nullable = false)
    private long minBet;

    @Column(name = "max_bet", nullable = false)
    private long maxBet;

    @Column(name = "max_participants", nullable = false)
    private int maxParticipants;

    /** 인기순 정렬용 비정규화 카운터. 참여/취소 시 챌린지 행을 잠근 채로 바꾼다. */
    @Column(name = "participant_count", nullable = false)
    private int participantCount;

    /** 인증 가능 시간대(옵션). 둘 다 있거나 둘 다 없다. */
    @Column(name = "verify_from")
    private LocalTime verifyFrom;

    @Column(name = "verify_until")
    private LocalTime verifyUntil;

    /** 부분 성공 시 비례 환급 (포인트 챌린지만) */
    @Column(name = "partial_refund", nullable = false)
    private boolean partialRefund;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ChallengeStatus status;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    public static Challenge create(Long hostId, Category category, String title, String description,
                                   ChallengeMode mode, ChallengeVisibility visibility, String inviteCode,
                                   LocalDate startDate, LocalDate endDate,
                                   FrequencyType frequencyType, Integer weeklyCount, long entryFee,
                                   int maxParticipants, LocalTime verifyFrom, LocalTime verifyUntil,
                                   boolean partialRefund) {
        Challenge c = new Challenge();
        c.hostId = hostId;
        c.category = category;
        c.title = title;
        c.description = description;
        c.mode = mode;
        c.visibility = visibility;
        c.inviteCode = inviteCode;
        c.startDate = startDate;
        c.endDate = endDate;
        c.frequencyType = frequencyType;
        c.weeklyCount = frequencyType == FrequencyType.WEEKLY_N ? weeklyCount : null;
        boolean bet = mode == ChallengeMode.BET;
        c.entryFee = bet ? entryFee : 0;
        c.minBet = c.entryFee;
        c.maxBet = c.entryFee;
        c.partialRefund = bet && partialRefund;
        c.maxParticipants = maxParticipants;
        c.verifyFrom = verifyFrom;
        c.verifyUntil = verifyUntil;
        c.status = ChallengeStatus.RECRUITING;
        return c;
    }

    /** 시작일 당일까지 참여할 수 있다. (시작일 0시에 ONGOING 으로 바뀌어도 그날은 참여 가능) */
    public boolean isRecruiting(LocalDate today) {
        return (status == ChallengeStatus.RECRUITING || status == ChallengeStatus.ONGOING)
                && !today.isAfter(startDate);
    }

    /** 시작일 전날까지만 참여를 취소할 수 있다. */
    public boolean canLeave(LocalDate today) {
        return status == ChallengeStatus.RECRUITING && today.isBefore(startDate);
    }

    /**
     * 인증을 받는 기간인지. 하루는 밤 12시(00:00)부터 다음 날 밤 12시까지이고, 시작일~종료일 모두 포함한다.
     * (상태를 ONGOING 으로 바꾸는 스케줄러가 생기기 전에도 날짜로 판단되도록 상태는 '끝났는지'만 본다)
     */
    public boolean isInProgress(LocalDate today) {
        return status != ChallengeStatus.ENDED && status != ChallengeStatus.SETTLED
                && !today.isBefore(startDate) && !today.isAfter(endDate);
    }

    /** 인증 가능 시간대가 없으면 하루 종일, 있으면 verifyFrom ~ verifyUntil (둘 다 포함) */
    public boolean isVerifyTimeOpen(LocalTime now) {
        return verifyFrom == null || (!now.isBefore(verifyFrom) && !now.isAfter(verifyUntil));
    }

    /**
     * 주 N회 챌린지의 '한 주'는 시작일부터 7일씩 끊는다. (예: 수요일 시작 → 수~화가 한 주)
     * day 가 속한 주의 첫날을 돌려준다.
     */
    public LocalDate weekStartOf(LocalDate day) {
        long weeks = ChronoUnit.DAYS.between(startDate, day) / 7;
        return startDate.plusWeeks(weeks);
    }

    /**
     * 끝까지 성공하려면 필요한 인증 횟수. 매일이면 전체 일수,
     * 주 N회면 주마다 N회 (마지막 주가 N일보다 짧으면 그 주는 남은 일수만큼).
     */
    public int targetCount() {
        int days = (int) totalDays();
        if (frequencyType == FrequencyType.DAILY) {
            return days;
        }
        int fullWeeks = days / 7;
        int rest = days % 7;
        return fullWeeks * weeklyCount + Math.min(weeklyCount, rest);
    }

    /** 자정 스케줄러: 시작일이 되면 진행 중으로 */
    void start() {
        if (status == ChallengeStatus.RECRUITING) {
            status = ChallengeStatus.ONGOING;
        }
    }

    /** 자정 스케줄러: 종료일이 지나면 종료 (정산은 ENDED 인 챌린지를 대상으로 한다) */
    void end() {
        status = ChallengeStatus.ENDED;
    }

    public boolean isPrivate() {
        return visibility == ChallengeVisibility.PRIVATE;
    }

    public boolean isHost(Long userId) {
        return hostId.equals(userId);
    }

    /** 빈 공지는 내린다(null). */
    void changeNotice(String notice, LocalDateTime now) {
        this.notice = notice == null || notice.isBlank() ? null : notice.strip();
        this.noticeUpdatedAt = this.notice == null ? null : now;
    }

    void changeInviteCode(String inviteCode) {
        this.inviteCode = inviteCode;
    }

    public boolean isFull() {
        return participantCount >= maxParticipants;
    }

    public long totalDays() {
        return ChronoUnit.DAYS.between(startDate, endDate) + 1;
    }

    void addParticipant() {
        participantCount++;
    }

    void removeParticipant() {
        participantCount--;
    }
}
