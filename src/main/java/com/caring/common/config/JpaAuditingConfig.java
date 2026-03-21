package com.caring.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * JPA Auditing 활성화 설정
 * BaseTimeEntity의 createdDate, lastModifiedDate 자동 설정을 위해 필요
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}

