package com.triobase.service.workflow.config;

import com.triobase.service.workflow.workflow.ProcessActivityImpl;
import com.triobase.service.workflow.workflow.ProcessWorkflowImpl;
import io.temporal.spring.boot.ActivityImpl;
import io.temporal.spring.boot.WorkflowImpl;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.PropertySourcesPlaceholdersResolver;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class WorkflowConfigurationBindingTest {

    @Test
    void applicationConfigurationBindsTemporalQueueAndWorkflowIntegrationProperties() throws IOException {
        MutablePropertySources propertySources = new MutablePropertySources();
        List<PropertySource<?>> loaded = new YamlPropertySourceLoader()
                .load("workflow-application", new ClassPathResource("application.yml"));
        loaded.forEach(propertySources::addLast);

        Binder binder = new Binder(
                ConfigurationPropertySources.from(propertySources),
                new PropertySourcesPlaceholdersResolver(propertySources));
        String applicationName = binder.bind("spring.application.name", String.class)
                .orElseThrow(IllegalStateException::new);
        WorkflowIntegrationProperties workflow = binder.bind(
                        "workflow", Bindable.of(WorkflowIntegrationProperties.class))
                .orElseThrow(IllegalStateException::new);
        WorkflowImpl workflowImpl = ProcessWorkflowImpl.class.getAnnotation(WorkflowImpl.class);
        ActivityImpl activityImpl = ProcessActivityImpl.class.getAnnotation(ActivityImpl.class);

        assertNotNull(workflowImpl);
        assertNotNull(activityImpl);
        assertArrayEquals(new String[]{applicationName}, workflowImpl.taskQueues());
        assertArrayEquals(new String[]{applicationName}, activityImpl.taskQueues());
        assertEquals(applicationName, workflow.getInternal().getServiceName());
        assertEquals("http://localhost:8081", workflow.getServices().getAuthUrl());
        assertEquals(Duration.ofMillis(200), workflow.getParticipants().getConnectTimeout());
        assertEquals(Duration.ofMillis(400), workflow.getParticipants().getReadTimeout());
        assertEquals(200, workflow.getParticipants().getMaxCandidates());
    }
}
