package com.triobase.service.openapi.temporal;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface IntegrationOrchestrationActivities {

    @ActivityMethod
    String loadRelease(String commandJson);

    @ActivityMethod
    String transform(String stepCommandJson);

    @ActivityMethod
    String invokeConnector(String stepCommandJson);

    @ActivityMethod
    String persistExecution(String stateCommandJson);

    @ActivityMethod
    String persistWait(String waitCommandJson);

    @ActivityMethod
    String compensate(String compensationCommandJson);
}
