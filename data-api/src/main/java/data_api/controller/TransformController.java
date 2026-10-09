package data_api.controller;

import data_api.dto.TransformRequest;
import data_api.dto.TransformResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class TransformController {

    @Value("${internal.security.token}")
    private String expectedToken;

    @PostMapping("/transform")
    public ResponseEntity<?> transformText(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestBody TransformRequest request) {

        if (token == null || !token.equals(expectedToken)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Access Denied: Invalid X-Internal-Token");
        }

        if (request == null || request.getText() == null) {
            return ResponseEntity.badRequest().body("Text cannot be empty");
        }

        String original = request.getText();
        String transformed = new StringBuilder(original).reverse().toString().toUpperCase();

        return ResponseEntity.ok(new TransformResponse(transformed));
    }
}