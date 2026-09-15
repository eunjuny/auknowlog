package com.auknowlog.backend.notification;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * `remote-access.sh quick-tunnel-email`만 localhost로 직접 호출하는 운영용 API다.
 * 수신자는 요청값이 아닌 로컬 비밀 설정으로 고정한다.
 */
@RestController
@RequestMapping("/api/notifications/remote-access")
public class RemoteAccessMailController {

    private final RemoteAccessMailService remoteAccessMailService;

    public RemoteAccessMailController(RemoteAccessMailService remoteAccessMailService) {
        this.remoteAccessMailService = remoteAccessMailService;
    }

    @PostMapping("/email")
    public RemoteAccessMailResponse send(@Valid @RequestBody RemoteAccessMailRequest request) {
        return remoteAccessMailService.send(request);
    }
}
