package com.caring.api.fcm.service;

import com.google.firebase.messaging.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class FcmService {

    /**
     * 단일 토큰으로 푸시 알림 전송
     */
    public String sendMessage(String token, String title, String body) {
        try {
            Message message = Message.builder()
                    .setToken(token)
                    .setNotification(Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build())
                    .build();

            String response = FirebaseMessaging.getInstance().send(message);
            log.info("FCM 메시지 전송 성공: {}", response);
            return response;
        } catch (FirebaseMessagingException e) {
            log.error("FCM 메시지 전송 실패: {}", e.getMessage());
            throw new RuntimeException("FCM 메시지 전송 실패", e);
        }
    }

    /**
     * 여러 토큰으로 푸시 알림 전송 (멀티캐스트)
     * - sendMulticast() -> sendEachForMulticast() 로 변경
     * - 각 토큰별 전송 결과에 대한 상세 오류 처리 로직 추가
     */
    public BatchResponse sendMulticastMessage(List<String> tokens, String title, String body) {
        // 토큰 리스트가 비어있으면 아무 작업도 하지 않고 즉시 반환
        if (tokens == null || tokens.isEmpty()) {
            log.info("전송할 FCM 토큰이 없어 멀티캐스트 메시지를 생략합니다.");
            return null;
        }

        try {
            MulticastMessage message = MulticastMessage.builder()
                    .addAllTokens(tokens)
                    .setNotification(Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build())
                    .build();

            // 더 이상 사용하지 않는 sendMulticast 대신 sendEachForMulticast 사용
            BatchResponse response = FirebaseMessaging.getInstance().sendEachForMulticast(message);

            // 실패한 토큰에 대한 상세 처리 로직
            if (response.getFailureCount() > 0) {
                List<SendResponse> responses = response.getResponses();
                List<String> failedTokens = new ArrayList<>();

                for (int i = 0; i < responses.size(); i++) {
                    if (!responses.get(i).isSuccessful()) {
                        String failedToken = tokens.get(i);
                        failedTokens.add(failedToken);

                        // 실패 원인 로깅
                        FirebaseMessagingException e = responses.get(i).getException();
                        log.error("FCM 전송 실패 - Token: {}, ErrorCode: {}, Message: {}",
                                failedToken, e.getMessagingErrorCode(), e.getMessage());
                    }
                }
                log.warn("총 {}개의 FCM 전송 중 {}개 실패. 실패 토큰: {}", tokens.size(), failedTokens.size(), failedTokens);
            }

            log.info("FCM 멀티캐스트 메시지 전송 완료 - 성공: {}, 실패: {}",
                    response.getSuccessCount(), response.getFailureCount());

            return response;
        } catch (FirebaseMessagingException e) {
            log.error("FCM 멀티캐스트 메시지 전송 중 심각한 오류 발생: {}", e.getMessage());
            throw new RuntimeException("FCM 멀티캐스트 메시지 전송 실패", e);
        }
    }
}
