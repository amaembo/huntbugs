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
package one.util.huntbugs.detect;

import com.strobel.assembler.metadata.BuiltinTypes;
import com.strobel.assembler.metadata.Flags;
import com.strobel.assembler.metadata.TypeDefinition;
import com.strobel.assembler.metadata.TypeReference;
import com.strobel.decompiler.ast.AstCode;
import com.strobel.decompiler.ast.Expression;

import one.util.huntbugs.db.Hierarchy;
import one.util.huntbugs.db.Hierarchy.TypeHierarchy;
import one.util.huntbugs.flow.ValuesFlow;
import one.util.huntbugs.registry.MethodContext;
import one.util.huntbugs.registry.anno.AstNodes;
import one.util.huntbugs.registry.anno.AstVisitor;
import one.util.huntbugs.registry.anno.WarningDefinition;
import one.util.huntbugs.util.Nodes;
import one.util.huntbugs.util.Types;
import one.util.huntbugs.warning.Roles;

/**
 * @author lan
 *
 */
@WarningDefinition(category="Correctness", name="ContravariantArrayStore", maxScore=60)
public class CovariantArrays {
    @AstVisitor(nodes=AstNodes.EXPRESSIONS)
    public void visit(Expression expr, MethodContext mc, Hierarchy h) {
        if(expr.getCode() == AstCode.StoreElement) {
            TypeReference arrayType = ValuesFlow.reduceType(Nodes.getChild(expr, 0));
            TypeReference valueType = ValuesFlow.reduceType(Nodes.getChild(expr, 2));
            if(arrayType == null || valueType == null || valueType.isPrimitive())
                return;
            valueType = toRawType(valueType);
            if(!arrayType.isArray())
                return;
            TypeReference arrayElementType = arrayType.getElementType();
            arrayElementType = toRawType(arrayElementType);
            if(!Types.isInstance(valueType, arrayElementType)) {
                int priority = 0;
                if(Types.isInstance(arrayElementType, valueType)) {
                    priority += 20;
                    if(allImplementationsDerivedFromSubclass(h, valueType, arrayElementType))
                        return;
                }
                mc.report("ContravariantArrayStore", priority, expr, Roles.ARRAY_TYPE.create(arrayType), Roles.VALUE_TYPE.create(valueType));
            }
        }
    }

    private TypeReference toRawType(TypeReference type) {
        if(!type.isGenericType())
            return type;
        try {
            return type.getRawType();
        } catch (UnsupportedOperationException e) {
            return BuiltinTypes.Object;
        }
    }

    private boolean allImplementationsDerivedFromSubclass(Hierarchy h, TypeReference superClass,
            TypeReference subClass) {
        TypeDefinition td = superClass.resolve();
        if(td == null || (!td.isInterface() && !Flags.testAny(td.getFlags(), Flags.ABSTRACT)) )
            return false;
        for(TypeHierarchy th : h.get(td).getSubClasses()) {
            if(subClass.getInternalName().equals(th.getInternalName()))
                continue;
            if(th.hasFlag(Flags.INTERFACE) || th.hasFlag(Flags.ABSTRACT))
                continue;
            TypeReference subType = td.getResolver().lookupType(th.getInternalName());
            if(subType == null || Types.isInstance(subType, subClass))
                continue;
            return false;
        }
        return true;
    }

}
