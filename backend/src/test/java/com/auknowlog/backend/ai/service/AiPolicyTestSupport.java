package com.auknowlog.backend.ai.service;

public final class AiPolicyTestSupport {
    private AiPolicyTestSupport() {}
    public static AiUsagePolicyService passthroughPolicy() {
        return org.mockito.Mockito.mock(AiUsagePolicyService.class, invocation -> {
            if (invocation.getMethod().getName().equals("execute")) {
                return ((java.util.function.Supplier<?>) invocation.getArgument(3)).get();
            }
            return org.mockito.Mockito.RETURNS_DEFAULTS.answer(invocation);
        });
    }
}
