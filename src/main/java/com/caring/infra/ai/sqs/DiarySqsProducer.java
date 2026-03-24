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

    public void send(DiaryPayload payload) {
        sqsTemplate.send(to -> to
                .queue(queueUrl)
                .payload(payload)
        );
        log.info("SQS 마음일기 메시지 전송: userId={}, source={}", payload.userId(), payload.source());
    }
}
