// SPDX-License-Identifier: MIT
package dev.aesi.planner;
import java.util.*;
public final class ModuleAccessTests {
    private static int checks;
    private static void check(boolean condition){checks++;if(!condition)throw new AssertionError("Module check "+checks);}
    public static void main(String[] args){
        var low=new ModuleAccess.Grant("mi/compressor",32);
        var high=new ModuleAccess.Grant("mi/compressor",512);
        var cap=ModuleAccess.union(List.of(List.of(low),List.of(low)));
        check(cap.equals(Map.of("mi/compressor",32L)));
        check(!ModuleAccess.allows(Map.of(),"mi/compressor",0));
        for(long value:new long[]{0,1,8,31,32})check(ModuleAccess.allows(cap,"mi/compressor",value));
        for(long value:new long[]{-1,33,128,512,Long.MAX_VALUE})check(!ModuleAccess.allows(cap,"mi/compressor",value));
        for(String type:List.of("mi/wiremill","mi/chemical_reactor","mek/compressor","crafting","mi/compressor_extra","mi"))
            check(!ModuleAccess.allows(cap,type,0));
        var merged=ModuleAccess.union(List.of(List.of(low),List.of(high,new ModuleAccess.Grant("crafting",Long.MAX_VALUE))));
        check(ModuleAccess.allows(merged,"mi/compressor",512));
        check(!ModuleAccess.allows(merged,"mi/compressor",513));
        check(ModuleAccess.allows(merged,"crafting",Long.MAX_VALUE));
        check(!ModuleAccess.allows(merged,"smelting",0));
        check(!ModuleAccess.allows(cap,"mi/compressor",128));
        for(String type:List.of("", " ", "mi/*")) {
            boolean rejected=false;try{new ModuleAccess.Grant(type,32);}catch(IllegalArgumentException e){rejected=true;}
            check(rejected);
        }
        boolean rejected=false;try{new ModuleAccess.Grant("mi/a",-1);}catch(IllegalArgumentException e){rejected=true;}
        check(rejected);
        try{merged.put("any",1L);throw new AssertionError("mutable snapshot");}catch(UnsupportedOperationException expected){check(true);}
        System.out.println(checks+" assertions passed");
    }
}
