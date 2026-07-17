package com.triobase.service.openapi.service;
import com.triobase.common.core.exception.BizException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import java.util.List;
@Service
public class RuntimeBudgetService {
 private static final DefaultRedisScript<Long> ACQUIRE=new DefaultRedisScript<>("""
 local current=redis.call('INCR',KEYS[1]); if current==1 then redis.call('EXPIRE',KEYS[1],ARGV[2]); end;
 if current>tonumber(ARGV[1]) then redis.call('DECR',KEYS[1]); return 0; end; return current;
 """,Long.class);
 private static final DefaultRedisScript<Long> RELEASE=new DefaultRedisScript<>("""
 local current=tonumber(redis.call('GET',KEYS[1]) or '0'); if current<=1 then redis.call('DEL',KEYS[1]); return 0; end; return redis.call('DECR',KEYS[1]);
 """,Long.class);
 private final StringRedisTemplate redis;
 public RuntimeBudgetService(StringRedisTemplate redis){this.redis=redis;}
 public BudgetLease acquireRequest(String tenant,String client,String route,long limit){return acquire("openapi:concurrency:"+tenant+":"+client+":"+route,limit,300);}
 public BudgetLease acquireWorkflow(String tenant,String client,String route,long limit){return acquire("openapi:workflows:"+tenant+":"+client+":"+route,limit,86400);}
 public void reserveWorkflow(String tenant,String client,String route,long limit){acquire("openapi:workflows:"+tenant+":"+client+":"+route,limit,86400).detach();}
 public void releaseWorkflow(String tenant,String client,String route){release("openapi:workflows:"+tenant+":"+client+":"+route);}
 public void consumeCallback(String tenant,String client,String callbackKey,long perMinute){String key="openapi:callback-budget:"+tenant+":"+client+":"+callbackKey+":"+(System.currentTimeMillis()/60000);try{Long count=redis.opsForValue().increment(key);if(count!=null&&count==1)redis.expire(key,java.time.Duration.ofMinutes(2));if(count==null||count>perMinute)throw new BizException(42901,"OPENAPI_CALLBACK_BUDGET_EXHAUSTED");}catch(BizException e){throw e;}catch(Exception e){throw new BizException(50393,"OPENAPI_RUNTIME_BUDGET_UNAVAILABLE");}}
 private BudgetLease acquire(String key,long limit,long ttl){if(limit<=0)throw new BizException(42901,"OPENAPI_RUNTIME_CONCURRENCY_EXHAUSTED");try{Long result=redis.execute(ACQUIRE,List.of(key),Long.toString(limit),Long.toString(ttl));if(result==null||result==0)throw new BizException(42901,"OPENAPI_RUNTIME_CONCURRENCY_EXHAUSTED");return new BudgetLease(key,this);}catch(BizException e){throw e;}catch(Exception e){throw new BizException(50393,"OPENAPI_RUNTIME_BUDGET_UNAVAILABLE");}}
 private void release(String key){try{redis.execute(RELEASE,List.of(key));}catch(Exception ignored){}}
 public static final class BudgetLease implements AutoCloseable{private final String key;private final RuntimeBudgetService owner;private boolean closed;private boolean detached;private BudgetLease(String key,RuntimeBudgetService owner){this.key=key;this.owner=owner;}private void detach(){detached=true;}@Override public void close(){if(!closed&&!detached){closed=true;owner.release(key);}}}
}
