package com.example.lowcode.auth.api;

import com.example.lowcode.auth.application.AuthService;
import com.example.lowcode.common.api.ApiResponse;
import com.example.lowcode.common.web.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ApiResponse<AuthService.LoginResult> login(
        @Valid @RequestBody LoginRequest request,
        HttpServletRequest servletRequest
    ) {
        AuthService.LoginResult result = authService.login(
            new AuthService.LoginCommand(request.phone(), request.verificationCode())
        );
        return ApiResponse.success(result, (String) servletRequest.getAttribute(TraceIdFilter.TRACE_ID_ATTRIBUTE));
    }

    public record LoginRequest(@NotBlank String phone, @NotBlank String verificationCode) {
    }
}
