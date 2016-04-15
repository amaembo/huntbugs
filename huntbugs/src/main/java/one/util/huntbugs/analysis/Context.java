/*
 * Copyright 2015, 2016 Tagir Valeev
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import com.strobel.assembler.metadata.ClasspathTypeLoader;
import com.strobel.assembler.metadata.CompositeTypeLoader;
import com.strobel.assembler.metadata.ITypeLoader;
import com.strobel.assembler.metadata.MetadataSystem;
import com.strobel.assembler.metadata.TypeDefinition;

import one.util.huntbugs.registry.DetectorRegistry;
import one.util.huntbugs.repo.Repository;
import one.util.huntbugs.repo.RepositoryVisitor;
import one.util.huntbugs.warning.Messages;
import one.util.huntbugs.warning.Warning;
import one.util.huntbugs.warning.WarningType;

/**
 * @author lan
 *
 */
public class Context {
    private final List<ErrorMessage> errors = Collections.synchronizedList(new ArrayList<>());
    private final List<Warning> warnings = Collections.synchronizedList(new ArrayList<>());
    private final DetectorRegistry registry;
    private final MetadataSystem ms;
    private final Repository repository;
    private final AtomicInteger classesCount = new AtomicInteger();
    private int totalClasses = 0;
    private final AnalysisOptions options;
    private final List<AnalysisListener> listeners = new CopyOnWriteArrayList<>();
    private final Map<String, Long> stat = new ConcurrentHashMap<>();
    private Messages msgs;

    public Context(Repository repository, AnalysisOptions options) {
        this.options = options;
        registry = new DetectorRegistry(this);
        this.repository = repository == null ? Repository.createNullRepository() : repository;
        ITypeLoader loader = this.repository.createTypeLoader();
        if (options.addBootClassPath) {
            loader = new CompositeTypeLoader(new ClasspathTypeLoader(System.getProperty("sun.boot.class.path")), loader);
        }
        ms = new MetadataSystem(loader);
    }
    
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

    boolean fireEvent(String stepName, String className) {
        for (AnalysisListener listener : listeners) {
            if (!listener.eventOccurred(stepName, className))
                return false;
        }
        return true;
    }

    public void analyzePackage(String name) {
        if (!fireEvent("Gathering statistics", null))
            return;
        List<String> classes = new ArrayList<>();
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
            for (String className : classes) {
                if (!fireEvent("Preparing", className))
                    return;
                classesCount.incrementAndGet();
                TypeDefinition type;
                try {
                    type = ms.resolve(ms.lookupType(className));
                } catch (Throwable t) {
                    addError(new ErrorMessage(null, className, null, null, -1, t));
                    continue;
                }
                if (type != null)
                    registry.populateDatabases(type);
            }
        }
        classesCount.set(0);
        for (String className : classes) {
            if (!fireEvent("Analyzing class", className))
                return;
            analyzeClass(className);
        }
    }

    void analyzeClass(String name) {
        classesCount.incrementAndGet();
        TypeDefinition type;
        try {
            type = ms.resolve(ms.lookupType(name));
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
    
    public Stream<Warning> warnings() {
        return warnings.stream();
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
    
    public WarningType getWarningType(String typeName) {
        return registry.getWarningType(typeName);
    }
}
