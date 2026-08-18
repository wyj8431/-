package com.example.lowcode.home.api;

import com.example.lowcode.common.api.ApiResponse;
import com.example.lowcode.common.web.TraceIdFilter;
import com.example.lowcode.home.application.HomeQueryService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/home")
public class HomeController {
    private final HomeQueryService homeQueryService;

    public HomeController(HomeQueryService homeQueryService) {
        this.homeQueryService = homeQueryService;
    }

    @GetMapping
    public ApiResponse<HomeQueryService.HomeView> get(HttpServletRequest request) {
        return ApiResponse.success(
            homeQueryService.loadHome(),
            (String) request.getAttribute(TraceIdFilter.TRACE_ID_ATTRIBUTE)
        );
    }
}
