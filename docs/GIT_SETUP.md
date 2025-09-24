## Git 설정 가이드 (자동 커밋/푸시 연동)

이 문서는 애플리케이션의 "Git에 저장" 기능이 원활히 동작하도록 레포지토리와 인증을 설정하는 방법을 설명합니다. 백엔드의 `GitService`는 `git add/commit` 후, 특정 디렉터리만 별도 원격(`notes`)으로 푸시합니다.

### 목표
- 커밋 작성자 정보를 설정합니다.
- 원격 저장소(`origin`)를 연결합니다.
- 인증(SSH 권장 또는 HTTPS+PAT)을 구성합니다.
- 기본 브랜치와 업스트림 추적을 설정합니다.

---

### 1) 사용자 정보 설정 (최초 1회)

커밋 메타데이터에 들어갈 작성자 정보를 설정합니다. 전역 또는 현재 레포에만 설정 가능합니다.

```bash
# 레포 루트(예: /Users/yeob-eunjun/eunjuny/project/auknowlog)
cd /Users/yeob-eunjun/eunjuny/project/auknowlog

# 전역(Global) 설정 - 모든 레포에 적용
git config --global user.name "eunjuny"
git config --global user.email "joon2di@gmail.com"

# 현재 레포에만 적용하고 싶다면 --global 제거
# git config user.name "eunjuny"
# git config user.email "joon2di@gmail.com"
```

macOS에서 권한 오류 예방을 위해 안전 디렉터리를 등록합니다.

```bash
git config --global --add safe.directory /Users/yeob-eunjun/eunjuny/project/auknowlog
```

이 설정은 "detected dubious ownership"와 같은 경고/오류를 방지합니다.

---

### 2) 원격 저장소 분리 연결 (origin ↔ notes)

- `origin`은 본 레포: `https://github.com/eunjuny/auknowlog.git`
- `notes`는 저장 전용 레포: `https://github.com/eunjuny/auknowlog_note.git`

```bash
# 원격 확인
git remote -v

# origin 설정(본 레포)
git remote set-url origin https://github.com/eunjuny/auknowlog.git  # 없으면 add

# notes 설정(노트 레포)
git remote set-url notes https://github.com/eunjuny/auknowlog_note.git 2>/dev/null || \
git remote add notes https://github.com/eunjuny/auknowlog_note.git
```

> 과거 문제: origin을 notes로 바꿔 전체 프로젝트가 notes에 푸시됨 → 반드시 origin/notes를 분리해 사용하세요.

---

### 3) 인증 방식 선택 (SSH 권장)

#### A. SSH (권장)

```bash
# SSH 키 생성 (없다면)
ssh-keygen -t ed25519 -C "joon2di@gmail.com"

# 에이전트 실행 및 키 등록 (macOS)
eval "$(ssh-agent -s)"
ssh-add --apple-use-keychain ~/.ssh/id_ed25519

# (선택) SSH 설정 파일 편의 구성
# vi ~/.ssh/config
# Host github.com
#   HostName github.com
#   User git
#   AddKeysToAgent yes
#   UseKeychain yes
#   IdentityFile ~/.ssh/id_ed25519

# 공개키를 복사하여 GitHub → Settings → SSH and GPG keys 에 등록
cat ~/.ssh/id_ed25519.pub

# 연결 테스트
ssh -T git@github.com
```

원격을 SSH로 사용하려면 아래처럼 설정합니다.

```bash
git remote set-url origin git@github.com:eunjuny/auknowlog.git
git remote set-url notes git@github.com:eunjuny/auknowlog_note.git 2>/dev/null || \
git remote add notes git@github.com:eunjuny/auknowlog_note.git
```

#### B. HTTPS + 개인 액세스 토큰(PAT)

