package com.triobase.service.openapi.service;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.core.exception.BizException;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
class PolicyRestrictionValidatorTest {
 private final ObjectMapper mapper=new ObjectMapper();private final PolicyRestrictionValidator validator=new PolicyRestrictionValidator();
 @Test void permitsOnlySubsetsAndStricterQuotas()throws Exception{assertThatCode(()->validator.requireSubset(mapper.readTree("[\"read\"]"),mapper.readTree("[\"read\",\"write\"]"),"denied")).doesNotThrowAnyException();assertThatThrownBy(()->validator.requireSubset(mapper.readTree("[\"admin\"]"),mapper.readTree("[\"read\"]"),"denied")).isInstanceOf(BizException.class).hasMessage("denied");assertThatCode(()->validator.requireStricterLimits(mapper.readTree("{\"dailyQuota\":100}"),mapper.readTree("{\"dailyQuota\":1000}"))).doesNotThrowAnyException();assertThatThrownBy(()->validator.requireStricterLimits(mapper.readTree("{\"dailyQuota\":2000}"),mapper.readTree("{\"dailyQuota\":1000}"))).isInstanceOf(BizException.class).hasMessage("OPENAPI_SUBSCRIPTION_OVERRIDE_BROADENS_QUOTA");}
}
