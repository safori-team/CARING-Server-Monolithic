package com.caring.infra.ai.hume.mapper;

import com.caring.infra.ai.hume.dto.callback.*;
import com.caring.infra.ai.hume.dto.processed.*;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Hume 원본 응답을 Lambda 전달 형식으로 가공한다.
 * 명세서의 "Spring 가공 규칙"을 그대로 구현.
 */
@Component
public class HumeResultMapper {

    private static final int PROSODY_SUMMARY_TOP_N = 10;
    private static final int BURST_SUMMARY_TOP_N = 5;
    private static final int LANGUAGE_SUMMARY_TOP_N = 10;
    private static final int TOP_EMOTIONS_PER_UTTERANCE = 3;

    /**
     * Hume 원본 predictions에서 전체 EmotionAnalysis를 생성한다.
     */
    public EmotionAnalysis toEmotionAnalysis(HumeModels models) {
        ProsodyResult prosody = models.getProsody() != null
                ? mapProsody(models.getProsody()) : null;
        BurstResult burst = models.getBurst() != null
                ? mapBurst(models.getBurst()) : null;
        LanguageResult language = models.getLanguage() != null
                ? mapLanguage(models.getLanguage()) : null;

        return EmotionAnalysis.builder()
                .prosody(prosody)
                .burst(burst)
                .language(language)
                .build();
    }

    /**
     * Language 모델의 STT 결과를 이어붙여 텍스트를 추출한다.
     */
    public String extractSttText(HumeModels models) {
        if (models.getLanguage() == null || models.getLanguage().getGroupedPredictions() == null) {
            return "";
        }
        return models.getLanguage().getGroupedPredictions().stream()
                .flatMap(group -> group.getPredictions().stream())
                .map(HumePrediction::getText)
                .filter(Objects::nonNull)
                .collect(Collectors.joining(" "));
    }

    // === Prosody ===

    private ProsodyResult mapProsody(HumeModelResult prosodyModel) {
        List<HumePrediction> allPredictions = flattenPredictions(prosodyModel);

        List<ProsodyResult.ProsodyUtterance> utterances = allPredictions.stream()
                .map(pred -> ProsodyResult.ProsodyUtterance.builder()
                        .text(pred.getText())
                        .time(toTimeRange(pred.getTime()))
                        .confidence(pred.getConfidence())
                        .topEmotions(topN(pred.getEmotions(), TOP_EMOTIONS_PER_UTTERANCE))
                        .build())
                .toList();

        List<List<HumeEmotion>> allEmotions = allPredictions.stream()
                .map(HumePrediction::getEmotions)
                .filter(Objects::nonNull)
                .toList();

        return ProsodyResult.builder()
                .summary(computeSummary(allEmotions, PROSODY_SUMMARY_TOP_N))
                .utterances(utterances)
                .build();
    }

    // === Burst ===

    private BurstResult mapBurst(HumeModelResult burstModel) {
        List<HumePrediction> allPredictions = flattenPredictions(burstModel);

        List<BurstResult.BurstEvent> events = allPredictions.stream()
                .map(pred -> BurstResult.BurstEvent.builder()
                        .time(toTimeRange(pred.getTime()))
                        .description(extractDescription(pred))
                        .topEmotions(topN(pred.getEmotions(), TOP_EMOTIONS_PER_UTTERANCE))
                        .build())
                .toList();

        List<List<HumeEmotion>> allEmotions = allPredictions.stream()
                .map(HumePrediction::getEmotions)
                .filter(Objects::nonNull)
                .toList();

        return BurstResult.builder()
                .summary(computeSummary(allEmotions, BURST_SUMMARY_TOP_N))
                .events(events)
                .build();
    }

    // === Language ===

    private LanguageResult mapLanguage(HumeModelResult languageModel) {
        List<HumePrediction> allPredictions = flattenPredictions(languageModel);

        // Utterances
        List<LanguageResult.LanguageUtterance> utterances = allPredictions.stream()
                .map(pred -> LanguageResult.LanguageUtterance.builder()
                        .text(pred.getText())
                        .position(toTextPosition(pred.getPosition()))
                        .topEmotions(topN(pred.getEmotions(), TOP_EMOTIONS_PER_UTTERANCE))
                        .sentimentDominant(computeSentimentDominant(pred.getSentiment()))
                        .build())
                .toList();

        // Emotion summary
        List<List<HumeEmotion>> allEmotions = allPredictions.stream()
                .map(HumePrediction::getEmotions)
                .filter(Objects::nonNull)
                .toList();

        // Sentiment aggregation
        LanguageResult.SentimentResult sentiment = aggregateSentiment(allPredictions);

        // Toxicity aggregation
        List<EmotionScore> toxicity = aggregateToxicity(allPredictions);

        return LanguageResult.builder()
                .summary(computeSummary(allEmotions, LANGUAGE_SUMMARY_TOP_N))
                .sentiment(sentiment)
                .toxicity(toxicity)
                .utterances(utterances)
                .build();
    }

    // === 공통 유틸 ===

    private List<HumePrediction> flattenPredictions(HumeModelResult model) {
        if (model.getGroupedPredictions() == null) {
            return List.of();
        }
        return model.getGroupedPredictions().stream()
                .flatMap(group -> group.getPredictions().stream())
                .toList();
    }

