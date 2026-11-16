package io.github.elr.blog.common.web;

import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.UUID;

/**
 * Fills {@link CurrentUser} parameters with the logged-in user's ID.
 * <p>
 * Spring MVC asks each registered resolver whether it supports a parameter, and calls
 * {@link #resolveArgument} to produce its value before the controller method runs.
 */
class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUser.class) && parameter.getParameterType() == UUID.class;
    }

    @Override
    public UUID resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
            // The security configuration lets no anonymous request reach such an endpoint,
            // so getting here means an endpoint was left public by mistake.
            throw new IllegalStateException("@CurrentUser used on an endpoint that does not require a token: "
                    + parameter.getExecutable());
        }
        return UUID.fromString(authentication.getName());
    }
}
