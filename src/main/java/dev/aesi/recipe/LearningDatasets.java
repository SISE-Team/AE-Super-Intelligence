// SPDX-License-Identifier: MIT
package dev.aesi.recipe;
import com.google.gson.Gson;
import dev.aesi.planner.ModuleAccess;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Finite process datasets shared by registered disks and recipe authorization. */
public final class LearningDatasets {
    public record Entry(String id,String mod,String kind,String station,List<ModuleAccess.Grant> grants,
            List<String> includes,List<String> ingredients,String recipe) {
        public Entry {
            grants=List.copyOf(grants);includes=List.copyOf(includes);ingredients=List.copyOf(ingredients);
            if(!id.matches("[a-z0-9_]+")||grants.isEmpty()||!Set.of("process","suite").contains(kind))
                throw new IllegalArgumentException("Invalid learning dataset "+id);
        }
        public boolean loaded(){return mod.equals("minecraft")||net.neoforged.fml.ModList.get().isLoaded(mod);}
    }
    private static final Map<String,Entry> ENTRIES=read();
    private static Map<String,Entry> read(){
        try(var stream=Objects.requireNonNull(LearningDatasets.class.getResourceAsStream("/data/aesi/learning/datasets.json"));
                var reader=new InputStreamReader(stream,StandardCharsets.UTF_8)) {
            var result=new LinkedHashMap<String,Entry>();
            for(var entry:new Gson().fromJson(reader,Entry[].class))
                if(result.put(entry.id(),entry)!=null)throw new IllegalArgumentException("Duplicate dataset "+entry.id());
            for(var entry:result.values())for(var dependency:entry.includes())
                if(!result.containsKey(dependency)||dependency.equals(entry.id()))throw new IllegalArgumentException("Unknown dataset prerequisite "+dependency);
            return Collections.unmodifiableMap(result);
        }catch(IOException error){throw new ExceptionInInitializerError(error);}
    }
    public static Collection<Entry> all(){return ENTRIES.values();}
    public static Entry get(String id){return ENTRIES.get(id);}
    public static Map<String,Long> capabilities(Set<String> installed){
        return ModuleAccess.union(installed.stream().map(ENTRIES::get).filter(Objects::nonNull)
                .filter(Entry::loaded).map(Entry::grants).toList());
    }
    public static String missing(String family,long eu){
        return ENTRIES.values().stream().filter(Entry::loaded)
                .filter(e->e.grants().stream().anyMatch(g->g.family().equals(family)&&g.maxEu()>=eu))
                .min(Comparator.comparingInt((Entry e)->e.grants().size())
                        .thenComparing(e->!e.mod().equals("minecraft")).thenComparing(Entry::id))
                .map(Entry::id).orElse("unsupported:"+family);
    }
}
