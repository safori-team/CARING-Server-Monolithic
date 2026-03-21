package com.caring.user_service.common.config;


import com.caring.user_service.common.service.MemberCodeArgumentResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.env.Environment;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;
import java.util.List;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final Environment env;

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> argumentResolvers) {
        argumentResolvers.add(new MemberCodeArgumentResolver());
    }

    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(new StringToListConverter());
    }

    private static class StringToListConverter implements Converter<String, List<String>> {
        @Override
        public List<String> convert(String source) {
            return Arrays.asList(source.split(","));
        }
    }

    // CORS는 Gateway(WebConfig)에서 전역 처리하므로, 내부 서비스에서는 비활성화
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry
                .addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("*")
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}
