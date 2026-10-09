package auth_api.controller;

import auth_api.dto.ProcessRequest;
import auth_api.dto.ProcessResponse;
import auth_api.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ProcessController {

    private final AuthService authService;

    @PostMapping("/process")
    public ResponseEntity<ProcessResponse> process(Authentication authentication,
                                                   @RequestBody ProcessRequest request) {
        UUID userId = (UUID) authentication.getPrincipal();
        return ResponseEntity.ok(authService.processText(userId, request.getText()));
    }
}