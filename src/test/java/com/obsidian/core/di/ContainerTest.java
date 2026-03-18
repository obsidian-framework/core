package com.obsidian.core.di;

import com.obsidian.core.di.annotations.Inject;
import com.obsidian.core.di.annotations.Repository;
import com.obsidian.core.di.annotations.Service;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ContainerTest
{
    @BeforeEach
    void setUp() {
        Container.clear();
    }

    // ──────────────────────────────────────────────
    // Test fixtures (inner classes)
    // ──────────────────────────────────────────────

    @Service
    static class SimpleService {
        public String hello() { return "hello"; }
    }

    @Repository
    static class SimpleRepository {
        public String find() { return "found"; }
    }

    @Service
    static class ServiceWithConstructorDep {
        private final SimpleRepository repo;

        public ServiceWithConstructorDep(SimpleRepository repo) {
            this.repo = repo;
        }

        public SimpleRepository getRepo() { return repo; }
    }

    @Service
    static class ServiceWithFieldInjection {
        @Inject
        private SimpleRepository repo;

        public SimpleRepository getRepo() { return repo; }
    }

    @Service
    static class CircularA {
        public CircularA(CircularB b) {}
    }

    @Service
    static class CircularB {
        public CircularB(CircularA a) {}
    }

    static class NotAnnotated {
        public String nope() { return "nope"; }
    }

    interface Greeter {
        String greet();
    }

    @Service
    static class FrenchGreeter implements Greeter {
        @Override
        public String greet() { return "Bonjour"; }
    }

    @Service
    static class MultiConstructorNoInject {
        public MultiConstructorNoInject() {}
        public MultiConstructorNoInject(String s) {}
    }

    @Service
    static class MultiConstructorWithInject {
        private final SimpleRepository repo;

        public MultiConstructorWithInject() {
            this.repo = null;
        }

        @Inject
        public MultiConstructorWithInject(SimpleRepository repo) {
            this.repo = repo;
        }

        public SimpleRepository getRepo() { return repo; }
    }

    @Service
    static class DeepA {
        private final DeepB b;
        public DeepA(DeepB b) { this.b = b; }
        public DeepB getB() { return b; }
    }

    @Service
    static class DeepB {
        private final SimpleRepository repo;
        public DeepB(SimpleRepository repo) { this.repo = repo; }
        public SimpleRepository getRepo() { return repo; }
    }

    // ──────────────────────────────────────────────
    // Tests
    // ──────────────────────────────────────────────

    @Test
    void resolve_simpleService() {
        SimpleService service = Container.resolve(SimpleService.class);

        assertNotNull(service);
        assertEquals("hello", service.hello());
    }

    @Test
    void resolve_simpleRepository() {
        SimpleRepository repo = Container.resolve(SimpleRepository.class);

        assertNotNull(repo);
        assertEquals("found", repo.find());
    }

    @Test
    void resolve_returnsSameSingleton() {
        SimpleService first = Container.resolve(SimpleService.class);
        SimpleService second = Container.resolve(SimpleService.class);

        assertSame(first, second);
    }

    @Test
    void resolve_constructorInjection() {
        ServiceWithConstructorDep service = Container.resolve(ServiceWithConstructorDep.class);

        assertNotNull(service);
        assertNotNull(service.getRepo());
        assertEquals("found", service.getRepo().find());
    }

    @Test
    void resolve_fieldInjection() {
        ServiceWithFieldInjection service = Container.resolve(ServiceWithFieldInjection.class);

        assertNotNull(service);
        assertNotNull(service.getRepo());
        assertEquals("found", service.getRepo().find());
    }

    @Test
    void resolve_constructorAndFieldShareSingleton() {
        ServiceWithConstructorDep byConstructor = Container.resolve(ServiceWithConstructorDep.class);
        ServiceWithFieldInjection byField = Container.resolve(ServiceWithFieldInjection.class);

        assertSame(byConstructor.getRepo(), byField.getRepo());
    }

    @Test
    void resolve_circularDependency_throws() {
        assertThrows(RuntimeException.class, () -> Container.resolve(CircularA.class));
    }

    @Test
    void resolve_notAnnotated_throws() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> Container.resolve(NotAnnotated.class)
        );
        assertTrue(ex.getMessage().contains("NotAnnotated"));
    }

    @Test
    void bind_interfaceToImpl() {
        Container.bind(Greeter.class, FrenchGreeter.class);

        Greeter greeter = Container.resolve(Greeter.class);

        assertNotNull(greeter);
        assertEquals("Bonjour", greeter.greet());
    }

    @Test
    void singleton_manualRegistration() {
        SimpleService manual = new SimpleService();
        Container.singleton(SimpleService.class, manual);

        SimpleService resolved = Container.resolve(SimpleService.class);

        assertSame(manual, resolved);
    }

    @Test
    void resolve_multipleConstructors_noInject_throws() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> Container.resolve(MultiConstructorNoInject.class)
        );
        assertTrue(ex.getMessage().contains("constructors"));
    }

    @Test
    void resolve_multipleConstructors_withInject_usesAnnotated() {
        MultiConstructorWithInject service = Container.resolve(MultiConstructorWithInject.class);

        assertNotNull(service);
        assertNotNull(service.getRepo());
    }

    @Test
    void resolve_deepDependencyChain() {
        DeepA a = Container.resolve(DeepA.class);

        assertNotNull(a);
        assertNotNull(a.getB());
        assertNotNull(a.getB().getRepo());
    }

    @Test
    void clear_removesEverything() {
        Container.resolve(SimpleService.class);
        Container.clear();

        // After clear, a fresh resolve should return a NEW instance
        SimpleService fresh = Container.resolve(SimpleService.class);
        assertNotNull(fresh);
    }

    @Test
    void injectFields_onExternalInstance() {
        ServiceWithFieldInjection external = new ServiceWithFieldInjection();
        assertNull(external.getRepo());

        Container.injectFields(external);

        assertNotNull(external.getRepo());
    }
}