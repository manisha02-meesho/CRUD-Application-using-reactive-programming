package com.example.CRUD.service;

import org.springframework.stereotype.Service;
import com.example.CRUD.model.Employee;
import com.example.CRUD.repository.EmployeeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

@Service
public class EmployeeService {

    private final EmployeeRepository employeeRepository;

    @Autowired
    public EmployeeService(EmployeeRepository employeeRepository) {
        this.employeeRepository = employeeRepository;
    }

    public Mono<Employee> getEmployeeById(String id) {
        if (id == null || id.isBlank()) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Employee ID cannot be empty"));
        }
        return employeeRepository.findById(id);
    }

    public Mono<Employee> createEmployee(Employee employee) {
        if (employee == null) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Employee body must not be null"));
        }
        if (employee.getId() == null || employee.getId().trim().isEmpty()) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Employee ID cannot be empty"));
        }
        if (employee.getPhoneNumber() == null || !employee.getPhoneNumber().matches("^\\d{10}$")) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Phone number must be 10 digits"));
        }
        return employeeRepository.save(employee);
    }

    public Mono<Employee> updateEmployee(String id, Employee employee) {
        if (id == null || id.isBlank()) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Employee ID cannot be empty"));
        }
        if (employee == null) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Employee cannot be null"));
        }

        Employee updatedEmployee = Employee.builder()
                .id(id)
                .name(employee.getName())
                .role(employee.getRole())
                .department(employee.getDepartment())
                .phoneNumber(employee.getPhoneNumber())
                .build();

        return employeeRepository.update(id, updatedEmployee);
    }

    public Mono<Void> deleteEmployee(String id) {
        if (id == null || id.isBlank()) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Employee ID cannot be empty"));
        }
        return employeeRepository.deleteById(id);
    }
}
