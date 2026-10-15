package com.ilham.personal_finance_api.services;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import com.ilham.personal_finance_api.entity.User;
import com.ilham.personal_finance_api.exception.DataAlreadyExistedException;
import com.ilham.personal_finance_api.dto.RegisterUserResponse;
import com.ilham.personal_finance_api.dto.UserResponse;
import com.ilham.personal_finance_api.repository.PendingRegistration;
import com.ilham.personal_finance_api.repository.UserRepository;

import jakarta.transaction.Transactional;

@Service
public class UserService {
@Autowired
private UserRepository userRepository;


// Dipanggil setelah OTP registrasi terverifikasi; password sudah di-hash saat sign-up
@Transactional
public RegisterUserResponse createVerifiedUser(PendingRegistration pending) {

    if (userRepository.existsByEmail(pending.email())) {
        throw new DataAlreadyExistedException("Email is already registered");
    }

    User user = new User();
    user.setFirstName(pending.firstName());
    user.setLastName(pending.lastName());
    user.setEmail(pending.email());
    user.setPassword(pending.passwordHash());

    try {
        userRepository.saveAndFlush(user);
    } catch (DataIntegrityViolationException e) {
        // Race condition: email yang sama terdaftar di antara pengecekan dan insert
        throw new DataAlreadyExistedException("Email is already registered");
    }

     RegisterUserResponse response = new RegisterUserResponse();
     response.setFirstName(user.getFirstName());
     response.setLastName(user.getLastName());
     response.setEmail(user.getEmail());
     return response;

}

public UserResponse get(User user) {
    UserResponse response = new UserResponse();
    response.setId(user.getId());
    response.setFirstName(user.getFirstName());
    response.setLastName(user.getLastName());
    response.setEmail(user.getEmail());
    return response;
}


}
