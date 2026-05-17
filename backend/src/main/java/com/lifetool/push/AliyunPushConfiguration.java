package com.lifetool.push;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AliyunPushProperties.class)
public class AliyunPushConfiguration {
}
