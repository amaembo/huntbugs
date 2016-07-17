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
package one.util.huntbugs.registry;

import java.io.PrintStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.strobel.assembler.ir.Instruction;
import com.strobel.assembler.ir.OpCode;
import com.strobel.assembler.metadata.MetadataSystem;
import com.strobel.assembler.metadata.MethodBody;
import com.strobel.assembler.metadata.MethodDefinition;
import com.strobel.assembler.metadata.MethodReference;
import com.strobel.assembler.metadata.TypeDefinition;
import com.strobel.assembler.metadata.TypeReference;
import com.strobel.decompiler.DecompilerContext;
import com.strobel.decompiler.ast.AstBuilder;
import com.strobel.decompiler.ast.AstOptimizationStep;
import com.strobel.decompiler.ast.AstOptimizer;
import com.strobel.decompiler.ast.Block;
import com.strobel.decompiler.ast.Lambda;
import com.strobel.decompiler.ast.Node;

import one.util.huntbugs.analysis.Context;
import one.util.huntbugs.analysis.ErrorMessage;
import one.util.huntbugs.db.FieldStats;
import one.util.huntbugs.db.MethodStats;
import one.util.huntbugs.flow.CFG;
import one.util.huntbugs.flow.ClassFields;
import one.util.huntbugs.flow.ValuesFlow;
import one.util.huntbugs.registry.anno.WarningDefinition;
import one.util.huntbugs.repo.Repository;
import one.util.huntbugs.repo.RepositoryVisitor;
import one.util.huntbugs.util.NodeChain;
import one.util.huntbugs.util.Nodes;
import one.util.huntbugs.warning.Messages.Message;
import one.util.huntbugs.warning.Role.NumberRole;
import one.util.huntbugs.warning.WarningType;

/**
 * @author Tagir Valeev
 *
 */
public class DetectorRegistry {
    private static final WarningType METHOD_TOO_LARGE = new WarningType("System", "MethodTooLarge", 30);
    private static final NumberRole BYTECODE_SIZE = NumberRole.forName("BYTECODE_SIZE");
    private static final NumberRole LIMIT = NumberRole.forName("LIMIT");

    static final String DETECTORS_PACKAGE = "one.util.huntbugs.detect";

    private final Map<WarningType, Detector> typeToDetector = new HashMap<>();
    private final List<Detector> detectors = new ArrayList<>();
    private final Context ctx;
    private final Detector systemDetector;

    private final DatabaseRegistry databases;
    private final Function<TypeReference, FieldStats> fieldStatsDb;
    private final Function<TypeReference, MethodStats> methodStatsDb;

