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

import com.strobel.assembler.metadata.MethodDefinition;
import com.strobel.assembler.metadata.MethodReference;
import com.strobel.assembler.metadata.TypeDefinition;
import com.strobel.assembler.metadata.TypeReference;
import com.strobel.decompiler.ast.AstCode;
import com.strobel.decompiler.ast.Expression;

import one.util.huntbugs.flow.ValuesFlow;
import one.util.huntbugs.registry.MethodContext;
import one.util.huntbugs.registry.anno.AstNodes;
import one.util.huntbugs.registry.anno.AstVisitor;
import one.util.huntbugs.registry.anno.WarningDefinition;
import one.util.huntbugs.util.Types;
import one.util.huntbugs.warning.WarningAnnotation;

/**
 * @author Tagir Valeev
 *
 */
@WarningDefinition(category = "Multithreading", name = "IncorrectConcurrentMethod", maxScore = 70)
public class LockProblems {
    @AstVisitor(nodes = AstNodes.EXPRESSIONS)
    public void visit(Expression expr, MethodContext mc) {
        if (expr.getCode() != AstCode.InvokeVirtual)
            return;
        MethodReference mr = (MethodReference) expr.getOperand();
        String name = mr.getName();
        if (!Types.isObject(mr.getDeclaringType()) || (!name.equals("wait") && !name.startsWith("notify")))
            return;
        TypeReference type = ValuesFlow.reduceType(expr.getArguments().get(0));
        if (type == null || !type.getInternalName().startsWith("java/util/concurrent/"))
            return;
        TypeDefinition target = type.resolve();
        if (target == null || !target.isPublic())
            return;
        MethodDefinition replacement = findReplacement(name, target);
        if(replacement != null) {
            mc.report("IncorrectConcurrentMethod", 0, expr, WarningAnnotation.forType("TARGET", target), WarningAnnotation.forMember("REPLACEMENT", replacement));
        }
    }

    private static MethodDefinition findReplacement(String name, TypeDefinition target) {
        for (MethodDefinition md : target.getDeclaredMethods()) {
            if (!md.isPublic() || !md.getSignature().equals("()V"))
                continue;
            if (name.equals("wait") && md.getName().equals("await"))
                return md;
            if (name.equals("notify") && (md.getName().equals("signal") || md.getName().equals("countDown")))
                return md;
            if (name.equals("notifyAll") && (md.getName().equals("signalAll") || md.getName().equals("countDown")))
                return md;
        }
        return null;

    }
}
