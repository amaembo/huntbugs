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

import com.strobel.assembler.metadata.MethodReference;
import com.strobel.assembler.metadata.TypeReference;
import com.strobel.decompiler.ast.AstCode;
import com.strobel.decompiler.ast.Expression;

import one.util.huntbugs.flow.ValuesFlow;
import one.util.huntbugs.registry.MethodContext;
import one.util.huntbugs.registry.anno.AstNodes;
import one.util.huntbugs.registry.anno.AstVisitor;
import one.util.huntbugs.registry.anno.WarningDefinition;
import one.util.huntbugs.util.NodeChain;
import one.util.huntbugs.util.Types;

/**
 * @author Tagir Valeev
 *
 */
@WarningDefinition(category="BadPractice", name="ReturnValueOfRead", maxScore=60)
@WarningDefinition(category="BadPractice", name="ReturnValueOfSkip", maxScore=50)
public class CheckReturnValue {
    @AstVisitor(nodes=AstNodes.EXPRESSIONS)
    public void visit(Expression expr, NodeChain nc, MethodContext mc) {
        if(nc.getNode() instanceof Expression)
            return;
        if(expr.getCode() == AstCode.InvokeVirtual || expr.getCode() == AstCode.InvokeInterface) {
            MethodReference mr = (MethodReference) expr.getOperand();
            boolean skipMethod = mr.getName().equals("skip") && mr.getSignature().equals("(J)J") ||
                    mr.getName().equals("skipBytes") && mr.getSignature().equals("(I)I");
            boolean readMethod = mr.getName().equals("read") && (mr.getSignature().equals("([B)I") ||
                    mr.getSignature().equals("([BII)I") || mr.getSignature().equals("([C)I") ||
                    mr.getSignature().equals("([CII)I"));
            if(skipMethod || readMethod) {
                TypeReference type = ValuesFlow.reduceType(expr.getArguments().get(0));
                if(type == null)
                    type = mr.getDeclaringType();
                if(!isInputStream(type))
                    return;
                if(skipMethod && Types.isInstance(type, "javax/imageio/stream/ImageInputStream"))
                    return;
                int priority = 0;
                if(!Types.isInstance(type, "java/io/BufferedInputStream"))
                    priority += 15;
                mc.report(skipMethod ? "ReturnValueOfSkip" : "ReturnValueOfRead", priority, expr);
            }
        }
    }

    private boolean isInputStream(TypeReference type) {
        return (Types.isInstance(type, "java/io/InputStream") && !Types.isInstance(type, "java/io/ByteArrayInputStream"))
            || Types.isInstance(type, "java/io/DataInput") || Types.isInstance(type, "java/io/Reader");
    }
}
