# Jenkins DevSecOps Pipeline with Kubernetes and ArgoCD
## Pre-Requisites
Ensure the following prerequisites are set up before executing the pipeline:

### 1. Jenkins Setup
Install and set up Jenkins on a server.

### 2. SonarQube
Install SonarQube on a server and configure it for use within the pipeline. Generate a global or project-specific Sonar token for later use. Create a project in SonarQube and copy the code block based on your application's tech stack to add to the pipeline.

### 3. NTFY Configuration
Set up NTFY on a separate server and create a dedicated user and topic. Generate a token for this user and retain it for later use.

### 4. Kubernetes Cluster with ArgoCD
Ensure a functional Kubernetes installation with ArgoCD configured. Also, define an ingress and the corresponding routes for your application.

### 5. Plugins
Ensure the following plugins are installed before executing the pipelines:

Node Js (Dependent on your tech stack)
CloudBees (For Docker)
Sonarqube
Sonar quality gate

### 6. Credentials
In the Jenkins credentials, add the credentials for GitHub repositories and DockerHub.