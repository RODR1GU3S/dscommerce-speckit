package com.devsuperior.dscommerce.config;

import com.devsuperior.dscommerce.services.RegistrationAttemptReference;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Clock;

/** Captures the registration reference before MVC reads or validates the JSON arguments. */
@Configuration(proxyBeanMethods = false)
public class CustomerRegistrationWebConfig implements WebMvcConfigurer {

    private static final String REFERENCE_CAPTURED = CustomerRegistrationWebConfig.class.getName()
            + ".referenceCaptured";

    private final Clock clock;
    private final RegistrationAttemptReference reference;

    public CustomerRegistrationWebConfig(Clock clock, RegistrationAttemptReference reference) {
        this.clock = clock;
        this.reference = reference;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                if (HttpMethod.POST.matches(request.getMethod())
                        && !Boolean.TRUE.equals(request.getAttribute(REFERENCE_CAPTURED))) {
                    reference.initialize(clock.instant());
                    // A redispatch of this request must retain the first instant and UTC civil date.
                    request.setAttribute(REFERENCE_CAPTURED, Boolean.TRUE);
                }
                return true;
            }
        }).addPathPatterns("/customers").order(Ordered.HIGHEST_PRECEDENCE);
    }
}
