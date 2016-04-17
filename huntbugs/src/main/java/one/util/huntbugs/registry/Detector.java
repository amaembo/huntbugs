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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import com.strobel.assembler.metadata.MethodDefinition;
import com.strobel.assembler.metadata.TypeDefinition;
import com.strobel.assembler.metadata.TypeReference;
import com.strobel.decompiler.ast.Block;
import com.strobel.decompiler.ast.Expression;
import com.strobel.decompiler.ast.Node;

import one.util.huntbugs.registry.anno.AstNodes;
import one.util.huntbugs.registry.anno.AstVisitor;
import one.util.huntbugs.registry.anno.ClassVisitor;
import one.util.huntbugs.registry.anno.MethodVisitor;
import one.util.huntbugs.util.NodeChain;
import one.util.huntbugs.warning.WarningType;

/**
 * @author lan
 *
 */
public class Detector {
    static final MethodType METHOD_VISITOR_TYPE = MethodType.methodType(boolean.class, Object.class,
        MethodContext.class, MethodDefinition.class, TypeDefinition.class);
    static final MethodType CLASS_VISITOR_TYPE = MethodType.methodType(boolean.class, Object.class,
        ClassContext.class, TypeDefinition.class);
    private static final MethodHandle ALWAYS_TRUE = MethodHandles.constant(boolean.class, true);
    private static final MethodType NODE_VISITOR_TYPE = MethodType.methodType(boolean.class, Object.class, Node.class,
        NodeChain.class, MethodContext.class, MethodDefinition.class, TypeDefinition.class);

    private final Map<String, WarningType> wts;
    final Map<Class<?>, Function<TypeReference, ?>> dbFetchers = new HashMap<>();
    private final Class<?> clazz;
    final List<VisitorInfo> astVisitors = new ArrayList<>();
    final List<MethodHandle> methodVisitors = new ArrayList<>();
    final List<MethodHandle> classVisitors = new ArrayList<>();

    class VisitorInfo {
        final VisitorType type;
        final MethodHandle mh;
        final AstVisitor anno;

        public VisitorInfo(AstVisitor anno, VisitorType type, MethodHandle mh) {
            this.anno = anno;
            this.type = type;
            this.mh = mh;
        }

        public MethodHandle bind(TypeDefinition td) {
            MethodHandle mh = this.mh;
            mh = bindDatabases(type.wantedType.parameterCount(), td, mh);
            return type.adapt(mh);
        }

        public boolean isApplicable(MethodDefinition md) {
            if (!anno.methodName().isEmpty() && !anno.methodName().equals(md.getName()))
                return false;
            if (!anno.methodSignature().isEmpty() && !anno.methodSignature().equals(md.getSignature()))
                return false;
            return true;
        }
    }

    static enum VisitorType {
        // All
        AST_NODE_VISITOR(AstNodes.ALL, MethodType.methodType(boolean.class, Object.class, Node.class, NodeChain.class,
            MethodContext.class, MethodDefinition.class, TypeDefinition.class), null),
        // Expressions
        AST_EXPRESSION_VISITOR(AstNodes.EXPRESSIONS, MethodType.methodType(boolean.class, Object.class,
            Expression.class, NodeChain.class, MethodContext.class, MethodDefinition.class, TypeDefinition.class),
                "runExpression"),
        // Root
        AST_BODY_VISITOR(AstNodes.ROOT, MethodType.methodType(void.class, Object.class, Block.class,
            MethodContext.class, MethodDefinition.class, TypeDefinition.class), "runBody");

        AstNodes nodeTypes;
        MethodType wantedType;
        private MethodHandle adapter;

        static boolean runBody(MethodHandle mh, Object det, Node n, NodeChain nc, MethodContext mc,
                MethodDefinition md, TypeDefinition td) throws Throwable {
            if (nc == null) {
                mh.invokeExact(det, (Block) n, mc, md, td);
            }
            return true;
        }

        static boolean runExpression(MethodHandle mh, Object det, Node n, NodeChain nc, MethodContext mc,
                MethodDefinition md, TypeDefinition td) throws Throwable {
            if (n instanceof Expression) {
                return (boolean) mh.invokeExact(det, (Expression) n, nc, mc, md, td);
            }
            return true;
        }

        private VisitorType(AstNodes nodeTypes, MethodType wantedType, String adapter) {
            this.nodeTypes = nodeTypes;
            this.wantedType = wantedType;
            if (adapter != null) {
                try {
                    this.adapter = MethodHandles.lookup().findStatic(getClass(), adapter,
                        NODE_VISITOR_TYPE.insertParameterTypes(0, MethodHandle.class));
                } catch (NoSuchMethodException | IllegalAccessException e) {
                    throw new InternalError(e);
                }
            }
        }

