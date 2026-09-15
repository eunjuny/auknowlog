package com.auknowlog.backend.notification;

import java.time.OffsetDateTime;

/** SMTP 서버가 발송 요청을 수락한 시각만 반환한다. 수신 주소와 인증 정보는 응답에 포함하지 않는다. */
public record RemoteAccessMailResponse(OffsetDateTime acceptedAt) {
}
