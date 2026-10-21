package iuh.fit.paymentservice.presentation.controller.internal;

import iuh.fit.paymentservice.application.service.AccountDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** auth-service hỏi trước khi cho người dùng xóa tài khoản. */
@RestController
@RequestMapping("/internal/users")
@RequiredArgsConstructor
public class AccountDataInternalController {
    private final AccountDataService accountDataService;

    @GetMapping("/{userId}/deletion-blockers")
    public List<String> deletionBlockers(@PathVariable UUID userId) {
        return accountDataService.deletionBlockers(userId);
    }
}
