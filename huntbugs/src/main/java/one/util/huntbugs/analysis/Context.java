/*
 * Copyright 2016 HuntBugs contributors
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package one.util.huntbugs.analysis;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import com.strobel.assembler.ir.ConstantPool;
import com.strobel.assembler.ir.ConstantPool.TypeInfoEntry;
import com.strobel.assembler.metadata.ClasspathTypeLoader;
import com.strobel.assembler.metadata.CompositeTypeLoader;
import com.strobel.assembler.metadata.ITypeLoader;
import com.strobel.assembler.metadata.MetadataSystem;
import com.strobel.assembler.metadata.TypeDefinition;
import com.strobel.assembler.metadata.TypeReference;

import one.util.huntbugs.registry.DetectorRegistry;
import one.util.huntbugs.repo.Repository;
import one.util.huntbugs.repo.RepositoryVisitor;
import one.util.huntbugs.warning.Messages;
import one.util.huntbugs.warning.Warning;
import one.util.huntbugs.warning.WarningType;

/**
 * @author Tagir Valeev
 *
 */
public class Context implements HuntBugsResult {
    private final List<ErrorMessage> errors = Collections.synchronizedList(new ArrayList<>());
    private final List<Warning> warnings = Collections.synchronizedList(new ArrayList<>());
    private final Set<String> missingClasses = ConcurrentHashMap.newKeySet();
    private final Set<String> classes = ConcurrentHashMap.newKeySet();
    private final DetectorRegistry registry;
    private final Repository repository;
    private final AtomicInteger classesCount = new AtomicInteger();
    private int totalClasses = 0;
    private final AnalysisOptions options;
    private final List<AnalysisListener> listeners = new CopyOnWriteArrayList<>();
    private final Map<String, Long> stat = new ConcurrentHashMap<>();
    private Messages msgs;
    private final ITypeLoader loader;

    public Context(Repository repository, AnalysisOptions options) {
        this.options = options;
        registry = new DetectorRegistry(this);
        this.repository = repository == null ? Repository.createNullRepository() : repository;
        ITypeLoader loader = this.repository.createTypeLoader();
        if (options.addBootClassPath) {
            loader = new CompositeTypeLoader(new ClasspathTypeLoader(System.getProperty("sun.boot.class.path")), loader);
        }
        this.loader = loader;
    }
    
    @Override
    public Messages getMessages() {
        if(msgs == null) {
            msgs = Messages.load();
        }
        return msgs;
    }

    public AnalysisOptions getOptions() {
        return options;
    }

    public void addListener(AnalysisListener listener) {
        listeners.add(listener);
    }

    boolean fireEvent(String stepName, String className, int step, int total) {
        for (AnalysisListener listener : listeners) {
            if (!listener.eventOccurred(stepName, className, step, total))
                return false;
        }
        return true;
    }

    public void analyzePackage(String name) {
        if (!fireEvent("Preparing", null, 0, 0))
            return;
        Set<String> classes = new TreeSet<>();
        repository.visit(name, new RepositoryVisitor() {
            @Override
            public boolean visitPackage(String packageName) {
                return true;
            }

            @Override
            public void visitClass(String className) {
                classes.add(className);
            }
        });
        totalClasses = classes.size();
        if(registry.hasDatabases()) {
            if(!preparingClasses(classes))
                return;
        }
        analyzingClasses(classes);
    }

    private boolean preparingClasses(Set<String> classes) {
        MetadataSystem ms = createMetadataSystem();
        Set<String> auxClasses = new TreeSet<>();
        int count = 0;
        for (String className : classes) {
            if (!fireEvent("Reading classes", className, count, classes.size()))
                return false;
            if(++count % options.classesPerFlush == 0) {
                ms = createMetadataSystem();
            }
            TypeDefinition type;
            try {
                type = lookUp(ms, className);
            } catch (Throwable t) {
                addError(new ErrorMessage(null, className, null, null, -1, t));
                continue;
            }
            if (type != null) {
                for(ConstantPool.Entry entry : type.getConstantPool()) {
                    if(entry instanceof TypeInfoEntry) {
                        String depName = getMainType(((TypeInfoEntry)entry).getName());
                        if(depName != null && !classes.contains(depName))
                            auxClasses.add(depName);
                    }
                }
                registry.populateDatabases(type);
            }
        }
        if (!fireEvent("Reading classes", null, classes.size(), classes.size()))
            return false;
        ms = createMetadataSystem();
        count = 0;
        for (String className : auxClasses) {
            if (!fireEvent("Reading dep classes", className, count, auxClasses.size()))
                return false;
            if(++count % options.classesPerFlush == 0) {
                ms = createMetadataSystem();
            }
            TypeDefinition type;
            try {
                type = lookUp(ms, className);
            } catch (Throwable t) {
                addError(new ErrorMessage(null, className, null, null, -1, t));
                continue;
            }
            if (type != null)
                registry.populateDatabases(type);
        }
        return fireEvent("Reading dep classes", null, auxClasses.size(), auxClasses.size());
    }

