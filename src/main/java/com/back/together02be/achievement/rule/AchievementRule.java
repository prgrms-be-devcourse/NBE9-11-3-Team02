package com.back.together02be.achievement.rule;

import com.back.together02be.achievement.event.TradeCompletedEvent;

public interface AchievementRule {
    // 이 룰이 DB의 어떤 업적(code)과 연결되는지 반환
    String getTargetAchievementCode();
    // 1. 이 업적이 이미 달성되었는지, 또는 조건에 부합하는지 검사
    boolean isSatisfied(TradeCompletedEvent event);

    // 자동 등록 시 사용할 기본 정보 정의
    default String getDefaultName() { return getTargetAchievementCode(); }
    default String getDefaultDescription() { return "자동 등록된 업적입니다."; }
}
