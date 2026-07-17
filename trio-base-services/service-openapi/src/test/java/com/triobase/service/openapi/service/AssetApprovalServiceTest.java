package com.triobase.service.openapi.service;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.core.context.SecurityContextHolder;
import com.triobase.common.core.exception.BizException;
import com.triobase.service.openapi.domain.entity.AssetApproval;
import com.triobase.service.openapi.domain.enums.ApprovalDecision;
import com.triobase.service.openapi.infrastructure.mapper.AssetApprovalMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
@ExtendWith(MockitoExtension.class)
class AssetApprovalServiceTest {
 @Mock AssetApprovalMapper mapper;@Mock IntegrationAuditService audit;AssetApprovalService service;
 @BeforeEach void setUp(){service=new AssetApprovalService(mapper,audit);context("submitter");}@AfterEach void clear(){SecurityContextHolder.clear();}
 @Test void rejectsSelfApproval(){AssetApproval a=approval("ASSET_OWNER","submitter",ApprovalDecision.PENDING);when(mapper.selectById("a1")).thenReturn(a);assertThatThrownBy(()->service.decide("a1",true,new ObjectMapper().createObjectNode())).isInstanceOf(BizException.class).hasMessage("OPENAPI_SELF_APPROVAL_FORBIDDEN");}
 @Test void requiresDistinctActorsForDualApproval(){when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of(approval("ASSET_OWNER","same",ApprovalDecision.APPROVED),approval("PLATFORM_ADMIN","same",ApprovalDecision.APPROVED)));assertThatThrownBy(()->service.requireApproved("PRODUCT","p1",Set.of("ASSET_OWNER","PLATFORM_ADMIN"))).isInstanceOf(BizException.class);when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of(approval("ASSET_OWNER","owner",ApprovalDecision.APPROVED),approval("PLATFORM_ADMIN","admin",ApprovalDecision.APPROVED)));assertThatCode(()->service.requireApproved("PRODUCT","p1",Set.of("ASSET_OWNER","PLATFORM_ADMIN"))).doesNotThrowAnyException();}
 private AssetApproval approval(String role,String actor,ApprovalDecision d){AssetApproval a=new AssetApproval();a.setId("a1");a.setApprovalRole(role);a.setSubmittedBy("submitter");a.setDecidedBy(actor);a.setDecision(d);return a;}private void context(String user){SecurityContextHolder.set(new SecurityContextHolder.SecurityContext(user,"owner","tenant-a",List.of(),List.of(),1L,1L,1L));}
}
