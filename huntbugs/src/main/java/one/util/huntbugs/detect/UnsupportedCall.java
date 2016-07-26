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

import java.util.Locale;

import com.strobel.assembler.metadata.MethodDefinition;
import com.strobel.assembler.metadata.MethodReference;
import com.strobel.decompiler.ast.AstCode;
import com.strobel.decompiler.ast.Expression;

import one.util.huntbugs.db.MethodStats;
import one.util.huntbugs.db.MethodStats.MethodData;
import one.util.huntbugs.registry.MethodContext;
import one.util.huntbugs.registry.anno.AstNodes;
import one.util.huntbugs.registry.anno.AstVisitor;
import one.util.huntbugs.registry.anno.MethodVisitor;
import one.util.huntbugs.registry.anno.WarningDefinition;
import one.util.huntbugs.util.AccessLevel;
import one.util.huntbugs.util.NodeChain;

/**
 * @author lan
 *
 */
@WarningDefinition(category = "CodeStyle", name = "UnsupportedCall", maxScore = 50)
public class UnsupportedCall {
    @MethodVisitor
    public boolean check(MethodDefinition md) {
        return !md.isSynthetic();
    }
    
    @AstVisitor(nodes = AstNodes.EXPRESSIONS)
    public void visit(Expression expr, NodeChain nc, MethodContext mc, MethodStats ms) {
        if(expr.getOperand() instanceof MethodReference) {
            MethodReference mr = (MethodReference) expr.getOperand();
            MethodData stats = ms.getStats(mr);
            boolean exact = expr.getCode() == AstCode.InitObject || expr.getCode() == AstCode.InvokeSpecial;
            if (stats != null
                && stats.testAny(MethodStats.METHOD_HAS_BODY, exact)
                && !stats.testAny(MethodStats.METHOD_SUPPORTED, exact)) {
                if(mr.getDeclaringType().getPackageName().equals("java.lang")) {
                    return;
                }
                String lcName = mr.getName().toLowerCase(Locale.ENGLISH);
                if(lcName.contains("unsupported") || lcName.contains("throw") || lcName.contains("exception") || lcName.contains("error"))
                    return;
                // Subclassed in SPI which might be out of the analysis scope
                if(mr.getDeclaringType().getInternalName().equals("java/nio/charset/CharsetDecoder"))
                    return;
                if(nc.isInTry("java/lang/UnsupportedOperationException")) {
                    return;
                }
                int priority = 0;
                if(expr.getCode() == AstCode.InvokeInterface) {
                    priority = 20;
                } else if(expr.getCode() == AstCode.InvokeVirtual) {
                    MethodDefinition md = mr.resolve();
                    if(md != null && !md.isFinal() && !md.getDeclaringType().isFinal()) {
                        priority = AccessLevel.of(md).select(25, 15, 0, 0);
                    }
                }
                mc.report("UnsupportedCall", priority, expr);
            }
        }
    }
}