```bash
# 키체인에 자격 증명 저장 (macOS)
git config --global credential.helper osxkeychain

# 원격 URL이 HTTPS인지 확인/설정
git remote set-url origin https://github.com/eunjuny/auknowlog.git
git remote set-url notes https://github.com/eunjuny/auknowlog_note.git 2>/dev/null || \
git remote add notes https://github.com/eunjuny/auknowlog_note.git

# 최초 push에서 사용자명과 PAT를 묻습니다. 키체인에 저장되어 다음부터는 자동 인증됩니다.
```

---

### 4) 기본 브랜치 및 업스트림 설정

```bash
# 기본 브랜치를 main으로 통일
git branch -M main

# 최초 푸시 및 업스트림 설정(-u)
git push -u origin main
```

업스트림을 설정하면 이후 `git push`만으로도 자동으로 원격/브랜치를 인지합니다.

---

### 5) 애플리케이션 연동 동작

- 프런트 버튼: "Git에 저장" → `POST /api/documents/save-quiz-git`
- 백엔드 처리 흐름:
  1) 퀴즈 결과를 마크다운으로 렌더링
  2) 파일 저장: `backend/src/main/resources/saved_quizzes/` (레포 기준)
  3) 커밋: `git add <절대경로>` → `git commit -m "chore: save quiz markdown (제목)"`
  4) 서브트리 푸시: 레포 최상위에서 `git subtree split --prefix=backend/src/main/resources/saved_quizzes -b tmp-notes-split`
     → `git push notes tmp-notes-split:main --force-with-lease` → 임시 브랜치 삭제

- 보장 사항:
  - notes에는 `saved_quizzes` 디렉터리 히스토리만 반영(전체 레포 푸시 방지)
  - 서브트리 실패 시 전체 HEAD 강제 푸시 대신 에러 반환 (안전)

- 사전 조건:
  - `git` 바이너리가 PATH에 있어야 함
  - `origin`/`notes` 원격과 인증(SSH 또는 HTTPS+PAT) 설정 완료
  - 최초 업스트림 설정 필요 시: `git push -u origin main`
  - 커밋할 변경이 없으면 "nothing to commit"으로 처리

---

### 6) 문제 해결(Troubleshooting)

- Permission denied (publickey)
  - SSH 키 미등록/미로드 → 3-A 절차 수행 후 `ssh -T git@github.com` 확인

- Authentication failed for 'https://...'
  - PAT 미설정/만료 → 새 PAT 발급 후 키체인 저장, `git config --global credential.helper osxkeychain`

- fatal: detected dubious ownership in repository
  - 안전 디렉터리 미등록 → `git config --global --add safe.directory <레포 절대경로>`

- remote: Repository not found / 403
  - 원격 URL 오타/권한 부족 → 원격 URL/권한 확인, 콜라보레이터 초대

- non-fast-forward 오류 (notes가 앞서 있음)
  - 앱은 `--force-with-lease`로 서브트리를 업데이트
  - 수동 초기화가 필요하면 notes/main을 빈 커밋으로 리셋:
    ```bash
    EMPTY_TREE=$(git hash-object -t tree /dev/null)
    COMMIT=$(printf "reset notes to empty\n" | git commit-tree $EMPTY_TREE)
    git push notes -f $COMMIT:refs/heads/main
    ```

- "이 명령은 작업 폴더의 최상위에서만" (subtree split 오류)
  - 레포 최상위가 아닐 때 발생 → 레포 루트에서 실행해야 함
  - 앱은 레포 루트에서 자동 실행하도록 수정됨

---

### 7) 운영 팁

- 커밋 메시지 포맷은 `GitService`에서 중앙관리(필요 시 규칙 반영)
- 별도 디렉터리에 저장하고 싶다면 `DocumentService`의 저장 경로와 `GitService`의 `--prefix`를 함께 변경 (레포 루트 기준)
- 경로 기준: `backend/src/main/resources/saved_quizzes`
- 초기 빈 저장소에 첫 푸시가 완료되면 원격에 파일이 보입니다: [auknowlog_note 저장소](https://github.com/eunjuny/auknowlog_note.git)


