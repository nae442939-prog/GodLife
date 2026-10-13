package com.godlife.backend.challenge;

import java.util.regex.Pattern;

/**
 * 챌린지 대표 사진의 파일 키 ↔ 주소.
 * 키는 "cover/{챌린지 id}/{uuid}.jpg" (개설자가 올린 사진) 또는 "cover/seed/{이름}.jpg" (더미 챌린지가 같이 쓰는 사진).
 */
public final class ChallengeCover {

    public static final String KEY_PREFIX = "cover/";
    public static final String URL_PREFIX = "/api/challenge-covers/";

    private static final Pattern FOLDER = Pattern.compile("[0-9]{1,19}|seed");
    private static final Pattern NAME = Pattern.compile("[0-9a-z-]{1,40}\\.jpg");

    private ChallengeCover() {
    }

    /** 파일 키 → 화면이 쓰는 주소. 사진이 없으면 null */
    public static String urlOf(String coverKey) {
        return coverKey == null || !coverKey.startsWith(KEY_PREFIX)
                ? null
                : URL_PREFIX + coverKey.substring(KEY_PREFIX.length());
    }

    /** 주소로 들어온 폴더 · 파일 이름이 우리가 만든 모양인지 */
    public static boolean isValid(String folder, String name) {
        return FOLDER.matcher(folder).matches() && NAME.matcher(name).matches();
    }
}
