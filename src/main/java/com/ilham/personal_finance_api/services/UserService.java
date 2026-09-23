package com.ilham.personal_finance_api.services;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ilham.personal_finance_api.entity.User;
import com.ilham.personal_finance_api.exception.DataAlreadyExistedException;
import com.ilham.personal_finance_api.dto.RegisterUserRequest;
import com.ilham.personal_finance_api.dto.RegisterUserResponse;
import com.ilham.personal_finance_api.repository.UserRepository;
import com.ilham.personal_finance_api.security.BCrypt;

import jakarta.transaction.Transactional;

@Service 
public class UserService {
@Autowired
private UserRepository userRepository;

@Autowired
private ValidationService validationService;


@Transactional
public RegisterUserResponse register(RegisterUserRequest request) {

    validationService.validate(request);

    if (userRepository.existsByEmail(request.getEmail())) {
        throw new DataAlreadyExistedException("Email is already registered");
    }

    User user = new User();
    user.setFirstName(request.getFirstName());
    user.setLastName(request.getLastName());
    user.setEmail(request.getEmail());
    user.setPassword(BCrypt.hashpw(request.getPassword(), BCrypt.gensalt()));
    userRepository.save(user);

     return RegisterUserResponse.builder()
            .firstName(user.getFirstName())
            .lastName(user.getLastName())
            .email(user.getEmail())
            .build();
    
}


}
