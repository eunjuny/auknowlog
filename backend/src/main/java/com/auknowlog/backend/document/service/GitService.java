package com.auknowlog.backend.document.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

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

    private static final Logger log = LoggerFactory.getLogger(GitService.class);

    public String commitAndPush(String absoluteFilePath, String commitMessage) {
        File workingDir = new File(".");

        try {
            runGit(List.of("git", "add", absoluteFilePath), workingDir);
            
            try {
                runGit(List.of("git", "commit", "-m", commitMessage), workingDir);
            } catch (IOException e) {
                String msg = e.getMessage() == null ? "" : e.getMessage();
                if (!msg.contains("nothing to commit") && !msg.contains("no changes added")) {
                    throw e;
                }
            }
            
            runGit(List.of("git", "push"), workingDir);
            return "Git add/commit/push 완료";
        } catch (Exception e) {
            return "Git 저장 실패: " + e.getMessage();
        }
    }

    public String commitAndPush(String absoluteFilePath, String commitMessage, String remoteName, String remoteBranch) {
        File repoTop = resolveRepoTop();
        String targetRemote = (remoteName == null || remoteName.isBlank()) ? "origin" : remoteName;
        String targetBranch = (remoteBranch == null || remoteBranch.isBlank()) ? "main" : remoteBranch;

        try {
            runGit(List.of("git", "add", absoluteFilePath), repoTop);
            
            try {
                runGit(List.of("git", "commit", "-m", commitMessage), repoTop);
            } catch (IOException e) {
                String msg = e.getMessage() == null ? "" : e.getMessage();
                if (!msg.contains("nothing to commit") && !msg.contains("no changes added")) {
                    throw e;
                }
            }
            
            String prefix = determinePrefixPath(absoluteFilePath, repoTop);
            runGit(List.of("git", "subtree", "split", "--prefix=" + prefix, "-b", "tmp-notes-split"), repoTop);
            
            try {
                runGit(List.of("git", "push", targetRemote, "tmp-notes-split:" + targetBranch, "--force-with-lease"), repoTop);
            } finally {
                try {
                    runGit(List.of("git", "branch", "-D", "tmp-notes-split"), repoTop);
                } catch (Exception ignored) {}
            }
            
            return "Git add/commit/subtree-push 완료 → " + targetRemote + "/" + targetBranch;
        } catch (Exception e) {
            log.error("Git 작업 실패", e);
            return "Git 저장 실패: " + e.getMessage();
        }
    }

    private File resolveRepoTop() {
        File cwd = new File(".");
        try {
            String root = runGit(List.of("git", "rev-parse", "--show-toplevel"), cwd);
            if (root == null || root.isBlank()) return cwd.getAbsoluteFile();
            return Paths.get(root).toFile();
        } catch (Exception e) {
            return cwd.getAbsoluteFile();
        }
    }

    private String determinePrefixPath(String absoluteFilePath, File repoTop) throws IOException {
        String rootPath = runGit(List.of("git", "rev-parse", "--show-toplevel"), repoTop);
        Path root = Paths.get(rootPath).normalize();
        Path file = Paths.get(absoluteFilePath).normalize();
        Path parent = file.getParent();
        if (parent == null) throw new IllegalStateException("Invalid file path: " + absoluteFilePath);
        Path rel = root.relativize(parent);
        String prefix = rel.toString().replace(File.separatorChar, '/');
        if (prefix.isBlank()) throw new IllegalStateException("Cannot compute subtree prefix for: " + absoluteFilePath);
        return prefix;
    }

    private String runGit(List<String> command, File workingDir) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(workingDir);
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (InputStream is = proc.getInputStream()) {
            is.transferTo(baos);
        }
        try {
            int exit = proc.waitFor();
            String out = baos.toString(StandardCharsets.UTF_8);
            if (exit != 0) {
                throw new IOException("Command failed: " + String.join(" ", command) + " -> " + out.trim());
            }
            return out.trim();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Git command interrupted", e);
        }
    }
}
