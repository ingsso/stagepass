package com.stagepass.admin.config;

import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.context.annotation.Configuration;

@Configuration
@AutoConfigurationPackage(basePackages = "com.stagepass.domain")
public class JpaConfig {
}
