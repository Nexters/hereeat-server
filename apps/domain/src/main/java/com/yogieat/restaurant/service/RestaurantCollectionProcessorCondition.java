package com.yogieat.restaurant.service;

import lombok.NonNull;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

public class RestaurantCollectionProcessorCondition implements Condition {

    private static final String PROPERTY_NAME = "restaurant.collection.processor.enabled";
    private static final String TRUE = "true";

    @Override
    public boolean matches(ConditionContext context, @NonNull AnnotatedTypeMetadata metadata) {
        String enabled = context.getEnvironment().getProperty(PROPERTY_NAME, TRUE);
        return TRUE.equalsIgnoreCase(enabled);
    }
}
