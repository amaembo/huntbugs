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
package one.util.huntbugs.registry;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.strobel.assembler.metadata.MetadataSystem;
import com.strobel.assembler.metadata.MethodBody;
import com.strobel.assembler.metadata.MethodDefinition;
import com.strobel.assembler.metadata.MethodHandle;
import com.strobel.assembler.metadata.TypeDefinition;
import com.strobel.decompiler.DecompilerContext;
import com.strobel.decompiler.ast.AstBuilder;
import com.strobel.decompiler.ast.AstOptimizationStep;
import com.strobel.decompiler.ast.AstOptimizer;
import com.strobel.decompiler.ast.Block;
import com.strobel.decompiler.ast.Lambda;
import com.strobel.decompiler.ast.Node;

import one.util.huntbugs.analysis.Context;
import one.util.huntbugs.analysis.ErrorMessage;
import one.util.huntbugs.assertions.MethodAsserter;
import one.util.huntbugs.flow.ValuesFlow;
import one.util.huntbugs.registry.anno.WarningDefinition;
import one.util.huntbugs.repo.Repository;
import one.util.huntbugs.repo.RepositoryVisitor;
import one.util.huntbugs.util.NodeChain;
import one.util.huntbugs.warning.Messages.Message;
import one.util.huntbugs.warning.WarningAnnotation;
import one.util.huntbugs.warning.WarningType;

/**
 * @author lan
 *
 */
public class DetectorRegistry {
    private static final WarningType METHOD_TOO_LARGE = new WarningType("System", "MethodTooLarge", 30);
    
    static final String DETECTORS_PACKAGE = "one.util.huntbugs.detect";
    
    private final Map<WarningType, Detector> typeToDetector = new HashMap<>();
    private final List<Detector> detectors = new ArrayList<>();
    private final Context ctx;
    private final Detector systemDetector;

    private final DatabaseRegistry databases;
    
    public static class SystemDetector {}

    public DetectorRegistry(Context ctx) {
        this.ctx = ctx;
        this.databases = new DatabaseRegistry(ctx);
        ctx.incStat("WarningTypes.Total");
        Map<String, WarningType> systemWarnings = createWarningMap(Stream.of(METHOD_TOO_LARGE));
        try {
            this.systemDetector = createDetector(SystemDetector.class, systemWarnings);
        } catch (IllegalAccessException e) {
            throw new InternalError(e);
        }
        init();
    }

    private Map<String, WarningType> createWarningMap(Stream<WarningType> stream) {
        Map<String, WarningType> systemWarnings = stream.map(ctx.getOptions().getRule()::adjust)
                .collect(Collectors.toMap(WarningType::getName, Function.identity()));
        return systemWarnings;
    }

    private List<WarningDefinition> getDefinitions(Class<?> clazz) {
        WarningDefinition[] wds = clazz.getAnnotationsByType(WarningDefinition.class);
        if (wds == null)
            return Collections.emptyList();
        return Arrays.asList(wds);
    }

    boolean addDetector(Class<?> clazz) {
        List<WarningDefinition> wds = getDefinitions(clazz);
        if (wds.isEmpty())
            return false;
        try {
            wds.forEach(wd -> ctx.incStat("WarningTypes.Total"));
            Map<String, WarningType> wts = createWarningMap(wds.stream().map(WarningType::new));
            Detector detector = createDetector(clazz, wts);
            if(detector == null)
                return false;
            detectors.add(detector);
        } catch (Exception e) {
            ctx.addError(new ErrorMessage(clazz.getName(), null, null, null, -1, e));
        }
        return true;
    }

    private Detector createDetector(Class<?> clazz, Map<String, WarningType> wts) throws IllegalAccessException {
        List<WarningType> activeWts = wts.values().stream().filter(
            wt -> wt.getMaxScore() >= ctx.getOptions().minScore).collect(Collectors.toList());
        if(activeWts.isEmpty())
            return null;
        Detector detector = new Detector(wts, clazz, databases);
        activeWts.forEach(wt -> {
            typeToDetector.put(wt, detector);
            ctx.incStat("WarningTypes");
        });
        return detector;
    }

    void init() {
        Repository repo = Repository.createSelfRepository();
        String pkg = DETECTORS_PACKAGE.replace('.', '/');
        repo.visit(pkg, new RepositoryVisitor() {
            @Override
            public boolean visitPackage(String packageName) {
                return packageName.equals(pkg);
            }

            @Override
            public void visitClass(String className) {
                String name = className.replace('/', '.');
                try {
                    ctx.incStat("Detectors.Total");
                    if(addDetector(MetadataSystem.class.getClassLoader().loadClass(name)))
                        ctx.incStat("Detectors");
                } catch (ClassNotFoundException e) {
                    ctx.addError(new ErrorMessage(name, null, null, null, -1, e));
                }
            }
        });
    }

    private void visitChildren(Node node, NodeChain parents, List<MethodContext> list, MethodDefinition realMethod, boolean isAnnotationComplete) {
        for (MethodContext mc : list) {
            mc.visitNode(node, parents, realMethod);
        }
        if(node instanceof Lambda) {
            Object arg = ((Lambda)node).getCallSite().getBootstrapArguments().get(1);
            if(arg instanceof MethodHandle) {
                MethodDefinition lm = ((MethodHandle) arg).getMethod().resolve();
                if(lm != null) realMethod = lm;
            }
        }
        List<Node> children = node.getChildren();
        if (!children.isEmpty()) {
            NodeChain newChain = new NodeChain(parents, node);
            for (Node child : children)
                visitChildren(child, newChain, list, realMethod, isAnnotationComplete);
        }
    }
    
