package com.auknowlog.backend.document.service;

import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
public class GitService {

    public Mono<String> commitAndPush(String absoluteFilePath, String commitMessage) {
        File workingDir = new File("."); // 현재 프로세스 디렉터리(서브디렉터리여도 Git 명령 동작)

        return runGit(List.of("git", "add", absoluteFilePath), workingDir)
                .flatMap(addOut -> runGit(List.of("git", "commit", "-m", commitMessage), workingDir)
                        .onErrorResume(e -> {
                            String msg = e.getMessage() == null ? "" : e.getMessage();
                            if (msg.contains("nothing to commit") || msg.contains("no changes added") || msg.contains("nothing added to commit")) {
                                return Mono.just("nothing to commit");
                            }
                            return Mono.error(e);
                        }))
                .flatMap(commitOut -> runGit(List.of("git", "push"), workingDir))
                .map(pushOut -> "Git add/commit/push 완료")
                .onErrorResume(e -> Mono.just("Git 저장 실패: " + e.getMessage()));
    }

    private Mono<String> runGit(List<String> command, File workingDir) {
        return Mono.fromCallable(() -> {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(workingDir);
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (InputStream is = proc.getInputStream()) {
                is.transferTo(baos);
            }
            int exit = proc.waitFor();
            String out = baos.toString(StandardCharsets.UTF_8);
            if (exit != 0) {
                throw new IOException("Command failed: " + String.join(" ", command) + " -> " + out.trim());
            }
            return out.trim();
        }).subscribeOn(Schedulers.boundedElastic());
    }
}


