package com.ilham.personal_finance_api.services;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.ilham.personal_finance_api.entity.User;
import com.ilham.personal_finance_api.dto.GoogleLoginRequest;
import com.ilham.personal_finance_api.dto.LoginUserRequest;
import com.ilham.personal_finance_api.dto.TokenResponse;
import com.ilham.personal_finance_api.repository.UserRepository;
import com.ilham.personal_finance_api.security.BCrypt;
import com.ilham.personal_finance_api.security.GoogleTokenVerifier;
import com.ilham.personal_finance_api.security.GoogleUserInfo;

import jakarta.transaction.Transactional;


@Service
public class AuthService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ValidationService validationService;

    @Autowired
    private GoogleTokenVerifier googleTokenVerifier;

    @Transactional
    public TokenResponse login(LoginUserRequest request) {
        validationService.validate(request);

        User user = userRepository.findByEmail(request.getEmail())
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.UNAUTHORIZED,
            "Username or password wrong"
        ));

        if(BCrypt.checkpw(request.getPassword(), user.getPassword())) {
            return issueToken(user);
        } else {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Username or Password Wrong");
        }
    }

    // Email Google sudah terverifikasi, jadi akun dengan email yang sama langsung ditautkan
    @Transactional
    public TokenResponse loginWithGoogle(GoogleLoginRequest request) {
        validationService.validate(request);

        GoogleUserInfo googleUser = googleTokenVerifier.verify(request.getIdToken());

        User user = userRepository.findByEmail(googleUser.email())
                .orElseGet(() -> createGoogleUser(googleUser));

        return issueToken(user);
    }

    private User createGoogleUser(GoogleUserInfo googleUser) {
        User user = new User();
        user.setFirstName(googleUser.firstName());
        user.setLastName(googleUser.lastName());
        user.setEmail(googleUser.email());
        // Kolom password wajib diisi; hash acak membuat login password tidak bisa dipakai
        user.setPassword(BCrypt.hashpw(UUID.randomUUID().toString(), BCrypt.gensalt()));

        try {
            return userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // Race condition: email yang sama terdaftar di antara pengecekan dan insert
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already registered, please retry");
        }
    }

    private TokenResponse issueToken(User user) {
        user.setToken(UUID.randomUUID().toString());
        user.setTokenExpiredAt(next30Days());
        userRepository.save(user);

        TokenResponse tokenResponse = new TokenResponse();
        tokenResponse.setToken(user.getToken());
        tokenResponse.setExpiredAt(user.getTokenExpiredAt());
        return tokenResponse;
    }

    private Long next30Days() {
        return System.currentTimeMillis() + (1000 *16*24*30);
    }


}
