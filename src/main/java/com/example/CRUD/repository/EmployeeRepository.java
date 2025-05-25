package com.example.CRUD.repository;

import java.util.stream.Collectors;

import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.stereotype.Repository;
import com.example.CRUD.model.Employee;
import reactor.core.publisher.Mono;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import reactor.core.publisher.Flux;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Repository
public class EmployeeRepository {
    private final ReactiveRedisTemplate<String, Employee> redisTemplate;
    private final ReactiveValueOperations<String, Employee> employeeCache;
    private final ElasticsearchClient elasticsearchClient;

    public EmployeeRepository(ReactiveRedisTemplate<String, Employee> redisTemplate,
            ElasticsearchClient elasticsearchClient) {
        this.redisTemplate = redisTemplate;
        this.employeeCache = redisTemplate.opsForValue();
        this.elasticsearchClient = elasticsearchClient;
    }

    public Mono<Employee> save(Employee employee) {
        double random = Math.random();
        Mono<Employee> saveToElastic = Mono.fromCallable(() -> {
            elasticsearchClient.index(i -> i.index("employees").id(employee.getId()).document(employee));
            return employee;
        });
        if (random > 0.5) {
            return saveToElastic.flatMap(saved -> employeeCache.set(employee.getId(), employee).thenReturn(employee));
        } else {
            return saveToElastic;
        }
    }

    public Mono<Employee> findById(String id) {
        return employeeCache.get(id)
                .switchIfEmpty(
                        Mono.fromCallable(() -> {
                            var response = elasticsearchClient.get(g -> g.index("employees").id(id), Employee.class);
                            if (!response.found()) {
                                throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                                        "Employee not found with ID: " + id);
                            }
                            return response.source();
                        }).flatMap(emp -> employeeCache.set(id, emp).thenReturn(emp)));
    }

    public Mono<Employee> update(String id, Employee employee) {
        Employee updatedEmployee = new Employee(id, employee.getName(), employee.getRole(), employee.getDepartment());
        return Mono.fromCallable(() -> elasticsearchClient.exists(e -> e.index("employees").id(id)).value())
                .flatMap(exists -> {
                    if (exists) {
                        return Mono.fromCallable(() -> {
                            elasticsearchClient.update(u -> u.index("employees").id(id).doc(updatedEmployee),
                                    Employee.class);
                            return updatedEmployee;
                        }).flatMap(updated -> redisTemplate.delete(id).thenReturn(updated));
                    } else {
                        return Mono.error(
                                new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee not found with ID: " + id));
                    }
                });
    }

    public Mono<Void> deleteById(String id) {
        return Mono.fromCallable(() -> elasticsearchClient.exists(e -> e.index("employees").id(id)).value())
                .flatMap(exists -> {
                    if (!exists) {
                        return Mono.error(
                                new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee not found with ID: " + id));
                    }
                    return Mono.fromRunnable(() -> {
                        try {
                            elasticsearchClient.delete(d -> d.index("employees").id(id));
                        } catch (Exception e) {
                            System.out.println("Employee not found with ID: " + id);
                        }
                    }).then(redisTemplate.delete(id).then());
                });
    }

    public Flux<Employee> findAll() {
        return Mono.fromCallable(() -> {
            var search = elasticsearchClient.search(s -> s.index("employees").query(q -> q.matchAll(m -> m)),
                    Employee.class);
            return search.hits().hits().stream().map(hit -> hit.source()).collect(Collectors.toList());
        }).flatMapMany(Flux::fromIterable);
    }
}
