# Snap Pattern

## Overview
Snap Pattern is a repository designed to prototype and implement various microservice design patterns in a Kubernetes environment. It provides tools for deploying, monitoring, and analyzing microservices using JavaFX for the UI and Kubernetes for orchestration. The repository also integrates monitoring stacks and metrics generation for performance evaluation.


## Getting Started
1. **Prerequisites**:
   - Java 17.
   - Minikube installed and configured.
   - Python 3.11 with required dependencies.

2. **Setup**:
   - Clone the repository.
   - Install dependencies listed in `requirements.txt` for Python.
   - Download and extract Apache JMeter from their website. Place it into the root of the repository
   - Alternaively just run the installation script `bash ./install.sh`

   [![asciicast](https://asciinema.org/a/DDCwOsDPutcVX7FEAneFoGD9u.svg)](https://asciinema.org/a/DDCwOsDPutcVX7FEAneFoGD9u)

3. **Run the Application**:
   - Start Minikube using the UI or CLI.
   - Execute the `Main.java` file to launch the JavaFX application.
   - Use the UI to deploy patterns, monitor services, and analyze metrics.

    [![tutorial](https://i.vimeocdn.com/video/2035940635-0e042383ac58489aa8a40d99d5b64a0e291315d6bce7ece9a540b1effd366f3f-d_640x360?&region=us)](https://vimeo.com/1100669519)
  
## User Guide

### Deploy the monitoring Stack


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
This repository is licensed under [MIT License](LICENSE).