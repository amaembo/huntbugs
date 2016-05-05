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
package one.util.huntbugs.detect;

import java.util.Locale;

import com.strobel.assembler.metadata.FieldReference;
import com.strobel.assembler.metadata.Flags;
import com.strobel.assembler.metadata.JvmType;
import com.strobel.assembler.metadata.MethodDefinition;
import com.strobel.assembler.metadata.TypeDefinition;
import com.strobel.decompiler.ast.AstCode;
import com.strobel.decompiler.ast.Expression;

import one.util.huntbugs.registry.MethodContext;
import one.util.huntbugs.registry.anno.AstNodes;
import one.util.huntbugs.registry.anno.AstVisitor;
import one.util.huntbugs.registry.anno.MethodVisitor;
import one.util.huntbugs.registry.anno.WarningDefinition;
import one.util.huntbugs.util.NodeChain;
import one.util.huntbugs.util.Nodes;
import one.util.huntbugs.util.Types;

/**
 * @author lan
 *
 */
@WarningDefinition(category="BadPractice", name="StaticFieldFromInstanceMethod", maxScore=55)
public class StaticFieldFromInstanceMethod {
    @MethodVisitor
    public boolean check(MethodDefinition md) {
        return !md.isStatic();
    }
    
    @AstVisitor(nodes=AstNodes.EXPRESSIONS)
    public void visit(Expression expr, NodeChain nc, MethodContext mc, MethodDefinition md, TypeDefinition td) {
        if(expr.getCode() == AstCode.PutStatic) {
            FieldReference fr = (FieldReference) expr.getOperand();
            int priority = 0;
            if(md.isPrivate() || td.isPrivate())
                priority += 20;
            else if(!md.isPublic() || !td.isPublic())
                priority += 10;
            else if(md.isConstructor())
                priority += 5;
            if(nc.isSynchronized() || Flags.testAny(md.getFlags(), Flags.SYNCHRONIZED))
                priority += 15;
            if(Nodes.getChild(expr, 0).getCode() == AstCode.AConstNull)
                priority += 5;
            String name = fr.getName().toLowerCase(Locale.ENGLISH);
            if(name.contains("debug") || name.contains("verbose") && fr.getFieldType().getSimpleType() == JvmType.Boolean)
                priority += 10;
            if((md.getName().equals("start") || md.getName().equals("stop")) && md.getErasedSignature().equals("(Lorg/osgi/framework/BundleContext;)V")
                    && Types.isInstance(td, "org/osgi/framework/BundleActivator")) {
                priority += 30;
            }
            mc.report("StaticFieldFromInstanceMethod", priority, expr);
        }
    }
}
