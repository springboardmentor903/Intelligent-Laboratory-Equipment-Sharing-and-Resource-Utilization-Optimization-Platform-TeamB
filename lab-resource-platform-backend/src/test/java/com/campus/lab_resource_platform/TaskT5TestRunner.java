package com.campus.lab_resource_platform;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class TaskT5TestRunner {

    public static void main(String[] args) {
        List<Class<?>> testClasses = List.of(
                com.campus.lab_resource_platform.sharing.service.SharingListingServiceTest.class,
                com.campus.lab_resource_platform.sharing.service.AccessRequestServiceTest.class,
                com.campus.lab_resource_platform.sharing.service.SharingAgreementServiceTest.class,
                com.campus.lab_resource_platform.booking.service.ExternalAccessValidatorTest.class,
                com.campus.lab_resource_platform.sharing.service.FeeCalculationServiceTest.class,
                com.campus.lab_resource_platform.sharing.service.SharedScheduleServiceTest.class,
                com.campus.lab_resource_platform.utilization.service.UtilizationServiceTest.class
        );

        int total = 0;
        int passed = 0;
        int failed = 0;
        List<String> failureMessages = new ArrayList<>();

        System.out.println("================================================================================");
        System.out.println("             RUNNING BACKEND TASK T5 COMPREHENSIVE TEST SUITE                  ");
        System.out.println("================================================================================");

        for (Class<?> clazz : testClasses) {
            System.out.println("\n--- Testing: " + clazz.getSimpleName() + " ---");

            Method beforeEach = null;
            Method afterEach = null;
            List<Method> testMethods = new ArrayList<>();

            for (Method m : clazz.getDeclaredMethods()) {
                if (m.isAnnotationPresent(BeforeEach.class)) {
                    beforeEach = m;
                    beforeEach.setAccessible(true);
                } else if (m.isAnnotationPresent(AfterEach.class)) {
                    afterEach = m;
                    afterEach.setAccessible(true);
                } else if (m.isAnnotationPresent(Test.class)) {
                    testMethods.add(m);
                    m.setAccessible(true);
                }
            }

            for (Method testMethod : testMethods) {
                total++;
                AutoCloseable mocks = null;
                try {
                    Object instance = clazz.getDeclaredConstructor().newInstance();
                    mocks = MockitoAnnotations.openMocks(instance);

                    if (beforeEach != null) {
                        beforeEach.invoke(instance);
                    }

                    testMethod.invoke(instance);

                    if (afterEach != null) {
                        afterEach.invoke(instance);
                    }

                    passed++;
                    System.out.println("  [PASS] " + testMethod.getName());
                } catch (Throwable t) {
                    failed++;
                    Throwable cause = t.getCause() != null ? t.getCause() : t;
                    System.err.println("  [FAIL] " + testMethod.getName() + " -> " + cause.getMessage());
                    failureMessages.add(clazz.getSimpleName() + "." + testMethod.getName() + ": " + cause.getMessage());
                } finally {
                    if (mocks != null) {
                        try {
                            mocks.close();
                        } catch (Exception ignored) {}
                    }
                }
            }
        }

        System.out.println("\n================================================================================");
        System.out.println(String.format("TEST EXECUTION SUMMARY: Total: %d, Passed: %d, Failed: %d", total, passed, failed));
        System.out.println("================================================================================");

        if (failed > 0) {
            System.err.println("\nFailures:");
            for (String msg : failureMessages) {
                System.err.println("  * " + msg);
            }
            System.exit(1);
        } else {
            System.out.println("\nALL TASK T5 TESTS PASSED SUCCESSFULLY! (100% SUCCESS RATE)");
            System.exit(0);
        }
    }
}

