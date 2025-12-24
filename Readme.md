# Snap Pattern

## Overview
Snap Pattern is a repository designed to prototype and implement various microservice design patterns in a Kubernetes environment. It provides tools for deploying, monitoring, and analyzing microservices using JavaFX for the UI and Kubernetes for orchestration. The repository also integrates monitoring stacks and metrics generation for performance evaluation.


## Getting Started
1. **Prerequisites**:
   - Java 17.
   - Apache Jmeter installed and configured.
   - Python 3.11 with required dependencies (available on [`Requirements.txt`](Python/requirements.txt)).
   - Deployment yaml file of your system under the test. The sample deployment Yaml file of the data-sharing pipelines with docker images for both [`Apple Silicon (arm64)`](examples/data-sharing-deployment.yaml) and [`Intel/AMD (amd64)`](examples/data-sharing-deployment-linux.yaml) is available.
   - Woral load generation script. a Sample available in [`Jmeter script`](src/main/resources/workloads/data-pipeline.jmx).

2. **Setup**:
   - Clone the repository.
   - Install dependencies listed in `requirements.txt` for Python.
   - Download and extract Apache JMeter from their website. Place it into the root of the repository
   - Update the `java17Path` variable in [`WorkloadController.java`](src/main/java/design_pattern_prototyping/controller/WorkloadController.java) to match your local Java path
   - Alternaively just run the installation script `bash ./install.sh`

   ```
   #!/bin/bash
   curl -LO https://dlcdn.apache.org/jmeter/binaries/apache-jmeter-5.6.3.tgz
   tar -xzf apache-jmeter-5.6.3.tgz
   rm -rf apache-jmeter-5.6.3.tgz
   python3 -m venv Python/env
   source Python/env/bin/activate
   pip install --upgrade pip
   pip install -r Python/requirements.txt
   ```