        MethodHandle adapt(MethodHandle mh) {
            if (adapter == null)
                return mh;
            return adapter.bindTo(mh);
        }
    }

    public Detector(Map<String, WarningType> wts, Class<?> clazz, DatabaseRegistry databases)
            throws IllegalAccessException {
        this.wts = Objects.requireNonNull(wts);
        this.clazz = Objects.requireNonNull(clazz);
        for (Method m : clazz.getMethods()) {
            AstVisitor annotation = m.getAnnotation(AstVisitor.class);
            if (annotation != null) {
                for (VisitorType type : VisitorType.values()) {
                    if (annotation.nodes() == type.nodeTypes) {
                        astVisitors.add(new VisitorInfo(annotation, type, adapt(MethodHandles.publicLookup().unreflect(
                            m), type.wantedType, databases)));
                    }
                }
            }
            if (m.getAnnotation(MethodVisitor.class) != null) {
                methodVisitors.add(adapt(MethodHandles.publicLookup().unreflect(m), METHOD_VISITOR_TYPE, databases));
            }
            if (m.getAnnotation(ClassVisitor.class) != null) {
                classVisitors.add(adapt(MethodHandles.publicLookup().unreflect(m), CLASS_VISITOR_TYPE, databases));
            }
        }
    }

    MethodHandle bindDatabases(int count, TypeDefinition td, MethodHandle mh) {
        int curCount = mh.type().parameterCount();
        if (curCount > count) {
            Object[] params = new Object[curCount - count];
            for (int i = count; i < curCount; i++) {
                params[i - count] = getDatabase(mh.type().parameterType(i), td);
            }
            mh = MethodHandles.insertArguments(mh, count, params);
        }
        return mh;
    }

    private <T> T getDatabase(Class<T> clazz, TypeReference tr) {
        Function<TypeReference, ?> fn = dbFetchers.get(clazz);
        if (fn == null)
            throw new IllegalArgumentException("Requested unknown database: " + clazz);
        return clazz.cast(fn.apply(tr));
    }

    private MethodHandle adapt(MethodHandle mh, MethodType wantedType, DatabaseRegistry databases) {
        MethodType type = mh.type();
        MethodHandle result = MethodHandles.explicitCastArguments(mh, type.changeParameterType(0, Object.class));
        if (wantedType.returnType() == boolean.class) {
            if (type.returnType() == void.class) {
                result = MethodHandles.filterReturnValue(result, ALWAYS_TRUE);
                type = mh.type();
            } else if (type.returnType() != boolean.class) {
                throw new IllegalStateException(mh + ": Unexpected return type " + type.returnType());
            }
        } else {
            if (type.returnType() != wantedType.returnType()) {
                throw new IllegalStateException(mh + ": Unexpected return type " + type.returnType());
            }
        }
        List<Class<?>> wantedTypes = new ArrayList<>(wantedType.parameterList());
        Class<?>[] types = type.parameterArray();
        for (int i = 1; i < types.length; i++) {
            int pos = wantedTypes.indexOf(types[i]);
            if (pos < 0) {
                dbFetchers.put(types[i], databases.queryDatabase(types[i]));
                wantedTypes.add(types[i]);
            }
        }
        if (wantedType.parameterCount() < wantedTypes.size())
            wantedType = MethodType.methodType(wantedType.returnType(), wantedTypes);
        int[] map = new int[wantedTypes.size()];
        Arrays.fill(map, -1);
        map[0] = 0;
        wantedTypes.set(0, null); // self-reference
        for (int i = 1; i < types.length; i++) {
            int pos = wantedTypes.indexOf(types[i]);
            if (pos < 0) {
                throw new IllegalStateException(mh + ": Duplicate parameter type " + types[i]);
            }
            wantedTypes.set(pos, null);
            map[i] = pos;
        }
        Class<?>[] missingClasses = wantedTypes.stream().filter(Objects::nonNull).toArray(Class<?>[]::new);
        if (missingClasses.length > 0) {
            int pos = 0;
            for (int i = types.length; i < map.length; i++) {
                while (wantedTypes.get(pos) == null)
                    pos++;
                map[i] = pos++;
            }
            result = MethodHandles.dropArguments(result, types.length, missingClasses);
        }
        result = MethodHandles.permuteArguments(result, wantedType, map);
        return result;
    }

    public WarningType getWarningType(String typeName) {
        return wts.get(typeName);
    }

    @Override
    public String toString() {
        return clazz.getName().replace(DetectorRegistry.DETECTORS_PACKAGE, "internal");
    }

    public Object newInstance() {
        try {
            return clazz.newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            throw new InternalError(e);
        }
    }
}
