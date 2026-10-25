package com.chasehorse.erp.security;

import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import com.chasehorse.erp.common.ApiException;

@Component
public class CurrentActorResolver implements HandlerMethodArgumentResolver {
    private static final String ATTR = CurrentActorResolver.class.getName() + ".actor";
    private final ActorService actors;

    public CurrentActorResolver(ActorService actors) {
        this.actors = actors;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentActor.class) && Actor.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        Object cached = webRequest.getAttribute(ATTR, RequestAttributes.SCOPE_REQUEST);
        if (cached instanceof Actor a) {
            return a;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth instanceof JwtAuthenticationToken jwtAuth)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "unauthorized", "Authentication required.");
        }
        Actor actor = actors.load(jwtAuth.getToken());
        webRequest.setAttribute(ATTR, actor, RequestAttributes.SCOPE_REQUEST);
        return actor;
    }
}