    public boolean hasDatabases() {
        return !databases.instances.isEmpty();
    }
    
    public void populateDatabases(TypeDefinition type) {
        databases.visitType(type);
        for (TypeDefinition subType : type.getDeclaredTypes()) {
            populateDatabases(subType);
        }
    }

    public void analyzeClass(TypeDefinition type) {
        ctx.incStat("TotalClasses");
        ClassContext[] ccs = detectors.stream().map(d -> new ClassContext(ctx, type, d)).filter(
            ClassContext::visitClass).toArray(ClassContext[]::new);

        for (MethodDefinition md : type.getDeclaredMethods()) {
            MethodAsserter ma = MethodAsserter.forMethod(md);

            Map<Boolean, List<MethodContext>> mcs = Stream.of(ccs).map(cc -> cc.forMethod(md)).peek(mc -> mc.setMethodAsserter(ma))
                    .collect(Collectors.partitioningBy(MethodContext::visitMethod));

            MethodBody body = md.getBody();
            if (body != null) {
                if(body.getCodeSize() > ctx.getOptions().maxMethodSize) {
                    if(systemDetector != null) {
                        MethodContext mc = new ClassContext(ctx, type, systemDetector).forMethod(md);
                        mc.setMethodAsserter(ma);
                        mc.report(METHOD_TOO_LARGE.getName(), 0, new WarningAnnotation<>("BYTECODE_SIZE", body
                                .getCodeSize()), new WarningAnnotation<>("LIMIT", ctx.getOptions().maxMethodSize));
                        mc.finalizeMethod();
                    }
                } else if (!mcs.get(true).isEmpty()) {
                    final DecompilerContext context = new DecompilerContext();
    
                    context.setCurrentMethod(md);
                    context.setCurrentType(type);
                    Block methodAst = new Block();
                    boolean isAnnotationComplete = false;
                    try {
                        methodAst.getBody().addAll(AstBuilder.build(body, true, context));
                        AstOptimizer.optimize(context, methodAst, AstOptimizationStep.None);
                        isAnnotationComplete = ValuesFlow.annotate(ctx, md, methodAst);
                    } catch (Throwable t) {
                        ctx.addError(new ErrorMessage(null, type.getFullName(), md.getFullName(), md.getSignature(), -1, t));
                    }
                    visitChildren(methodAst, null, mcs.get(true), md, isAnnotationComplete);
                }
            }
            for (MethodContext mc : mcs.get(true)) {
                mc.finalizeMethod();
            }
            for (MethodContext mc : mcs.get(false)) {
                mc.finalizeMethod();
            }
            ma.checkFinally(new MethodContext(ctx, null, md));
        }

        for (TypeDefinition subType : type.getDeclaredTypes()) {
            analyzeClass(subType);
        }
    }
    
    private void printTree(PrintStream out, List<String> result, String arrow) {
        result.sort(null);
        String lastCategory = arrow;
        for(int i=0; i<result.size(); i++) {
            String str = result.get(i);
            if(str.startsWith(lastCategory)) {
                out.printf("%" + lastCategory.length() + "s%s%n", i == result.size() - 1
                    || !result.get(i + 1).startsWith(lastCategory) ? "\\-> " : "|-> ", str.substring(lastCategory
                        .length()));
            } else {
                out.println(str);
                lastCategory = str.substring(0, str.indexOf(arrow)+arrow.length());
            }
        }
    }

    public void reportWarningTypes(PrintStream out) {
        List<String> result = new ArrayList<>();
        
        String arrow = " --> ";
        typeToDetector.forEach((wt, detector) -> {
            result.add(wt.getCategory()+arrow+wt.getName()+arrow+detector);
        });
        printTree(out, result, arrow);
        out.println("Total types: "+typeToDetector.size());
    }

    public void reportDatabases(PrintStream out) {
        List<String> result = new ArrayList<>();
        String arrow = " --> ";
        detectors.forEach(det -> det.dbFetchers.keySet().forEach(db -> result.add(db.getName()+arrow+det)));
        databases.instances.forEach((db, dbi) -> {
            if(dbi.parentDb != null) {
                result.add(dbi.parentDb.getClass().getName()+arrow+"Derived DB: "+db.getName());
            }
        });
        printTree(out, result, arrow);
        out.println("Total databases: "+databases.instances.size());
    }
    
    public void reportTitles(PrintStream out) {
        List<String> rows = new ArrayList<>();
        for(WarningType wt : typeToDetector.keySet()) {
            Message msg = ctx.getMessages().getMessagesForType(wt);
            ctx.incStat("Messages.Total");
            if(msg.getTitle().equals(wt.getName())) {
                rows.add(wt.getName()+": ?");
            } else {
                ctx.incStat("Messages");
                rows.add(wt.getName()+": "+msg.getTitle());
            }
        }
        rows.sort(null);
        rows.forEach(out::println);
    }

    public WarningType getWarningType(String typeName) {
        return typeToDetector.keySet().stream().filter(wt -> wt.getName().equals(typeName)).findFirst().orElse(null);
    }
}
