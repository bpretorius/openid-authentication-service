package com.openbanking.authentication.mfa;

import org.springframework.security.core.context.DeferredSecurityContext;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.function.Supplier;

public class SupplierDeferredSecurityContext implements DeferredSecurityContext {

    private final Supplier<SecurityContext> securityContextSupplier;

    public SupplierDeferredSecurityContext(Supplier<SecurityContext> securityContextSupplier) {
        this.securityContextSupplier = securityContextSupplier;
    }

    @Override
    public boolean isGenerated() {
        // You can add logic here as needed, e.g., return true or false
        return true;
    }

    @Override
    public SecurityContext get() {
        SecurityContext context = securityContextSupplier.get();
        // Ensure the context is never null
        return context != null ? context : SecurityContextHolder.createEmptyContext();
    }
}
