package com.example.CRUD.Application.service;

import com.example.CRUD.model.Employee;
import com.example.CRUD.repository.EmployeeRepository;
import com.example.CRUD.service.EmployeeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.Mockito.*;

class EmployeeServiceTest {

    private EmployeeRepository employeeRepository;
    private EmployeeService employeeService;

    @BeforeEach
    void setup() {
        employeeRepository = mock(EmployeeRepository.class);
        employeeService = new EmployeeService(employeeRepository);
    }

    @Test
    void findById_NullId_ShouldReturnBadRequest() {
        StepVerifier.create(employeeService.getEmployeeById(null))
                .expectErrorMatches(throwable -> throwable instanceof
                        ResponseStatusException && ((ResponseStatusException) throwable)
                        .getStatusCode()==HttpStatus.BAD_REQUEST && throwable.getMessage()
                        .contains("Employee ID cannot be empty"))
                .verify();
        verifyNoInteractions(employeeRepository);
    }

    @Test
    void save_NULLId_ShouldReturnBadRequest(){
        Employee employee=Employee.builder()
                .id(null)
                .name("Manisha")
                .role("Developer")
                .department("IT")
                .phoneNumber("9898989898")
                .build();

        StepVerifier.create(employeeService.createEmployee(employee))
                .expectError(ResponseStatusException.class)
                .verify();
    }

    @Test
    void save_InvalidPhoneNumber_ShouldReturnBadRequest() {
        Employee employee=Employee.builder()
                .id("1")
                .name("Manisha")
                .role("Developer")
                .department("IT")
                .phoneNumber("123")
                .build();

        StepVerifier.create(employeeService.createEmployee(employee))
                .expectErrorMatches(throwable ->
                        throwable instanceof ResponseStatusException &&
                                ((ResponseStatusException) throwable).getStatusCode() == HttpStatus.BAD_REQUEST &&
                                throwable.getMessage().contains("Phone number must be 10 digits"))
                .verify();
        verify(employeeRepository, never()).save(any());
    }

    @Test
    void save_NullEmployee_ShouldReturnBadRequest() {
        StepVerifier.create(employeeService.createEmployee(null))
                .expectError(ResponseStatusException.class)
                .verify();
    }

    @Test
    void save_ValidEmployee_ShouldCallRepository() {
        Employee employee = Employee.builder()
                .id("1")
                .name("Manisha")
                .role("Developer")
                .department("IT")
                .phoneNumber("9876543210")
                .build();

        when(employeeRepository.save(employee)).thenReturn(Mono.just(employee));

        StepVerifier.create(employeeService.createEmployee(employee))
                .expectNext(employee)
                .verifyComplete();

        verify(employeeRepository).save(employee);
    }

    @Test
    void update_NullId_ShouldReturnBadRequest() {
        StepVerifier.create(employeeService.updateEmployee(null, new Employee()))
                .expectError(ResponseStatusException.class)
                .verify();
    }

    @Test
    void update_NullEmployee_ShouldReturnBadRequest() {
        StepVerifier.create(employeeService.updateEmployee("1", null))
                .expectError(ResponseStatusException.class)
                .verify();
    }

    @Test
    void deleteById_NullId_ShouldReturnBadRequest() {
        StepVerifier.create(employeeService.deleteEmployee(null))
                .expectError(ResponseStatusException.class)
                .verify();
    }
}
