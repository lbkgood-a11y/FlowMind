package com.triobase.service.openapi.service;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.service.openapi.domain.entity.PolicySnapshot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
@Slf4j @Component
public class PolicySnapshotPublisher {
 private final StringRedisTemplate redis;private final ObjectMapper mapper;
 public PolicySnapshotPublisher(StringRedisTemplate redis,ObjectMapper mapper){this.redis=redis;this.mapper=mapper;}
 public boolean publish(PolicySnapshot snapshot){String tenant=snapshot.getTenantId()==null?"__PLATFORM__":snapshot.getTenantId();String immutable="openapi:policy:snapshot:"+tenant+":"+snapshot.getEnvironment()+":"+snapshot.getSnapshotVersion();String pointer="openapi:policy:current:"+tenant+":"+snapshot.getEnvironment();try{redis.opsForValue().set(immutable,mapper.writeValueAsString(snapshot));redis.opsForValue().set(pointer,String.valueOf(snapshot.getSnapshotVersion()));redis.convertAndSend("openapi:policy:updates",tenant+":"+snapshot.getEnvironment()+":"+snapshot.getSnapshotVersion());return true;}catch(Exception e){log.warn("Policy snapshot distribution failed tenant={} environment={} version={}",tenant,snapshot.getEnvironment(),snapshot.getSnapshotVersion());return false;}}
}