    MetadataSystem createMetadataSystem() {
        return new MetadataSystem(loader) {
            Set<String> loadedTypes = new HashSet<>();
            
            @Override
            protected TypeDefinition resolveType(String descriptor, boolean mightBePrimitive) {
                if(missingClasses.contains(descriptor)) {
                    return null;
                }
                try {
                    if(loadedTypes.add(descriptor))
                        incStat("ClassLoadingEfficiency.Total");
                    if(classes.add(descriptor))
                        incStat("ClassLoadingEfficiency");
                    return super.resolveType(descriptor, mightBePrimitive);
                } catch (Throwable t) {
                    addError(new ErrorMessage(null, descriptor, null, null, -1, t));
                    missingClasses.add(descriptor);
                    return null;
                }
            }
        };
    }

    private TypeDefinition lookUp(MetadataSystem ms, String className) {
        TypeReference tr = ms.lookupType(className);
        if(tr == null) {
            missingClasses.add(className);
            return null;
        }
        return ms.resolve(tr);
    }

    private void analyzingClasses(Set<String> classes) {
        MetadataSystem ms = createMetadataSystem();
        classesCount.set(0);
        for (String className : classes) {
            if(classesCount.get() % options.classesPerFlush == 0)
                ms = createMetadataSystem();
            if (!fireEvent("Analyzing classes", className, classesCount.get(), classes.size()))
                return;
            analyzeClass(ms, className);
        }
        if (!fireEvent("Analyzing classes", null, classes.size(), classes.size()))
            return;
    }

    void analyzeClass(MetadataSystem ms, String name) {
        classesCount.incrementAndGet();
        TypeDefinition type;
        try {
            type = lookUp(ms, name);
        } catch (Throwable t) {
            addError(new ErrorMessage(null, name, null, null, -1, t));
            return;
        }
        if (type != null)
            registry.analyzeClass(type);
    }

    public void addError(ErrorMessage msg) {
        incStat("InternalErrors");
        errors.add(msg);
    }

    public void addWarning(Warning warning) {
        if(warning.getScore() < getOptions().minScore)
            return;
        incStat("Warnings");
        warnings.add(warning);
    }
    
    @Override
    public Stream<Warning> warnings() {
        return warnings.stream();
    }

    @Override
    public Stream<ErrorMessage> errors() {
        return errors.stream();
    }

    public void reportWarnings(PrintStream app) {
        List<Warning> warns = new ArrayList<>(warnings);
        warns.sort(Comparator.comparingInt(Warning::getScore).reversed().thenComparing(w -> w.getType().getName())
            .thenComparing(Warning::getClassName));
        warns.forEach(w -> app.append(w.toString()).append("\n"));
    }

    public void reportStats(PrintStream app) {
        if (stat.isEmpty())
            return;
        app.append("Statistics:\n");
        stat.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> {
            String key = e.getKey();
            Long value = e.getValue();
            if(stat.containsKey(key+".Total"))
                return;
            if(key.endsWith(".Total")) {
                String subKey = key.substring(0, key.length()-".Total".length());
                Long part = stat.getOrDefault(subKey, 0L);
                app.printf(Locale.ENGLISH, "\t%s: %d of %d (%.2f%%)%n", subKey, part, value, part*100.0/value);
            } else 
                app.printf(Locale.ENGLISH, "\t%s: %d%n", key, value);
        });
    }

    public void reportErrors(PrintStream app) {
        errors.forEach(msg -> app.append(msg.toString()).append("\n"));
    }

    public void reportWarningTypes(PrintStream out) {
        registry.reportWarningTypes(out);
    }

    public void reportDatabases(PrintStream out) {
        registry.reportDatabases(out);
    }
    
    public void reportTitles(PrintStream out) {
        registry.reportTitles(out);
    }

    public int getClassesCount() {
        return classesCount.get();
    }

    public int getTotalClasses() {
        return totalClasses;
    }

    public int getErrorCount() {
        return errors.size();
    }

    public void incStat(String key) {
        stat.merge(key, 1L, Long::sum);
    }
    
    public Stream<WarningType> warningTypes() {
        return registry.warningTypes();
    }
    
    public WarningType getWarningType(String typeName) {
        return registry.getWarningType(typeName);
    }

    public long getStat(String key) {
        return stat.getOrDefault(key, 0L);
    }

    private static String getMainType(String internalName) {
        if(internalName.startsWith("[")) {
            if(!internalName.endsWith(";"))
                return null;
            int pos = 0;
            while(internalName.charAt(pos) == '[') pos++;
            if(internalName.charAt(pos++) != 'L')
                return null;
            internalName = internalName.substring(pos, internalName.length()-1);
        }
        int lastSlash = internalName.lastIndexOf('/');
        int dollar = internalName.indexOf('$');
        if(dollar > lastSlash) {
            return internalName.substring(0, dollar);
        }
        return internalName;
    }
}
