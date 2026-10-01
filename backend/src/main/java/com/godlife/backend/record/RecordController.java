package com.godlife.backend.record;

import com.godlife.backend.auth.AuthUser;
import com.godlife.backend.record.dto.RecordDtos.DayResponse;
import com.godlife.backend.record.dto.RecordDtos.DiaryCreated;
import com.godlife.backend.record.dto.RecordDtos.DiaryRequest;
import com.godlife.backend.record.dto.RecordDtos.MonthResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;

/** 갓생기록 (로그인 회원, 본인 기록만): 캘린더 · 그날 결과 · 일기 */
@RestController
@RequiredArgsConstructor
public class RecordController {

    private final RecordService recordService;
    private final Clock clock;

    /** 한 달 캘린더 + 요약. ?month=2026-10 (없거나 잘못되면 이번 달), ?challengeId= 로 챌린지 하나만 */
    @GetMapping("/api/records")
    public MonthResponse month(@RequestParam(required = false) String month,
                               @RequestParam(required = false) Long challengeId,
                               @AuthenticationPrincipal AuthUser authUser) {
        return recordService.month(authUser.id(), parse(month), challengeId);
    }

    /** 그날 챌린지별 결과 + 그날 쓴 일기들 */
    @GetMapping("/api/records/days/{date}")
    public DayResponse day(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                           @AuthenticationPrincipal AuthUser authUser) {
        return recordService.day(authUser.id(), date);
    }

    /** 일기 새로 쓰기: 글 · 기분 · 태그한 챌린지 (하루에 여러 개 쓸 수 있다) */
    @PostMapping("/api/records/days/{date}/diaries")
    public ResponseEntity<DiaryCreated> createDiary(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @AuthenticationPrincipal AuthUser authUser, @Valid @RequestBody DiaryRequest request) {
        Long id = recordService.createDiary(authUser.id(), date, request.content(), request.mood(),
                request.challengeIds());
        return ResponseEntity.status(HttpStatus.CREATED).body(new DiaryCreated(id));
    }

    /** 사진으로 일기 시작하기 (multipart: file = JPG/PNG 5MB 이하). 글 · 기분은 이어서 고치기로 채운다. */
    @PostMapping(value = "/api/records/days/{date}/diaries/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DiaryCreated> createDiaryWithPhoto(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @AuthenticationPrincipal AuthUser authUser, @RequestPart("file") MultipartFile file) {
        Long id = recordService.createDiaryWithPhoto(authUser.id(), date, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(new DiaryCreated(id));
    }

    /** 일기 고치기 (글도 기분도 사진도 없으면 지운다) */
    @PutMapping("/api/records/diaries/{id}")
    public ResponseEntity<Void> updateDiary(@PathVariable Long id, @AuthenticationPrincipal AuthUser authUser,
                                            @Valid @RequestBody DiaryRequest request) {
        recordService.updateDiary(authUser.id(), id, request.content(), request.mood(), request.challengeIds());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/records/diaries/{id}")
    public ResponseEntity<Void> deleteDiary(@PathVariable Long id, @AuthenticationPrincipal AuthUser authUser) {
        recordService.deleteDiary(authUser.id(), id);
        return ResponseEntity.noContent().build();
    }

    /** 일기에 사진 한 장 붙이기 (multipart: file = JPG/PNG 5MB 이하). 다시 올리면 바꾼다. */
    @PostMapping(value = "/api/records/diaries/{id}/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> saveDiaryPhoto(@PathVariable Long id, @AuthenticationPrincipal AuthUser authUser,
                                               @RequestPart("file") MultipartFile file) {
        recordService.saveDiaryPhoto(authUser.id(), id, file);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/records/diaries/{id}/photo")
    public ResponseEntity<Void> deleteDiaryPhoto(@PathVariable Long id, @AuthenticationPrincipal AuthUser authUser) {
        recordService.deleteDiaryPhoto(authUser.id(), id);
        return ResponseEntity.noContent().build();
    }

    /** 내 일기 사진 (본인만). 바뀔 수 있어 캐시하지 않는다. */
    @GetMapping("/api/records/diaries/{id}/photo")
    public ResponseEntity<Resource> diaryPhoto(@PathVariable Long id, @AuthenticationPrincipal AuthUser authUser) {
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.noStore())
                .body(new FileSystemResource(recordService.diaryPhoto(authUser.id(), id)));
    }

    /** 내 인증 사진. 이미지 태그로는 토큰을 못 보내서 프론트가 fetch 로 받아 보여 준다. */
    @GetMapping("/api/records/photos/{verificationId}")
    public ResponseEntity<Resource> photo(@PathVariable Long verificationId,
                                          @AuthenticationPrincipal AuthUser authUser) {
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePrivate())
                .body(new FileSystemResource(recordService.photo(authUser.id(), verificationId)));
    }

    private YearMonth parse(String month) {
        try {
            return month == null ? YearMonth.now(clock) : YearMonth.parse(month);
        } catch (DateTimeParseException e) {
            return YearMonth.now(clock);
        }
    }
}