    /**
     * Summary 계산: 각 utterance의 동일 이름 감정 score를 모아 평균 → 내림차순 → 상위 N개
     */
    List<EmotionScore> computeSummary(List<List<HumeEmotion>> allEmotions, int topN) {
        Map<String, List<Double>> scoresByName = new LinkedHashMap<>();

        for (List<HumeEmotion> emotions : allEmotions) {
            for (HumeEmotion emotion : emotions) {
                scoresByName.computeIfAbsent(emotion.getName(), k -> new ArrayList<>())
                        .add(emotion.getScore());
            }
        }

        return scoresByName.entrySet().stream()
                .map(entry -> new EmotionScore(
                        entry.getKey(),
                        entry.getValue().stream().mapToDouble(Double::doubleValue).average().orElse(0.0)
                ))
                .sorted(Comparator.comparingDouble(EmotionScore::getScore).reversed())
                .limit(topN)
                .toList();
    }

    /**
     * 감정 목록에서 score 내림차순 상위 N개를 추출한다.
     */
    private List<EmotionScore> topN(List<HumeEmotion> emotions, int n) {
        if (emotions == null) return List.of();
        return emotions.stream()
                .sorted(Comparator.comparingDouble(HumeEmotion::getScore).reversed())
                .limit(n)
                .map(e -> new EmotionScore(e.getName(), e.getScore()))
                .toList();
    }

    /**
     * sentiment 배열에서 argmax + 1 = dominant 값 계산
     */
    private int computeSentimentDominant(List<HumeEmotion> sentiment) {
        if (sentiment == null || sentiment.isEmpty()) return 0;
        return sentiment.stream()
                .max(Comparator.comparingDouble(HumeEmotion::getScore))
                .map(e -> Integer.parseInt(e.getName()))
                .orElse(0);
    }

    /**
     * 전체 predictions의 sentiment를 평균 내어 distribution, dominant, weighted_mean을 산출한다.
     */
    private LanguageResult.SentimentResult aggregateSentiment(List<HumePrediction> predictions) {
        List<List<HumeEmotion>> allSentiments = predictions.stream()
                .map(HumePrediction::getSentiment)
                .filter(Objects::nonNull)
                .toList();

        if (allSentiments.isEmpty()) {
            return LanguageResult.SentimentResult.builder()
                    .distribution(List.of())
                    .dominant(0)
                    .weightedMean(0.0)
                    .build();
        }

        // 1~9 각 점수별 평균 score
        Map<String, List<Double>> scoresByPoint = new LinkedHashMap<>();
        for (List<HumeEmotion> sentiments : allSentiments) {
            for (HumeEmotion s : sentiments) {
                scoresByPoint.computeIfAbsent(s.getName(), k -> new ArrayList<>())
                        .add(s.getScore());
            }
        }

        List<EmotionScore> distribution = new ArrayList<>();
        for (int i = 1; i <= 9; i++) {
            String key = String.valueOf(i);
            double avg = scoresByPoint.getOrDefault(key, List.of(0.0)).stream()
                    .mapToDouble(Double::doubleValue).average().orElse(0.0);
            distribution.add(new EmotionScore(key, avg));
        }

        // dominant: score가 가장 높은 항목
        int dominant = distribution.stream()
                .max(Comparator.comparingDouble(EmotionScore::getScore))
                .map(e -> Integer.parseInt(e.getName()))
                .orElse(0);

        // weighted_mean
        double sumWeighted = distribution.stream()
                .mapToDouble(e -> Integer.parseInt(e.getName()) * e.getScore())
                .sum();
        double sumScores = distribution.stream()
                .mapToDouble(EmotionScore::getScore)
                .sum();
        double weightedMean = sumScores > 0 ? sumWeighted / sumScores : 0.0;

        return LanguageResult.SentimentResult.builder()
                .distribution(distribution)
                .dominant(dominant)
                .weightedMean(weightedMean)
                .build();
    }

    /**
     * 전체 predictions의 toxicity를 평균 낸다. (6개 카테고리 고정)
     */
    private List<EmotionScore> aggregateToxicity(List<HumePrediction> predictions) {
        List<List<HumeEmotion>> allToxicity = predictions.stream()
                .map(HumePrediction::getToxicity)
                .filter(Objects::nonNull)
                .toList();

        if (allToxicity.isEmpty()) return List.of();

        Map<String, List<Double>> scoresByName = new LinkedHashMap<>();
        for (List<HumeEmotion> toxicity : allToxicity) {
            for (HumeEmotion t : toxicity) {
                scoresByName.computeIfAbsent(t.getName(), k -> new ArrayList<>())
                        .add(t.getScore());
            }
        }

        return scoresByName.entrySet().stream()
                .map(entry -> new EmotionScore(
                        entry.getKey(),
                        entry.getValue().stream().mapToDouble(Double::doubleValue).average().orElse(0.0)
                ))
                .toList();
    }

    private ProsodyResult.TimeRange toTimeRange(HumeTimeRange time) {
        if (time == null) return null;
        return new ProsodyResult.TimeRange(time.getBegin(), time.getEnd());
    }

    private LanguageResult.TextPosition toTextPosition(HumeTextPosition position) {
        if (position == null) return null;
        return new LanguageResult.TextPosition(position.getBegin(), position.getEnd());
    }

    private String extractDescription(HumePrediction prediction) {
        if (prediction.getDescriptions() == null || prediction.getDescriptions().isEmpty()) {
            return null;
        }
        return prediction.getDescriptions().get(0).getName();
    }
}
