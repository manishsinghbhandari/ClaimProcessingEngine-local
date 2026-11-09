# Camunda Platform - Local Setup Guide

This guide outlines the steps required to run the Camunda Platform on your local environment using Docker Compose.

## Table of Contents
- [Requirements](#requirements)
- [Running Camunda Locally](#running-camunda-locally)
- [Testing the Setup](#testing-the-setup)
- [Troubleshooting](#troubleshooting)

---

### Requirements

- **Docker**: Ensure Docker is installed and running on your machine.
- **Docker Compose**: Ensure Docker Compose is installed to manage your services.

### Running Camunda Locally

To start the Camunda platform using your existing Docker Compose setup:

1. **Navigate to the `docker-camunda` directory** where your `docker-compose.yml` file is located:
   ```bash
   cd path/to/docker-camunda

2. **Run the following command** to start the Camunda platform:
   ```bash
   docker-compose up -d

3. **Access Camunda:** Once the setup is complete, access Camunda at [localhost:8080](http://localhost:8080/).

## Testing the Setup

**Camunda Welcome Page:**

Navigate to http://localhost:8080 in your browser to confirm the setup is running.
Admin Login:

Default credentials are:
* **Username:** demo
* **Password:** demo

## Troubleshooting
* **Port Conflicts:** Ensure no other service is using port 8080. Change the port in your docker-compose.yml if necessary.
* **Database Issues:** Verify that your database settings are correctly configured if using a persistent database.
* **Logs:** Check Camunda logs with:
    ```bash
    docker logs <container_name>

**Replace <container_name> with the name of your Camunda container.**