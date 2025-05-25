package com.example.CRUD.service;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import com.example.CRUD.repository.EmployeeRepository;
import com.example.CRUD.model.Employee;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;

@Service
public class EmployeeService {

    private final EmployeeRepository employeeRepository;

    @Autowired
    public EmployeeService(EmployeeRepository employeeRepository) {
        this.employeeRepository = employeeRepository;
    }

    public Mono<Employee> getEmployeeById(String id) {
        return employeeRepository.findById(id);
    }

    public Mono<Employee> createEmployee(Employee employee) {
        return employeeRepository.save(employee);
    }

    public Mono<Employee> updateEmployee(String id, Employee employee) {
        return employeeRepository.update(id, employee);
    }

    public Mono<Void> deleteEmployee(String id) {
        return employeeRepository.deleteById(id);
    }

    public Flux<Employee> findAll() {
        return employeeRepository.findAll();
    }
}
