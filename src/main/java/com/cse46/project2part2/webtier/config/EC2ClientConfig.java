package com.cse46.project2part2.webtier.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;

@Configuration
public class EC2ClientConfig {

    @Value("${aws.region}")
    private String region;

    @Bean
    public Ec2Client ec2Client() {
        // Configure the EC2 client with region and other settings if needed.
        return Ec2Client.builder()
                .region(Region.of(region))
                .build();
    }
}
