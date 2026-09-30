package com.auknowlog.backend.notification;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/account/notifications")
public class LearningNotificationController {
    private final LearningNotificationService service;
    public LearningNotificationController(LearningNotificationService service) { this.service=service; }
    public record Settings(@Email @Size(max=254) String email, boolean reviewEnabled, boolean dailyEnabled, @Min(0) @Max(23) int sendHour) {}
    public record Verification(@Size(min=8,max=8) String code) {}
    @GetMapping public Map<String,Object> settings() { return service.settings(); }
    @PutMapping public Map<String,Object> save(@Valid @RequestBody Settings value) {
        return service.save(value.email(),value.reviewEnabled(),value.dailyEnabled(),value.sendHour());
    }
    @PostMapping("/verification") public Map<String,Object> requestVerification() { service.requestVerification(); return Map.of("requested",true); }
    @PostMapping("/verify") public Map<String,Object> verify(@Valid @RequestBody Verification value) { service.verify(value.code()); return Map.of("verified",true); }
    @GetMapping("/history") public Map<String,Object> history(@RequestParam(defaultValue="0") int page) { return service.history(page); }
    @PostMapping("/send-now") public Map<String,Object> sendNow() { return Map.of("queued",service.enqueueMine()); }
    @PostMapping("/{id}/retry") public Map<String,Object> retry(@PathVariable Long id) { service.retryMine(id); return Map.of("queued",true); }
}
