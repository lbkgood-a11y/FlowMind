package com.triobase.service.openapi.service;
import com.triobase.service.openapi.domain.enums.Environment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
@Slf4j @Component
public class PolicyChangeNotifier {
 private final PolicySnapshotService snapshotService;
 public PolicyChangeNotifier(PolicySnapshotService snapshotService){this.snapshotService=snapshotService;}
 public void publishAfterCommit(String tenantId,Environment environment){Runnable publish=()->{try{snapshotService.publish(tenantId,environment);}catch(Exception e){log.error("Access policy publication failed after state change tenant={} environment={}",tenantId,environment);}};if(!TransactionSynchronizationManager.isSynchronizationActive()){publish.run();return;}TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){@Override public void afterCommit(){publish.run();}});}
}