    public static class SystemDetector {
    }

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
        this.fieldStatsDb = databases.queryDatabase(FieldStats.class);
        this.methodStatsDb = databases.queryDatabase(MethodStats.class);
        init();
    }

    private Map<String, WarningType> createWarningMap(Stream<WarningType> stream) {
        Map<String, WarningType> systemWarnings = stream.map(ctx.getOptions().getRule()::adjust).collect(
            Collectors.toMap(WarningType::getName, Function.identity()));
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
            if (detector == null)
                return false;
            detectors.add(detector);
        } catch (Exception e) {
            ctx.addError(new ErrorMessage(clazz.getName(), null, null, null, -1, e));
        }
        return true;
    }

    private Detector createDetector(Class<?> clazz, Map<String, WarningType> wts) throws IllegalAccessException {
        List<WarningType> activeWts = wts.values().stream().filter(wt -> wt.getMaxScore() >= ctx.getOptions().minScore)
                .collect(Collectors.toList());
        if (activeWts.isEmpty())
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
                    if (addDetector(MetadataSystem.class.getClassLoader().loadClass(name)))
                        ctx.incStat("Detectors");
                } catch (ClassNotFoundException e) {
                    ctx.addError(new ErrorMessage(name, null, null, null, -1, e));
                }
            }
        });
    }

    private void visitChildren(Node node, NodeChain parents, List<MethodContext> list, MethodData mdata) {
        if (node instanceof Lambda) {
            MethodDefinition curMethod = mdata.realMethod;
            CFG curCFG = mdata.cfg;
            mdata.realMethod = Nodes.getLambdaMethod((Lambda) node);
            mdata.cfg = curCFG.getLambdaCFG((Lambda) node);
            Iterable<Node> children = Nodes.getChildren(node);
            NodeChain newChain = new NodeChain(parents, node);
            for (Node child : children)
                visitChildren(child, newChain, list, mdata);
            mdata.realMethod = curMethod;
            mdata.cfg = curCFG;
        } else {
            Iterable<Node> children = Nodes.getChildren(node);
            NodeChain newChain = new NodeChain(parents, node);
            for (Node child : children)
                visitChildren(child, newChain, list, mdata);
        }
        mdata.parents = parents;
        for (MethodContext mc : list) {
            mc.visitNode(node);
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
        
        ClassData cdata = new ClassData(type);
        ClassFields cf = new ClassFields(type, fieldStatsDb.apply(type), methodStatsDb.apply(type));
        
        List<MethodDefinition> declMethods = new ArrayList<>(type.getDeclaredMethods());
        sortMethods(declMethods);
        List<FieldData> fields = type.getDeclaredFields().stream().map(FieldData::new).collect(Collectors.toList());

        type.getDeclaredMethods().forEach(cdata::registerAsserter);
        type.getDeclaredFields().forEach(cdata::registerAsserter);

        ClassContext[] ccs = detectors.stream().map(d -> new ClassContext(ctx, cdata, d)).filter(
            ClassContext::visitClass).toArray(ClassContext[]::new);
        
        for (MethodDefinition md : declMethods) {
            if(!md.isSpecialName()) {
                cf.clearCtorData();
            }
            if(md.isSynthetic() && md.getName().startsWith("lambda$"))
                continue;
            MethodData mdata = new MethodData(md);

            Map<Boolean, List<MethodContext>> mcs = Stream.of(ccs).map(cc -> cc.forMethod(mdata)).collect(
                Collectors.partitioningBy(MethodContext::visitMethod));

            MethodBody body = md.getBody();
            if (body != null) {
                if (body.getCodeSize() > ctx.getOptions().maxMethodSize) {
                    if (systemDetector != null) {
                        MethodContext mc = new ClassContext(ctx, cdata, systemDetector).forMethod(mdata);
                        mc.report(METHOD_TOO_LARGE.getName(), 0, BYTECODE_SIZE.create(body.getCodeSize()), LIMIT.create(
                            ctx.getOptions().maxMethodSize));
                        mc.finalizeMethod();
                    }
                } else if (!mcs.get(true).isEmpty()) {
                    final DecompilerContext context = new DecompilerContext();

                    context.setCurrentMethod(md);
                    context.setCurrentType(type);
                    Block methodAst = new Block();
                    try {
                        methodAst.getBody().addAll(AstBuilder.build(body, true, context));
                        AstOptimizer.optimize(context, methodAst, AstOptimizationStep.None);
                        mdata.cfg = CFG.build(md, methodAst);
                        mdata.origParams = ValuesFlow.annotate(ctx, md, cf, mdata.cfg);
                        mdata.fullyAnalyzed = true;
                    } catch (Throwable t) {
                        ctx.addError(new ErrorMessage(null, type.getFullName(), md.getFullName(), md.getSignature(),
                                -1, t));
                    }
                    visitChildren(methodAst, null, mcs.get(true), mdata);
                }
            } else {
                mdata.fullyAnalyzed = true;
            }
            for (MethodContext mc : mcs.get(true)) {
                mc.visitAfterMethod();
                mc.finalizeMethod();
            }
            for (MethodContext mc : mcs.get(false)) {
                mc.finalizeMethod();
            }
        }
        for(FieldData fdata : fields) {
            for(ClassContext cc : ccs) {
                cc.forField(fdata).visitField();
            }
        }
        for(ClassContext cc : ccs) {
            cc.visitAfterClass();
        }
        cdata.finish(ctx);

        for (TypeDefinition subType : type.getDeclaredTypes()) {
            analyzeClass(subType);
        }
    }

    private void sortMethods(List<MethodDefinition> declMethods) {
        declMethods.sort(Comparator.comparingInt(md ->
                md.isTypeInitializer() ? 0 :
                    md.isConstructor() ? 1 : 2));
        int start = -1, end = declMethods.size();
        for (int i = 0; i < end; i++) {
            if(start == -1) {
                if(declMethods.get(i).isConstructor())
                    start = i;
            } else 
                if(!declMethods.get(i).isConstructor()) {
                    end = i;
            }
        }
        if(start >= 0) {
            sortConstructors(declMethods.subList(start, end));
        }
    }

    private void sortConstructors(List<MethodDefinition> ctors) {
        if(ctors.size() < 2)
            return;
        Map<MethodDefinition, MethodDefinition> deps = new HashMap<>();
        for(MethodDefinition ctor : ctors) {
            MethodBody body = ctor.getBody();
            if(body != null) {
                for(Instruction instr : body.getInstructions()) {
                    if(instr.getOpCode() == OpCode.INVOKESPECIAL) {
                        MethodReference mr = (MethodReference)instr.getOperand(0);
                        if(mr.getDeclaringType().isEquivalentTo(ctor.getDeclaringType()) && mr.isConstructor()) {
                            deps.put(ctor, mr.resolve());
                        }
                        break;
                    }
                }
            }
        }
        Set<MethodDefinition> result = new LinkedHashSet<>();
        for(MethodDefinition ctor : ctors) {
            Deque<MethodDefinition> chain = new ArrayDeque<>();
            MethodDefinition cur = ctor;
            while(cur != null && !result.contains(cur)) {
                chain.addFirst(cur);
                cur = deps.get(cur);
            }
            result.addAll(chain);
        }
        if(result.size() != ctors.size())
            throw new InternalError();
        int i=0;
        for(MethodDefinition ctor : result) {
            ctors.set(i++, ctor);
        }
    }

    private void printTree(PrintStream out, List<String> result, String arrow) {
        result.sort(null);
        String lastCategory = arrow;
        for (int i = 0; i < result.size(); i++) {
            String str = result.get(i);
            if (str.startsWith(lastCategory)) {
                out.printf("%" + lastCategory.length() + "s%s%n", i == result.size() - 1
                    || !result.get(i + 1).startsWith(lastCategory) ? "\\-> " : "|-> ", str.substring(lastCategory
                        .length()));
            } else {
                out.println(str);
                lastCategory = str.substring(0, str.indexOf(arrow) + arrow.length());
            }
        }
    }

    public void reportWarningTypes(PrintStream out) {
        List<String> result = new ArrayList<>();

        String arrow = " --> ";
        typeToDetector.forEach((wt, detector) -> {
            result.add(wt.getCategory() + arrow + wt.getName() + arrow + detector);
        });
        printTree(out, result, arrow);
        out.println("Total types: " + typeToDetector.size());
    }

    public void reportDatabases(PrintStream out) {
        List<String> result = new ArrayList<>();
        String arrow = " --> ";
        detectors.forEach(det -> det.dbFetchers.keySet().forEach(db -> result.add(db.getName() + arrow + det)));
        databases.instances.forEach((db, dbi) -> {
            if (dbi.parentDb != null) {
                result.add(dbi.parentDb.getClass().getName() + arrow + "Derived DB: " + db.getName());
            }
        });
        printTree(out, result, arrow);
        out.println("Total databases: " + databases.instances.size());
    }

    public void reportTitles(PrintStream out) {
        List<String> rows = new ArrayList<>();
        warningTypes().forEach(wt -> {
            Message msg = ctx.getMessages().getMessagesForType(wt);
            ctx.incStat("Messages.Total");
            if (msg.getTitle().equals(wt.getName())) {
                rows.add(wt.getName() + ": ?");
            } else {
                ctx.incStat("Messages");
                rows.add(wt.getName() + ": " + msg.getTitle());
            }
        });
        rows.sort(null);
        rows.forEach(out::println);
    }

    public WarningType getWarningType(String typeName) {
        return typeToDetector.keySet().stream().filter(wt -> wt.getName().equals(typeName)).findFirst().orElse(null);
    }
    
    public Stream<WarningType> warningTypes() {
        return typeToDetector.keySet().stream();
    }
}
