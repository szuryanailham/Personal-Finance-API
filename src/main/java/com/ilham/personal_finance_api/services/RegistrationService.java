package com.ilham.personal_finance_api.services;

import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.ilham.personal_finance_api.config.OtpProperties;
import com.ilham.personal_finance_api.dto.OtpSentResponse;
import com.ilham.personal_finance_api.dto.RegisterUserRequest;
import com.ilham.personal_finance_api.dto.RegisterUserResponse;
import com.ilham.personal_finance_api.dto.ResendOtpRequest;
import com.ilham.personal_finance_api.dto.VerifyOtpRequest;
import com.ilham.personal_finance_api.exception.DataAlreadyExistedException;
import com.ilham.personal_finance_api.exception.InvalidOtpException;
import com.ilham.personal_finance_api.exception.TooManyRequestsException;
import com.ilham.personal_finance_api.repository.PendingRegistration;
import com.ilham.personal_finance_api.repository.RegistrationOtpStore;
import com.ilham.personal_finance_api.repository.UserRepository;
import com.ilham.personal_finance_api.security.BCrypt;

/**
 * Registrasi dua langkah: sign-up mengirim OTP ke email, user baru dibuat
 * setelah OTP diverifikasi. Salah OTP atau terlalu sering minta OTP akan
 * mengunci email tersebut sementara.
 */
@Service
public class RegistrationService {

    private static final Logger log = LoggerFactory.getLogger(RegistrationService.class);

    private final UserRepository userRepository;
    private final UserService userService;
    private final ValidationService validationService;
    private final RegistrationOtpStore otpStore;
    private final OtpGenerator otpGenerator;
    private final OtpMailSender otpMailSender;
    private final OtpProperties properties;

    public RegistrationService(
            UserRepository userRepository,
            UserService userService,
            ValidationService validationService,
            RegistrationOtpStore otpStore,
            OtpGenerator otpGenerator,
            OtpMailSender otpMailSender,
            OtpProperties properties) {
        this.userRepository = userRepository;
        this.userService = userService;
        this.validationService = validationService;
        this.otpStore = otpStore;
        this.otpGenerator = otpGenerator;
        this.otpMailSender = otpMailSender;
        this.properties = properties;
    }

    public OtpSentResponse register(RegisterUserRequest request) {
        validationService.validate(request);
        String email = normalizeEmail(request.getEmail());

        ensureNotLocked(email);
        if (userRepository.existsByEmail(email)) {
            throw new DataAlreadyExistedException("Email is already registered");
        }

        PendingRegistration pending = new PendingRegistration(
                request.getFirstName(),
                request.getLastName(),
                email,
                BCrypt.hashpw(request.getPassword(), BCrypt.gensalt()),
                null);

        return sendOtp(pending);
    }

    public OtpSentResponse resend(ResendOtpRequest request) {
        validationService.validate(request);
        String email = normalizeEmail(request.getEmail());

        ensureNotLocked(email);
        PendingRegistration pending = otpStore.findPending(email)
                .orElseThrow(() -> new InvalidOtpException("No pending registration for this email, please sign up again"));

        return sendOtp(pending);
    }

    public RegisterUserResponse verify(VerifyOtpRequest request) {
        validationService.validate(request);
        String email = normalizeEmail(request.getEmail());

        ensureNotLocked(email);
        PendingRegistration pending = otpStore.findPending(email)
                .orElseThrow(() -> new InvalidOtpException("OTP has expired or registration not found, please sign up again"));

        if (!otpGenerator.matches(request.getOtp(), pending.otpHash())) {
            handleFailedAttempt(email);
        }

        RegisterUserResponse response = userService.createVerifiedUser(pending);
        otpStore.clear(email);
        return response;
    }

    private OtpSentResponse sendOtp(PendingRegistration pending) {
        String email = pending.email();

        long cooldown = otpStore.cooldownRemainingSeconds(email);
        if (cooldown > 0) {
            throw new TooManyRequestsException(
                    "Please wait " + cooldown + " seconds before requesting a new OTP", cooldown);
        }

        long sends = otpStore.incrementSends(email, properties.ttl());
        if (sends > properties.maxSends()) {
            throw lockEmail(email, "Too many OTP requests");
        }

        String otp = otpGenerator.generate();
        try {
            otpMailSender.send(email, pending.firstName(), otp, properties.ttl());
        } catch (MailException e) {
            log.error("Failed to send registration OTP to {}", email, e);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Failed to send OTP email, please try again later");
        }

        otpStore.savePending(pending.withOtpHash(otpGenerator.hash(otp)), properties.ttl());
        otpStore.startCooldown(email, properties.resendCooldown());

        OtpSentResponse response = new OtpSentResponse();
        response.setEmail(email);
        response.setExpiresInSeconds(properties.ttl().toSeconds());
        response.setResendAvailableInSeconds(properties.resendCooldown().toSeconds());
        return response;
    }

    private void handleFailedAttempt(String email) {
        long attempts = otpStore.incrementAttempts(email, properties.ttl());
        if (attempts >= properties.maxAttempts()) {
            throw lockEmail(email, "Too many failed OTP attempts");
        }

        long remaining = properties.maxAttempts() - attempts;
        throw new InvalidOtpException("Invalid OTP. " + remaining + " attempt(s) remaining");
    }

    private TooManyRequestsException lockEmail(String email, String reason) {
        otpStore.lock(email, properties.lockDuration());
        long seconds = properties.lockDuration().toSeconds();
        log.warn("Registration locked for {}: {}", email, reason);
        return new TooManyRequestsException(reason + ". Try again in " + seconds + " seconds", seconds);
    }

    private void ensureNotLocked(String email) {
        long remaining = otpStore.lockRemainingSeconds(email);
        if (remaining > 0) {
            throw new TooManyRequestsException(
                    "Too many attempts. Try again in " + remaining + " seconds", remaining);
        }
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
