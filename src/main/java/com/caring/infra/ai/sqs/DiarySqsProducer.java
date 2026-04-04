package com.caring.infra.ai.sqs;

import com.caring.infra.ai.lambda.dto.DiaryPayload;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnBean(SqsConfig.class)
public class DiarySqsProducer {

    private final SqsTemplate sqsTemplate;
    private final String queueUrl;

    public DiarySqsProducer(
            SqsTemplate sqsTemplate,
            @Value("${sqs.diary-to-chatbot-url}") String queueUrl
    ) {
        this.sqsTemplate = sqsTemplate;
        this.queueUrl = queueUrl;
    }

    /**
     * SQS에 마음일기 메시지를 전송한다.
     *
     * @throws SqsSendException 전송 실패 시. 호출자는 이 예외를 처리하거나 상위로 전파해야 한다.
     *                          {@link com.caring.infra.ai.hume.callback.HumeCallbackController}는
     *                          이 예외가 전파되어야 pendingItems ack를 건너뛰고 재시도를 허용한다.
     */
    public void send(DiaryPayload payload) {
        try {
            sqsTemplate.send(to -> to
                    .queue(queueUrl)
                    .payload(payload)
            );
            log.info("SQS 마음일기 메시지 전송: userId={}, source={}", payload.userId(), payload.source());
        } catch (Exception e) {
            log.error("SQS 마음일기 메시지 전송 실패: userId={}, error={}", payload.userId(), e.getMessage(), e);
            throw new SqsSendException("SQS 전송 실패: userId=" + payload.userId(), e);
        }
    }
}
