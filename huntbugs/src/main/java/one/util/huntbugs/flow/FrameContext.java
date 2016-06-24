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
package one.util.huntbugs.flow;

import java.util.HashMap;
import java.util.Map;







import one.util.huntbugs.util.Exprs;
import one.util.huntbugs.util.Maps;
import one.util.huntbugs.warning.WarningAnnotation.MemberInfo;







import com.strobel.assembler.metadata.JvmType;
import com.strobel.assembler.metadata.MemberReference;
import com.strobel.assembler.metadata.MethodDefinition;
import com.strobel.decompiler.ast.AstCode;
import com.strobel.decompiler.ast.Expression;

/**
 * @author lan
 *
 */
class FrameContext {
    final MethodDefinition md;
    final ClassFields cf;
    private final Map<Expression, Expression> updatedNodes = new HashMap<>();

    FrameContext(MethodDefinition md, ClassFields cf) {
        this.md = md;
        this.cf = cf;
    }
    
    boolean isThis(Expression expr) {
        return !md.isStatic() && Exprs.isThis(expr);
    }
    
    Map<MemberInfo, Expression> getCtorFields(MemberReference ctor) {
        return cf.ctorFields.get(new MemberInfo(ctor));
    }
    
    Map<MemberInfo, Expression> getInitialFields() {
        Map<MemberInfo, Expression> map = new HashMap<>();
        if(md.isConstructor()) {
            cf.fields.forEach((mi, fd) -> {
                if(!fd.isStatic())
                    map.put(mi, getInitialExpression(fd.getFieldType().getSimpleType()));
                else {
                    Expression expr = cf.values.get(mi);
                    if(expr != null)
                        map.put(mi, expr);
                }
            });
        } else if(md.isTypeInitializer()) {
            cf.fields.forEach((mi, fd) -> {
                if(fd.isStatic())
                    map.put(mi, fd.getConstantValue() != null ? constant(fd.getConstantValue())
                            : getInitialExpression(fd.getFieldType().getSimpleType()));
            });
        } else if((md.getName().equals("readResolve") || md.getName().equals("readObjectNoData"))
                && md.getSignature().startsWith("()") ||
                md.getName().equals("readObject") && md.getSignature().equals("(Ljava/io/ObjectInputStream;)V")){
            cf.fields.forEach((mi, fd) -> {
                if(fd.isStatic()) {
                    Expression expr = cf.values.get(mi);
                    if(expr != null)
                        map.put(mi, expr);
                }
            });
        } else {
            map.putAll(cf.values);
        }
        return Maps.compactify(map);
    }
    
    private static Expression constant(Object val) {
        Expression expr = new Expression(AstCode.LdC, val, 0);
        Inf.CONST.put(expr, val);
        return expr;
    }

    private static Expression getInitialExpression(JvmType simpleType) {
        switch(simpleType)
        {
        case Array:
        case Object:
            return new Expression(AstCode.AConstNull, null, 0);
        case Integer:
        case Byte:
        case Short:
        case Boolean:
        case Character:
            return constant(0);
        case Double:
            return constant(0.0);
        case Float:
            return constant(0.0f);
        case Long:
            return constant(0L);
        default:
            throw new InternalError("Unexpected simple type: "+simpleType);
        }
    }

    Expression makeUpdatedNode(Expression src) {
        return updatedNodes.computeIfAbsent(src, s -> new Expression(Frame.UPDATE_TYPE, null, s.getOffset(), s));
    }

    public void makeFieldsFrom(Frame frame) {
        if(md.isTypeInitializer()) {
            cf.setStaticFinalFields(frame);
        } else if(md.isConstructor()) {
            cf.ctorFields.put(new MemberInfo(md), frame.fieldValues);
            cf.mergeFinalFields(frame);
        }
    }
}
