// SPDX-License-Identifier: MIT
package dev.aesi.planner;
import java.util.*;
/** Exact process permissions; output and recipe namespaces do not grant another process. */
public final class ModuleAccess {
    private ModuleAccess() {}
    public record Grant(String family,long maxEu) {
        public Grant {
            if(family==null||family.isBlank()||family.contains("*")||maxEu<0)throw new IllegalArgumentException("Invalid process grant");
        }
    }
    public static Map<String,Long> union(Collection<? extends Collection<Grant>> datasets) {
        var result=new HashMap<String,Long>();
        for(var dataset:datasets)for(var grant:dataset)result.merge(grant.family(),grant.maxEu(),Math::max);
        return Map.copyOf(result);
    }
    public static boolean allows(Map<String,Long> capabilities,String family,long requiredEu) {
        var maximum=capabilities.get(family);
        return maximum!=null&&requiredEu>=0&&requiredEu<=maximum;
    }
}
