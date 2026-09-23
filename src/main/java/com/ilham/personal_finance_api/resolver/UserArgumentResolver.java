package com.ilham.personal_finance_api.resolver;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.server.ResponseStatusException;
import com.ilham.personal_finance_api.entity.User;
import com.ilham.personal_finance_api.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;


@Component 
public class UserArgumentResolver implements  HandlerMethodArgumentResolver {

    @Autowired 
    private UserRepository userRepository;


   @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return User.class.equals(parameter.getParameterType());
    }

    @Override 
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer, NativeWebRequest webRequest , WebDataBinderFactory binderFactory) {
       HttpServletRequest servletRequest =(HttpServletRequest) webRequest.getNativeRequest();
       String authorizationHeader = servletRequest.getHeader("Authorization");
       if(authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
       }

       String token = authorizationHeader.substring("Bearer ".length()).trim();
       if(token.isBlank()) {
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
       }

      User user = userRepository
                .findFirstByToken(token)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Unauthorized"
                ));
        return user;
    }
}
