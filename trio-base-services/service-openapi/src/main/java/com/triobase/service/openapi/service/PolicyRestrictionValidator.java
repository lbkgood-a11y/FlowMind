package com.triobase.service.openapi.service;
import com.fasterxml.jackson.databind.JsonNode;
import com.triobase.common.core.exception.BizException;
import org.springframework.stereotype.Component;
import java.util.HashSet;
import java.util.Set;
@Component
public class PolicyRestrictionValidator {
 private static final Set<String> LIMITS=Set.of("ratePerSecond","burst","dailyQuota","maxBodyBytes","maxConcurrency","maxActiveWorkflows","callbackPerMinute");
 public void requireSubset(JsonNode requested,JsonNode allowed,String message){Set<String> a=textSet(allowed);if(!a.containsAll(textSet(requested)))throw new BizException(40980,message);}
 public void requireStricterLimits(JsonNode override,JsonNode base){if(override==null||override.isNull())return;if(!override.isObject())invalid();override.fields().forEachRemaining(e->{if(!LIMITS.contains(e.getKey())||!e.getValue().canConvertToLong()||e.getValue().asLong()<=0)invalid();JsonNode parent=base==null?null:base.get(e.getKey());if(parent!=null&&parent.canConvertToLong()&&e.getValue().asLong()>parent.asLong())throw new BizException(40980,"OPENAPI_SUBSCRIPTION_OVERRIDE_BROADENS_QUOTA");});}
 public Set<String> textSet(JsonNode array){Set<String>s=new HashSet<>();if(array!=null&&array.isArray())array.forEach(v->{if(v.isTextual())s.add(v.asText());else invalid();});else if(array!=null&&!array.isNull())invalid();return s;}
 private void invalid(){throw new BizException(40080,"OPENAPI_POLICY_RESTRICTION_INVALID");}
}
