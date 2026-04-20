package com.stagepass.admin.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@EntityScan(basePackages = "com.stagepass.domain")
@EnableJpaRepositories(basePackages = "com.stagepass.domain")
public class JpaConfig {
}
