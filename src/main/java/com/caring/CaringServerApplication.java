package com.caring;

import io.awspring.cloud.autoconfigure.core.AwsAutoConfiguration;
import io.awspring.cloud.autoconfigure.core.CredentialsProviderAutoConfiguration;
import io.awspring.cloud.autoconfigure.core.RegionProviderAutoConfiguration;
import io.awspring.cloud.autoconfigure.sqs.SqsAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(exclude = {
        SqsAutoConfiguration.class,
        AwsAutoConfiguration.class,
        CredentialsProviderAutoConfiguration.class,
        RegionProviderAutoConfiguration.class
})
@EnableScheduling
public class CaringServerApplication {

	public static void main(String[] args) {
		SpringApplication.run(CaringServerApplication.class, args);
	}

}
