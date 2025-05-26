# CRUD-Application-using-reactive-programming

## App Info

This is a reactive CRUD application built with Spring WebFlux that demonstrates:

- REST API endpoints for Employee management (Create, Read, Update, Delete)
- Reactive Redis for caching
- Elasticsearch for data persistence
- Reactive programming patterns using Project Reactor (Mono/Flux)

### Tech Stack
- Spring Boot
- Spring WebFlux
- Redis
- Elasticsearch
- Project Reactor 
- Java

### API Endpoints

- GET /api/employees/{id} - Get employee by ID
- POST /api/employees - Create new employee
- PUT /api/employees/{id} - Update employee
- DELETE /api/employees/{id} - Delete employee
- GET /api/employees/all - Get all employees

### Data Model
The Employee model contains:
- id (String)
- name (String) 
- role (String)
- department (String)