package com.back.together02be.achievement.controller

import com.back.together02be.achievement.dto.AchievementRes
import com.back.together02be.achievement.service.AchievementService
import com.back.together02be.global.apiRes.ApiRes
import com.back.together02be.global.security.SecurityUser
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/achievements")
class AchievementController(
    private val achievementService: AchievementService
) {

    @GetMapping("/me")
    fun getMyAchievements(
        @AuthenticationPrincipal securityUser: SecurityUser
    ): ResponseEntity<ApiRes<List<AchievementRes>>> {
        val achievements = achievementService.getUserAchievements(securityUser.id)
        return ResponseEntity.ok(ApiRes("달성 업적 조회 성공", achievements))
    }
}