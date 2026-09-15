package com.auknowlog.backend.quality.dto;

import java.util.List;

public record QualityReviewQueue(
        List<DuplicateReviewItem> duplicateCases,
        List<ObjectiveReviewItem> objectiveCases
) {
    public record DuplicateReviewItem(
            long pairId,
            long questionAId,
            String questionATopic,
            String questionAText,
            long questionBId,
            String questionBTopic,
            String questionBText,
            double similarity,
            String systemVerdict,
            String humanVerdict
    ) {
    }

    public record ObjectiveReviewItem(
            long caseId,
            String caseType,
            String topic,
            String referenceTitle,
            String importance,
            Long learningObjectiveId,
            String learningObjectiveTitle,
            Long learningQuestionId,
            String learningQuestionText,
            String aiVerdict,
            double aiConfidence,
            String aiRationale,
            String humanVerdict
    ) {
    }
}
