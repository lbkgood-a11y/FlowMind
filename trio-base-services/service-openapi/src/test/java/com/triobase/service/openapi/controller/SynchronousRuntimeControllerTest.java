package com.triobase.service.openapi.controller;
import com.triobase.service.openapi.service.SynchronousInvocationService;
import com.triobase.service.openapi.service.RuntimeAdmissionContextResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.bind.annotation.RequestMapping;
import java.lang.reflect.Method;
import java.util.Arrays;
import static org.assertj.core.api.Assertions.assertThat;
@ExtendWith(MockitoExtension.class)
class SynchronousRuntimeControllerTest {
 @Mock SynchronousInvocationService service;
 @Mock RuntimeAdmissionContextResolver resolver;
 @Test void runtimeContractAcceptsOnlyRouteKeyAndNeverRuntimeTargetUrl(){assertThat(new SynchronousRuntimeController(service,resolver)).isNotNull();Method invoke=Arrays.stream(SynchronousRuntimeController.class.getDeclaredMethods()).filter(m->m.isAnnotationPresent(RequestMapping.class)).findFirst().orElseThrow();assertThat(Arrays.stream(invoke.getParameters()).map(p->p.getName().toLowerCase()).toList()).noneMatch(name->name.contains("url")||name.contains("endpoint")||name.contains("secret"));}
}
