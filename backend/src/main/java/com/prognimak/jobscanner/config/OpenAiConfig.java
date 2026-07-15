package com.prognimak.jobscanner.config;

import org.springframework.ai.model.openai.autoconfigure.OpenAiChatProperties;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenAiConfig {

    @Bean
    static BeanPostProcessor openAiChatTemperatureSanitizer() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
                if (bean instanceof OpenAiChatProperties properties) {
                    properties.setTemperature(null);
                }
                return bean;
            }
        };
    }
}
