# Containers Images Lifecycle
총 7문항 · 정답 5 · 오답 2

## Q1. Which container state usually comes before it is started?
- A. created
- B. running
- C. paused
- D. deleted
✅ 내 답: created
설명: A container is typically created first, then started to enter the running state.

## Q2. Which command stops and then starts a container in one step?
- A. docker restart mycontainer
- B. docker stop mycontainer
- C. docker rm mycontainer
- D. docker logs mycontainer
✅ 내 답: docker restart mycontainer
설명: docker restart performs a stop followed by a start.

## Q3. Which command removes a stopped container from the host?
- A. docker rm mycontainer
- B. docker run mycontainer
- C. docker exec mycontainer
- D. docker inspect mycontainer
✅ 내 답: docker rm mycontainer
설명: docker rm deletes the container after it is no longer needed.

## Q4. Which command shows detailed configuration such as environment variables, mounts, and network settings for a container?
- A. docker inspect mycontainer
- B. docker ps mycontainer
- C. docker top mycontainer
- D. docker attach mycontainer
❌ 내 답: docker ps mycontainer  |  정답: docker inspect mycontainer
설명: docker inspect returns detailed JSON configuration and runtime metadata.

## Q5. Which command is used to read the output a container produced during execution?
- A. docker logs mycontainer
- B. docker commit mycontainer
- C. docker pull mycontainer
- D. docker rename mycontainer
✅ 내 답: docker logs mycontainer
설명: docker logs shows the container's stdout and stderr output.

## Q6. How should you think about a running container's processes on the host?
- A. The container's processes run as host processes in their own isolated namespace.
- B. They run only inside the image and are invisible to the host.
- C. They are converted into kernel processes that cannot be inspected.
- D. They consume no host CPU or memory.
❌ 내 답: They consume no host CPU or memory.  |  정답: The container's processes run as host processes in their own isolated namespace.
설명: Container processes are still host processes, but they are isolated with namespaces and cgroups.

## Q7. What is the practical purpose of container resource limits on the host?
- A. It can limit how much CPU and memory the container may use.
- B. It forces the container to create new images.
- C. It changes the container's internal files automatically.
- D. It makes logs unavailable.
✅ 내 답: It can limit how much CPU and memory the container may use.
설명: Resource limits help control host usage by constraining CPU, memory, and related resources.