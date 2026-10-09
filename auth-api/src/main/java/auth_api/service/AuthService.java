package auth_api.service;

import auth_api.dto.*;
import auth_api.entity.ProcessingLog;
import auth_api.entity.User;
import auth_api.repository.ProcessingLogRepository;
import auth_api.repository.UserRepository;
import auth_api.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final ProcessingLogRepository processingLogRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RestTemplate restTemplate;

    @Value("${internal.data-api.url}")
    private String dataApiUrl;

    @Value("${internal.security.token}")
    private String internalToken;

    // Регистрация нового пользователя
    public void register(RegisterRequest request) {
        if (isBlank(request.getEmail()) || isBlank(request.getPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email and password are required");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already in use");
        }

        User user = User.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .build();

        userRepository.save(user);
    }

    // Логин: проверяем пароль и выдаём JWT
    public AuthResponse login(LoginRequest request) {
        if (isBlank(request.getEmail()) || isBlank(request.getPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email and password are required");
        }

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }

        String token = jwtTokenProvider.generateToken(user.getId(), user.getEmail());
        return new AuthResponse(token);
    }

    // Отправляем текст в data-api, сохраняем запись в БД, возвращаем результат
    public ProcessResponse processText(UUID userId, String text) {
        if (isBlank(text)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Text cannot be empty");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Token", internalToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, String>> entity = new HttpEntity<>(Map.of("text", text), headers);

        ResponseEntity<TransformResponse> response;
        try {
            response = restTemplate.exchange(dataApiUrl, HttpMethod.POST, entity, TransformResponse.class);
        } catch (RestClientResponseException e) {
            log.error("data-api returned {}: {}", e.getStatusCode().value(), e.getResponseBodyAsString());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Processing service rejected the request");
        } catch (RestClientException e) {
            log.error("data-api is unavailable: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Processing service is unavailable");
        }

        if (response.getBody() == null || response.getBody().getResult() == null) {
            log.error("data-api returned an empty body");
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Processing service returned an empty response");
        }

        String result = response.getBody().getResult();

        ProcessingLog processingLog = ProcessingLog.builder()
                .userId(userId)
                .inputText(text)
                .outputText(result)
                .build();

        processingLogRepository.save(processingLog);

        return new ProcessResponse(result);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}