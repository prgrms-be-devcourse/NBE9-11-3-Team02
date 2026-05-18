package com.back.together02be.achievement.controller;

import com.back.together02be.achievement.dto.AchievementRes;
import com.back.together02be.achievement.service.AchievementService;
import com.back.together02be.global.apiRes.ApiRes;
import com.back.together02be.global.security.SecurityUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/achievements")
@RequiredArgsConstructor
public class AchievementController {

    private final AchievementService achievementService;

    @GetMapping("/me")
    public ResponseEntity<ApiRes<List<AchievementRes>>> getMyAchievements(@AuthenticationPrincipal SecurityUser securityUser) {
        List<AchievementRes> achievements = achievementService.getUserAchievements(securityUser.getId());
        return ResponseEntity.ok(new ApiRes<>("달성 업적 조회 성공", achievements));
    }
}