<!---
[![asciicast](https://asciinema.org/a/DDCwOsDPutcVX7FEAneFoGD9u.svg)](https://asciinema.org/a/DDCwOsDPutcVX7FEAneFoGD9u)
-->
3. **Run the Application**:
   - Start Minikube using the UI or CLI.
   - Execute the `Main.java` file to launch the JavaFX application.
   - For deployment of the data piplines as system under the test use the deployment files with docker images for both [`Apple Silicon (arm64)`](examples/data-sharing-deployment.yaml) and [`Intel/AMD (amd64)`](examples/data-sharing-deployment-linux.yaml)
   - For the workload script, you can our sample [`Jmeter script`](src/main/resources/workloads/data-pipeline.jmx).
   - Use the UI to deploy patterns, monitor services, and analyze metrics.

    [![tutorial](https://i.vimeocdn.com/video/2035940635-0e042383ac58489aa8a40d99d5b64a0e291315d6bce7ece9a540b1effd366f3f-d_640x360?&region=us)](https://vimeo.com/1100669519)

## Evaluation Configuration Parameters(Reproducibility of Experiments)

### Reproducibility of Plots 
To regenerate the plots, you can run [`main.py`](Python/main.ipynb), which uses the metrics in the [`mertics.csv`](Python/results/metrics_data.xlsx) file to generate the plots. However, you need to copy the energy-related metrics to the [`sub_metrics_file`](Python/results/metrics_data_5min_updated.csv) before running the program. 
### Baseline
For our baseline, we deploy a the deployment.yaml file without an added pattern.

### Async Request Reply

| Service Name          | Endpoint Path |
|-----------------------|----------------|
| filter-service        | /              |
| formatting-service    | /              |
| aggregation-service   | /              |
| anonymization-service | /              |
  
### Circuit Breaker
| Service Name          | Route Path | Port | Max Conn | Max Pend | Max Req | Retries | Timeout |
|-----------------------|------------|------|----------|----------|---------|---------|---------|
| filter-service        | /          | 8081 | 100      | 20       | 1       | 2       | 1s      |
| formatting-service    | /          | 8084 | 100      | 20       | 1       | 2       | 1s      |
| aggregation-service   | /          | 8082 | 100      | 20       | 1       | 2       | 1s      |
| anonymization-service | /          | 8083 | 100      | 20       | 1       | 2       | 1s      |

### Gateway Offloading

Workload is run through the gateway ingress instead of the coordinator service

| Field             | Value         |
|------------------|---------------|
| **Service Name** | coordinator   |
| **Service Port** | 8080          |
| **Service Endpoint** | /         |

### Cache Aside

| Field                        | Value               |
|-----------------------------|---------------------|
| Backend Service             | data-product-service|
| Backend Port                | 8089                |
| Cached Endpoints            | /                   |
| Cache TTL (seconds)         | 60                  |
| Max Connections             | 100                 |

### Request Collapsing

| Field               | Value                     |
| ------------------- | ------------------------- |
| **Backend Service** | data-product-service      |
| **Backend Port**    | 8089                      |
| **Endpoint Path**   | /data-json                |
| **Query Parameter** | *(empty)*                 |
| **ID Field**        | *(empty)*                 |
| **Batch Query**     | ^/data\\-json\$           |
| **DB Host**         | data-product-service.user |
| **DB Port**         | 8089                      |
| **DB Name**         | *(empty)*                 |
| **DB Username**     | *(empty)*                 |
| **DB Password**     | *(empty)*                 |


## File Structure
The repository is organized as follows:

### `/src/main/java`
Contains the Java source code for the application.

- **`design_pattern_prototyping`**: The main package for the application.
  - **`controller`**: Contains JavaFX controllers for managing UI interactions.
    - `MainController.java`: Handles the main application logic and Kubernetes cluster management.
    - `PatternController.java`: Manages the creation and deployment of design patterns.
    - `MetricsController.java`: Handles metrics generation and visualization.
    - `ResourceController.java`: Manages Kubernetes resources like pods and services.
    - `DeploymentController.java`: Handles instrumentation language selection for deployments.
  - **`Kubernetes`**: Contains utilities for interacting with Kubernetes clusters.
    - `KubernetesUtil.java`: Provides methods for managing namespaces, applying YAML files, and interacting with Minikube.
    - `KubernetesClientAPI.java`: Implements Kubernetes API client functionalities.
  - **`Monitoring`**: Contains classes for deploying and interacting with monitoring stacks.
    - `DeployMonitoringStack.java`: Deploys monitoring tools like Prometheus, Grafana, and Jaeger.
    - `MetricsExporter.java`: Exports metrics to Excel files.
    - `JaegerClient.java`: Collects trace data from Jaeger.
  - **`pattern_generator`**: Implements logic for generating and deploying design patterns.
    - `CacheAsideGenerator.java`, `CircuitBreakerGenerator.java`, etc.: Classes for specific design patterns.
  - **`util`**: Contains utility classes for YAML manipulation, logging, and configuration dialogs.
    - `YamlEditor.java`: Edits YAML files for Kubernetes configurations.
    - `UILogger.java`: Logs messages to the UI and console.

### `/src/main/resources`
Contains resources like YAML templates, configuration files, and UI layouts.

- **`Patterns`**: Contains YAML templates for different design patterns.
  - **`CacheAside`**, **`CircuitBreaker`**, etc.: Subfolders for specific patterns.
- **`monitoring`**: Contains YAML files for deploying monitoring tools.
- **`workloads`**: Stores JMeter workload files for performance testing.

### `/Python`
Contains Python scripts for metrics generation and visualization.

- **`Metrics_Service.py`**: A Flask-based service for generating metrics.
- **`results`**: Stores generated metrics and plots.

### `/Readme.md`
This file provides an overview of the repository and its structure.

## Features
- **Design Pattern Prototyping**: Generate and deploy microservice design patterns like Cache-Aside, Circuit Breaker, etc.
- **Kubernetes Integration**: Manage namespaces, apply configurations, and interact with Minikube.
- **Monitoring Stack**: Deploy Prometheus, Grafana, and Jaeger for metrics collection and visualization.
- **Metrics Export**: Export metrics to Excel files for analysis.
- **UI**: JavaFX-based graphical interface for managing the application.



## Contributing
Contributions are welcome! Please follow the repository's coding standards and submit pull requests for review.

## License
This project is licensed under the MIT License. 
