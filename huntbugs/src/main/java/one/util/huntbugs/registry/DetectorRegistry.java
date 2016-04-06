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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.strobel.assembler.metadata.MethodBody;
import com.strobel.assembler.metadata.MethodDefinition;
import com.strobel.assembler.metadata.TypeDefinition;
import com.strobel.decompiler.DecompilerContext;
import com.strobel.decompiler.ast.AstBuilder;
import com.strobel.decompiler.ast.AstOptimizationStep;
import com.strobel.decompiler.ast.AstOptimizer;
import com.strobel.decompiler.ast.Block;
import com.strobel.decompiler.ast.Node;

import one.util.huntbugs.analysis.Context;
import one.util.huntbugs.analysis.ErrorMessage;
import one.util.huntbugs.assertions.MethodAsserter;
import one.util.huntbugs.registry.anno.WarningDefinition;
import one.util.huntbugs.repo.CompositeRepository;
import one.util.huntbugs.repo.Repository;
import one.util.huntbugs.repo.RepositoryVisitor;
import one.util.huntbugs.util.NodeChain;
import one.util.huntbugs.warning.Warning;
import one.util.huntbugs.warning.WarningAnnotation;
import one.util.huntbugs.warning.WarningType;

/**
 * @author lan
 *
 */
public class DetectorRegistry {
    private static final WarningType METHOD_TOO_LARGE = new WarningType("System", "MethodTooLarge", 30);
    
    private static final String DETECTORS_PACKAGE = "one/util/huntbugs/detect";
    private final Map<WarningType, Detector> typeToDetector = new HashMap<>();
    private final List<Detector> detectors = new ArrayList<>();
    private final Context ctx;

    public DetectorRegistry(Context ctx) {
        this.ctx = ctx;
        init();
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
            Map<String, WarningType> wts = wds.stream().map(WarningType::new).collect(
                Collectors.toMap(WarningType::getName, Function.identity()));
            Detector detector = new Detector(wts, clazz);
            wts.values().forEach(wt -> typeToDetector.put(wt, detector));
            detectors.add(detector);
        } catch (Exception e) {
            ctx.addError(new ErrorMessage(clazz.getName(), null, null, null, -1, e));
        }
        return true;
    }

    void init() {
        CompositeRepository repo = Repository.createSelfRepository();
        repo.visit(DETECTORS_PACKAGE, new RepositoryVisitor() {
            @Override
            public boolean visitPackage(String packageName) {
                return packageName.equals(DETECTORS_PACKAGE);
            }

            @Override
            public void visitClass(String className) {
                String name = className.replace('/', '.');
                try {
                    addDetector(Class.forName(name));
                } catch (ClassNotFoundException e) {
                    ctx.addError(new ErrorMessage(name, null, null, null, -1, e));
                }
            }
        });
        System.out.println("Loaded " + detectors.size() + " detectors");
    }

    private void visitChildren(Node node, NodeChain parents, MethodContext[] mcs) {
        for (MethodContext mc : mcs) {
            mc.visitNode(node, parents);
        }
        List<Node> children = node.getChildren();
        if (!children.isEmpty()) {
            NodeChain newChain = new NodeChain(parents, node);
            for (Node child : children)
                visitChildren(child, newChain, mcs);
        }
    }

    public void analyzeClass(TypeDefinition type) {
        ClassContext[] ccs = detectors.stream().map(d -> new ClassContext(ctx, type, d)).toArray(ClassContext[]::new);

        for (MethodDefinition md : type.getDeclaredMethods()) {
            MethodAsserter ma = MethodAsserter.forMethod(md);

            MethodBody body = md.getBody();
            if (body != null) {
                if(body.getCodeSize() > ctx.getOptions().maxMethodSize) {
                    ctx.addWarning(new Warning(METHOD_TOO_LARGE, 0, Arrays.asList(WarningAnnotation.forType(type), WarningAnnotation
                            .forMethod(md), new WarningAnnotation<>("BYTECODE_SIZE", body.getCodeSize()))));
                } else {
                    final DecompilerContext context = new DecompilerContext();
    
                    context.setCurrentMethod(md);
                    context.setCurrentType(type);
                    final Block methodAst = new Block();
                    methodAst.getBody().addAll(AstBuilder.build(body, true, context));
                    AstOptimizer.optimize(context, methodAst, AstOptimizationStep.None);
    
                    MethodContext[] mcs = Stream.of(ccs).flatMap(cc -> cc.forMethod(md)).peek(
                        mc -> mc.setMethodAsserter(ma)).toArray(MethodContext[]::new);
    
                    visitChildren(methodAst, null, mcs);
    
                    for (MethodContext mc : mcs) {
                        mc.finalizeMethod();
                    }
                }
            }
            ma.checkFinally(new MethodContext(ctx, null, md));
        }

        for (TypeDefinition subType : type.getDeclaredTypes()) {
            analyzeClass(subType);
        }
    }
}
