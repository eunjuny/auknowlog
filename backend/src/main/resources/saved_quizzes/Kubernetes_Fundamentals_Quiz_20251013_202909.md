# Kubernetes Fundamentals Quiz
총 5문항 · 정답 5 · 오답 0

## Q1. What is Kubernetes primarily designed for?
- A. Database management
- B. Container orchestration
- C. Network routing
- D. Operating system development
✅ 내 답: Container orchestration
설명: Kubernetes is an open-source system for automating deployment, scaling, and management of containerized applications.

## Q2. Which of the following is a core component of the Kubernetes Control Plane?
- A. Kubelet
- B. Kube-proxy
- C. Kube-apiserver
- D. Container Runtime
✅ 내 답: Kube-apiserver
설명: The Kube-apiserver exposes the Kubernetes API and is the front end for the Kubernetes control plane.

## Q3. In Kubernetes, what object is used to define and manage a set of identical, replicated pods?
- A. Service
- B. Ingress
- C. Deployment
- D. ConfigMap
✅ 내 답: Deployment
설명: A Deployment provides declarative updates for Pods and ReplicaSets. It manages the desired state of your application.

## Q4. Which Kubernetes object is used to expose an application running on a set of Pods as a network service?
- A. Pod
- B. Node
- C. Service
- D. PersistentVolume
✅ 내 답: Service
설명: A Kubernetes Service is an abstract way to expose an application running on a set of Pods as a network service with a stable IP address and DNS name.

## Q5. What is the smallest deployable unit that can be created and managed in Kubernetes?
- A. Node
- B. Cluster
- C. Container
- D. Pod
✅ 내 답: Pod
설명: A Pod is the smallest deployable unit in Kubernetes, representing a single instance of a running process in your cluster, which can contain one or more tightly coupled containers.