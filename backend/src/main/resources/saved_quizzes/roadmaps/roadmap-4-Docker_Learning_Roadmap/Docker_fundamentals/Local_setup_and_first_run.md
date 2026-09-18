# Local Setup and First Run
총 6문항 · 정답 4 · 오답 2

## Q1. After installing Docker on a local machine, what is the best first step to confirm the engine is working?
- A. Start Docker Desktop and verify it is running
- B. Create a container image from scratch
- C. Uninstall and reinstall the CLI
- D. Edit the Docker daemon configuration file
❌ 내 답: Edit the Docker daemon configuration file  |  정답: Start Docker Desktop and verify it is running
설명: Starting Docker and confirming it is running verifies that the engine is available locally.

## Q2. Which command is used to check the installed Docker version?
- A. docker version
- B. docker help version
- C. docker check
- D. docker info version
✅ 내 답: docker version
설명: docker version prints the client and server version information.

## Q3. Which command shows Docker's general help text and command structure?
- A. docker --help
- B. docker status
- C. docker commands
- D. docker usage
✅ 내 답: docker --help
설명: docker --help displays the main Docker CLI help and available commands.

## Q4. What is the general structure of a Docker CLI command?
- A. It is the same command with the form docker <command> <subcommand>
- B. It must always start with sudo docker install
- C. It uses only one-word commands with no arguments
- D. It requires a GUI menu before each command
✅ 내 답: It is the same command with the form docker <command> <subcommand>
설명: Docker commands typically follow the pattern docker plus a command and optional subcommand or flags.

## Q5. Which command is the standard first workflow to run a simple test container and see output?
- A. docker run hello-world
- B. docker build hello-world
- C. docker exec hello-world
- D. docker pull hello-world
✅ 내 답: docker run hello-world
설명: docker run hello-world starts a container that prints a success message and exits.

## Q6. What is the main difference between interactive and detached Docker container modes?
- A. Detached mode runs in the background; interactive mode keeps your terminal attached
- B. Interactive mode always deletes the container; detached mode never does
- C. Detached mode requires a Dockerfile; interactive mode does not
- D. Interactive mode can only run images from the internet
❌ 내 답: Detached mode requires a Dockerfile; interactive mode does not  |  정답: Detached mode runs in the background; interactive mode keeps your terminal attached
설명: Interactive mode is attached to your terminal, while detached mode runs in the background.