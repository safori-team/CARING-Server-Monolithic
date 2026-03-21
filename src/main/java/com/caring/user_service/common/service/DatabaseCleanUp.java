package com.caring.user_service.common.service;

import com.google.common.base.CaseFormat;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Table;
import jakarta.persistence.metamodel.EntityType;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DatabaseCleanUp implements InitializingBean {

    private final EntityManager entityManager;
    private List<String> tableNames = new ArrayList<>();

    @Override
    public void afterPropertiesSet() throws Exception {
        tableNames = entityManager.getMetamodel().getEntities().stream()
                .filter(entityType -> entityType.getJavaType().getAnnotation(Entity.class) != null)
                .map(this::getTableName)
                .collect(Collectors.toList());
    }

    private String getTableName(EntityType<?> entityType) {
        Class<?> javaType = entityType.getJavaType();
        Table tableAnnotation = javaType.getAnnotation(Table.class);

        // `@Table`이 있으면 테이블 이름을 반환하고, 없으면 클래스 이름을 변환
        if (tableAnnotation != null && !tableAnnotation.name().isEmpty()) {
            return tableAnnotation.name();
        } else {
            // `@Table`이 없을 경우 엔티티 이름을 소문자 언더스코어로 변환
            return CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, entityType.getName());
        }
    }


    @Transactional
    public void truncateAllEntity() {
        entityManager.flush();


        entityManager.createNativeQuery("SET REFERENTIAL_INTEGRITY FALSE").executeUpdate();
        for (String tableName : tableNames) {
            entityManager.createNativeQuery("TRUNCATE TABLE " + tableName).executeUpdate();

        }
        entityManager.createNativeQuery("SET REFERENTIAL_INTEGRITY TRUE").executeUpdate();
    }
}
