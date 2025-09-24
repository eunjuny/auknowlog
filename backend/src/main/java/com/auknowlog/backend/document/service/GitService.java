package com.auknowlog.backend.document.service;

import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
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

    public Mono<String> commitAndPush(String absoluteFilePath, String commitMessage, String remoteName, String remoteBranch) {
        File repoTop = resolveRepoTop();
        String targetRemote = (remoteName == null || remoteName.isBlank()) ? "origin" : remoteName;
        String targetBranch = (remoteBranch == null || remoteBranch.isBlank()) ? "main" : remoteBranch;

        return runGit(List.of("git", "add", absoluteFilePath), repoTop)
                .flatMap(addOut -> runGit(List.of("git", "commit", "-m", commitMessage), repoTop)
                        .onErrorResume(e -> {
                            String msg = e.getMessage() == null ? "" : e.getMessage();
                            if (msg.contains("nothing to commit") || msg.contains("no changes added") || msg.contains("nothing added to commit")) {
                                return Mono.just("nothing to commit");
                            }
                            return Mono.error(e);
                        }))
                .flatMap(commitOut -> determinePrefixPath(absoluteFilePath, repoTop)
                        .flatMap(prefix -> runGit(List.of("git", "subtree", "split", "--prefix=" + prefix, "-b", "tmp-notes-split"), repoTop)
                                .then(runGit(List.of("git", "push", targetRemote, "tmp-notes-split:" + targetBranch, "--force-with-lease"), repoTop))
                                .flatMap(pushOut -> runGit(List.of("git", "branch", "-D", "tmp-notes-split"), repoTop)
                                        .onErrorResume(err -> Mono.empty()))
                                .thenReturn("Git add/commit/subtree-push 완료 → " + targetRemote + "/" + targetBranch))
                        .onErrorResume(err -> Mono.error(new IOException("서브트리 푸시에 실패했습니다: " + err.getMessage())))
                )
                .onErrorResume(e -> Mono.just("Git 저장 실패: " + e.getMessage()));
    }

    private File resolveRepoTop() {
        File cwd = new File(".");
        try {
            String root = runGit(List.of("git", "rev-parse", "--show-toplevel"), cwd).block();
            if (root == null || root.isBlank()) return cwd.getAbsoluteFile();
            return Paths.get(root).toFile();
        } catch (Exception e) {
            return cwd.getAbsoluteFile();
        }
    }

    private Mono<String> determinePrefixPath(String absoluteFilePath, File repoTop) {
        return runGit(List.of("git", "rev-parse", "--show-toplevel"), repoTop)
                .map(rootPath -> {
                    Path root = Paths.get(rootPath).normalize();
                    Path file = Paths.get(absoluteFilePath).normalize();
                    Path parent = file.getParent();
                    if (parent == null) throw new IllegalStateException("Invalid file path: " + absoluteFilePath);
                    Path rel = root.relativize(parent);
                    String prefix = rel.toString().replace(File.separatorChar, '/');
                    if (prefix.isBlank()) throw new IllegalStateException("Cannot compute subtree prefix for: " + absoluteFilePath);
                    return prefix;
                });
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


