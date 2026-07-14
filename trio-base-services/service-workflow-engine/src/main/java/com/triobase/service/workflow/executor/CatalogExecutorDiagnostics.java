package com.triobase.service.workflow.executor;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.triobase.service.workflow.entity.BusinessObjectAction;
import com.triobase.service.workflow.entity.BusinessObjectAgentAction;
import com.triobase.service.workflow.mapper.BusinessObjectActionMapper;
import com.triobase.service.workflow.mapper.BusinessObjectAgentActionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.TreeSet;

@Slf4j
@Component
@RequiredArgsConstructor
public class CatalogExecutorDiagnostics implements ApplicationRunner {

    private final BusinessObjectActionMapper actionMapper;
    private final BusinessObjectAgentActionMapper agentActionMapper;
    private final ProcessExecutorRegistry executorRegistry;

    @Override
    public void run(ApplicationArguments args) {
        CatalogExecutorDiagnosticSnapshot snapshot = inspectCatalog();
        if (!snapshot.missingBusinessActionExecutors().isEmpty()) {
            log.warn("Business object catalog has action executorKeys without registered code executors: {}",
                    snapshot.missingBusinessActionExecutors());
        }
        if (!snapshot.missingAgentFollowUpExecutors().isEmpty()) {
            log.warn("Business object catalog has Agent executorKeys without registered code executors: {}",
                    snapshot.missingAgentFollowUpExecutors());
        }
    }

    public CatalogExecutorDiagnosticSnapshot inspectCatalog() {
        List<String> actionExecutorKeys = actionMapper.selectList(
                        new LambdaQueryWrapper<BusinessObjectAction>()
                                .isNotNull(BusinessObjectAction::getExecutorKey))
                .stream()
                .map(BusinessObjectAction::getExecutorKey)
                .filter(StringUtils::hasText)
                .toList();
        List<String> agentExecutorKeys = agentActionMapper.selectList(
                        new LambdaQueryWrapper<BusinessObjectAgentAction>()
                                .isNotNull(BusinessObjectAgentAction::getExecutorKey))
                .stream()
                .map(BusinessObjectAgentAction::getExecutorKey)
                .filter(StringUtils::hasText)
                .toList();

        TreeSet<String> missingBusiness = executorRegistry
                .missingBusinessOrClosureExecutors(actionExecutorKeys);
        TreeSet<String> missingAgent = executorRegistry
                .missingAgentFollowUpExecutors(agentExecutorKeys);
        return new CatalogExecutorDiagnosticSnapshot(missingBusiness, missingAgent);
    }

    public record CatalogExecutorDiagnosticSnapshot(
            TreeSet<String> missingBusinessActionExecutors,
            TreeSet<String> missingAgentFollowUpExecutors) {
    }
}
