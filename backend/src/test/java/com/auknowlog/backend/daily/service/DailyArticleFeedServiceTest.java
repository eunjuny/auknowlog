package com.auknowlog.backend.daily.service;

import com.auknowlog.backend.daily.entity.DailyLearningFocus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DailyArticleFeedServiceTest {

    @Test
    void 개발_직접_관련_기사를_반도체_일반_기사보다_우선한다() {
        var semiconductor = candidate("반도체 팹 증설 경쟁", "생산 라인과 공급망을 확대한다", 10);
        var kubernetes = candidate("쿠버네티스 보안 패치 배포", "클라우드 인프라 취약점 대응 방법", 9);

        var selected = DailyArticleFeedService.chooseCandidate(List.of(semiconductor, kubernetes));

        assertThat(selected.title()).isEqualTo(kubernetes.title());
        assertThat(selected.focusTier()).isEqualTo(DailyLearningFocus.DEVELOPER_CORE);
    }

    @Test
    void 개발_직접_관련_후보가_없으면_개발_인접_기사를_넓은_IT_기사보다_우선한다() {
        var semiconductor = candidate("반도체 팹 증설 경쟁", "HBM 생산 라인과 공급망을 확대한다", 10);
        var llm = candidate("생성형 AI 모델 운영 방식 변화", "LLM과 데이터센터 GPU 수요", 8);

        var selected = DailyArticleFeedService.chooseCandidate(List.of(semiconductor, llm));

        assertThat(selected.title()).isEqualTo(llm.title());
        assertThat(selected.focusTier()).isEqualTo(DailyLearningFocus.DEVELOPER_ADJACENT);
    }

    @Test
    void 개발_후보가_전혀_없을_때만_넓은_IT_기사를_선택한다() {
        var semiconductor = candidate("반도체 팹 증설 경쟁", "생산 라인과 공급망을 확대한다", 10);
        var robot = candidate("로봇 시장 성장", "산업용 로봇 도입이 증가했다", 8);

        var selected = DailyArticleFeedService.chooseCandidate(List.of(robot, semiconductor));

        assertThat(selected.title()).isEqualTo(robot.title());
        assertThat(selected.focusTier()).isEqualTo(DailyLearningFocus.IT_EXPANSION);
    }

    private DailyArticleFeedService.ArticleCandidate candidate(String title, String description, int hour) {
        return new DailyArticleFeedService.ArticleCandidate(title, "https://example.com/" + hour,
                LocalDateTime.of(2026, 9, 28, hour, 0), description, List.of("IT"), null);
    }
}
