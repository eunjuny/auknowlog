# Helm Chart Basics Quiz
총 5문항 · 정답 5 · 오답 0

## Q1. What is a Helm Chart?
- A. A package manager for Python applications.
- B. A collection of pre-configured Docker images.
- C. A package format for Kubernetes resources.
- D. A monitoring tool for Kubernetes clusters.
✅ 내 답: A package format for Kubernetes resources.
설명: Helm Charts are packages that contain all the necessary resources and configuration to deploy an application or service onto a Kubernetes cluster, simplifying deployment and management.

## Q2. Which file is used to define metadata about a Helm Chart, such as its name, version, and API version?
- A. values.yaml
- B. templates/deployment.yaml
- C. Chart.yaml
- D. requirements.yaml
✅ 내 답: Chart.yaml
설명: The `Chart.yaml` file is mandatory and contains crucial metadata about the chart itself, defining its identity, API version, and other descriptive information.

## Q3. How can you customize the configuration of a Helm Chart during installation or upgrade?
- A. By directly editing the manifest files within the templates/ directory.
- B. By modifying the Chart.yaml file.
- C. By providing a values.yaml file or using the --set flag with helm install/upgrade.
- D. Helm Charts cannot be customized after creation.
✅ 내 답: By providing a values.yaml file or using the --set flag with helm install/upgrade.
설명: Helm allows for customization through a `values.yaml` file, which overrides default values, or by using the `--set` flag for ad-hoc value overrides directly on the command line.

## Q4. What is the primary purpose of the `values.yaml` file in a Helm Chart?
- A. To declare the API version of the Kubernetes cluster.
- B. To define default configuration values that can be overridden.
- C. To list all required dependencies for the chart.
- D. To specify the Docker images used in the chart.
✅ 내 답: To define default configuration values that can be overridden.
설명: `values.yaml` provides a set of default configuration values that the chart's templates can consume, making the chart reusable and customizable for different environments.

## Q5. Which Helm command is used to install a Helm Chart onto a Kubernetes cluster?
- A. helm get
- B. helm delete
- C. helm install
- D. helm update
✅ 내 답: helm install
설명: The `helm install` command is used to deploy a new release of a specified Helm Chart to a Kubernetes cluster, creating or updating its resources.