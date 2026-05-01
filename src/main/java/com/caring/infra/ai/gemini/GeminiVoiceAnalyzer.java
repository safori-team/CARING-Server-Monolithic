package com.caring.infra.ai.gemini;

import com.caring.domain.voice.adaptor.VoiceAdaptor;
import com.caring.domain.voice.adaptor.VoiceCompositeAdaptor;
import com.caring.domain.voice.entity.Voice;
import com.caring.domain.voice.entity.VoiceComposite;
import com.caring.infra.ai.gemini.dto.GeminiAnalysisResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.Client;
import com.google.genai.types.Content;
import com.google.genai.types.FileData;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import com.google.genai.types.UploadFileConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
public class GeminiVoiceAnalyzer {

    private static final String PROMPT = """
            당신은 감정 분석 AI입니다. 사용자의 한국어 음성을 분석하여 JSON으로 응답하세요.

            규칙:
            - transcript: 정확한 한국어 전사
            - summary: 전체 발화의 2-3문장 요약
            - segments: 자연스러운 발화 단위로 분할 (최소 1, 최대 10구간)
              - timestamp: MM:SS 형식 (예: "00:15")
              - text: 해당 구간 전사 텍스트
              - emotions: 상위 5개 감정, intensity는 0.0-1.0
              - category: label에 해당하는 대분류 (neutral/happy/sad/angry/surprised)
              - prosody_notes: 톤/속도/억양 관찰 (한국어 1문장)
            - stability_score: 0(극도로 불안정) ~ 10(매우 안정적). 화자의 전반적 감정 안정성 기준.

            감정 label은 반드시 아래 목록에서만 선택:
            [neutral] calmness, contemplation, concentration, interest, realization, boredom, tiredness, confusion, doubt, nostalgia
            [happy] joy, ecstasy, contentment, satisfaction, amusement, excitement, pride, triumph, relief, admiration, adoration, love, romance, entrancement, aesthetic_appreciation, determination
            [sad] sadness, distress, disappointment, guilt, shame, embarrassment, empathic_pain, sympathy, loneliness
            [angry] anger, contempt, disgust, frustration, envy, craving
            [surprised] surprise_positive, surprise_negative, awe, horror, fear, anxiety, awkwardness
            """;

    private final Optional<Client> geminiClient;
    private final String modelName;
    private final Optional<S3Client> s3Client;
    private final VoiceAdaptor voiceAdaptor;
    private final VoiceCompositeAdaptor voiceCompositeAdaptor;
    private final GeminiEmotionMapper emotionMapper;
    private final String s3Bucket;
    private final ObjectMapper objectMapper;

    public GeminiVoiceAnalyzer(
            Optional<Client> geminiClient,
            @Value("${gemini.model:gemini-2.5-flash}") String modelName,
            Optional<S3Client> s3Client,
            VoiceAdaptor voiceAdaptor,
            VoiceCompositeAdaptor voiceCompositeAdaptor,
            GeminiEmotionMapper emotionMapper,
            @Value("${spring.cloud.aws.s3.bucket:}") String s3Bucket,
            ObjectMapper objectMapper) {
        this.geminiClient = geminiClient;
        this.modelName = modelName;
        this.s3Client = s3Client;
        this.voiceAdaptor = voiceAdaptor;
        this.voiceCompositeAdaptor = voiceCompositeAdaptor;
        this.emotionMapper = emotionMapper;
        this.s3Bucket = s3Bucket;
        this.objectMapper = objectMapper;
    }

    @Async
    public void analyzeAsync(Long voiceId, String voiceKey) {
        if (geminiClient.isEmpty() || s3Client.isEmpty()) {
            log.debug("Gemini or S3 client not configured, skipping analysis for voiceId={}", voiceId);
            return;
        }

        try {
            Voice voice = voiceAdaptor.queryById(voiceId);
            byte[] audioBytes = downloadFromS3(voiceKey);
            String analysisJson = analyzeWithGemini(audioBytes, voiceKey);
            GeminiAnalysisResult result = objectMapper.readValue(analysisJson, GeminiAnalysisResult.class);
            VoiceComposite composite = emotionMapper.toVoiceComposite(result, voice);
            voiceCompositeAdaptor.save(composite);
            voice.markAnalysisCompleted();
            voiceAdaptor.save(voice);
            log.info("Gemini analysis saved for voiceId={}, topEmotion={}", voiceId, composite.getTopEmotion());
        } catch (Exception e) {
            log.error("Gemini analysis failed for voiceId={}, voiceKey={}", voiceId, voiceKey, e);
            try {
                Voice voice = voiceAdaptor.queryById(voiceId);
                voice.markAnalysisFailed();
                voiceAdaptor.save(voice);
            } catch (Exception ex) {
                log.error("Failed to update analysisStatus to FAILED for voiceId={}", voiceId, ex);
            }
        }
    }

    private byte[] downloadFromS3(String voiceKey) {
        ResponseBytes<GetObjectResponse> responseBytes = s3Client.get().getObjectAsBytes(
                GetObjectRequest.builder()
                        .bucket(s3Bucket)
                        .key(voiceKey)
                        .build()
        );
        return responseBytes.asByteArray();
    }

    private String analyzeWithGemini(byte[] audioBytes, String voiceKey) throws Exception {
        Client client = geminiClient.get();
        String mimeType = resolveMimeType(voiceKey);

        Path tempFile = Files.createTempFile("gemini_voice_", extractExtension(voiceKey));
        try {
            Files.write(tempFile, audioBytes);

            com.google.genai.types.File uploaded = client.files.upload(
                    tempFile.toFile(),
                    UploadFileConfig.builder().mimeType(mimeType).build()
            );

            log.debug("Gemini file uploaded: uri={}", uploaded.uri().orElse("(no uri)"));

            GenerateContentResponse response = client.models.generateContent(
                    modelName,
                    Content.builder()
                            .role("user")
                            .parts(List.of(
                                    Part.builder().text(PROMPT).build(),
                                    Part.builder().fileData(
                                            FileData.builder()
                                                    .fileUri(uploaded.uri().orElseThrow())
                                                    .mimeType(mimeType)
                                                    .build()
                                    ).build()
                            ))
                            .build(),
                    GenerateContentConfig.builder()
                            .responseMimeType("application/json")
                            .build()
            );

            String text = response.text();
            if (text == null || text.isBlank()) {
                throw new IllegalStateException("Empty response from Gemini");
            }
            return text;
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    private String resolveMimeType(String voiceKey) {
        String lower = voiceKey.toLowerCase();
        if (lower.endsWith(".wav")) return "audio/wav";
        if (lower.endsWith(".m4a")) return "audio/mp4";
        return "audio/mpeg";
    }

    private String extractExtension(String voiceKey) {
        int dot = voiceKey.lastIndexOf('.');
        return dot >= 0 ? voiceKey.substring(dot) : ".bin";
    }
}
