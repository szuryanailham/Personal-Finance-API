package com.ilham.personal_finance_api.services;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.ilham.personal_finance_api.entity.User;
import com.ilham.personal_finance_api.model.LoginUserRequest;
import com.ilham.personal_finance_api.model.TokenResponse;
import com.ilham.personal_finance_api.repository.UserRepository;
import com.ilham.personal_finance_api.security.BCrypt;

import jakarta.transaction.Transactional;


@Service 
public class AuthService {

    @Autowired 
    private UserRepository userRepository;

    @Autowired 
    private ValidationService validationService;

    @Transactional 
    public TokenResponse login(LoginUserRequest request) {
        validationService.validate(request);

        User user = userRepository.findByEmail(request.getEmail())
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.UNAUTHORIZED,
            "Username or password wrong"
        ));

        if(BCrypt.checkpw(request.getPassword(), user.getPassword())) {
            user.setToken(UUID.randomUUID().toString());
            user.setTokenExpiredAt(next30Days());
            userRepository.save(user);

            return TokenResponse.builder().token(user.getToken()).expiredAt(user.getTokenExpiredAt()).build();
        } else {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Username or Password Wrong");
        }
    }

    private Long next30Days() {
        return System.currentTimeMillis() + (1000 *16*24*30);
    }


}
