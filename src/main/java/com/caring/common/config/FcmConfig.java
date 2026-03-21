package com.caring.common.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class FcmConfig {

    private final Environment env;

    @Bean
    public FirebaseApp firebaseApp() throws IOException {
        byte[] decodedBytes = Base64.getDecoder().decode(env.getProperty("fcm.key.base64"));
        ByteArrayInputStream serviceAccount = new ByteArrayInputStream(decodedBytes);

        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials
                        .fromStream(serviceAccount)
                )
                .build();

        if (FirebaseApp.getApps().isEmpty()) {
            return FirebaseApp.initializeApp(options);
        } else {
            return FirebaseApp.getInstance();
        }
    }
}
