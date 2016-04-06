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

import com.strobel.assembler.metadata.MethodDefinition;
import com.strobel.assembler.metadata.MethodReference;
import com.strobel.decompiler.ast.AstCode;
import com.strobel.decompiler.ast.Expression;

import one.util.huntbugs.registry.MethodContext;
import one.util.huntbugs.registry.anno.AstExpressionVisitor;
import one.util.huntbugs.registry.anno.WarningDefinition;
import one.util.huntbugs.util.Nodes;

/**
 * @author lan
 *
 */
@WarningDefinition(category="BadPractice", name="SystemExit", baseScore=60)
@WarningDefinition(category="BadPractice", name="ThreadStopThrowable", baseScore=60)
public class BadMethodCalls {
    @AstExpressionVisitor
    public void visit(Expression node, MethodContext ctx, MethodDefinition curMethod) {
        if(Nodes.isInvoke(node) && node.getCode() != AstCode.InvokeDynamic) {
            check(node, (MethodReference)node.getOperand(), ctx, curMethod);
        }
    }

	private void check(Expression node, MethodReference mr,
			MethodContext ctx, MethodDefinition curMethod) {
	    if(mr.getDeclaringType().getInternalName().equals("java/lang/System") && mr.getName().equals("exit")) {
	        String curName = curMethod.getName();
			if (isMain(curMethod) || curName.equals("processWindowEvent")
					|| curName.startsWith("windowClos"))
				return;
			int score = 0;
	        curName = curName.toLowerCase(Locale.ENGLISH);
			if (curName.indexOf("exit") > -1 || curName.indexOf("crash") > -1
					|| curName.indexOf("die") > -1
					|| curName.indexOf("main") > -1)
				score -= 20;
			if(curMethod.isStatic())
			    score -= 10;
	        if(curMethod.getDeclaringType().getDeclaredMethods().stream().anyMatch(BadMethodCalls::isMain))
	            score -= 20;
			ctx.report("SystemExit", score, node);
	    }
	    else if(mr.getDeclaringType().getInternalName().equals("java/lang/Thread") && mr.getName().equals("stop")
	            && mr.getSignature().equals("(Ljava/lang/Throwable;)V"))
	        ctx.report("ThreadStopThrowable", 0, node);
	}

	private static boolean isMain(MethodDefinition curMethod) {
		return curMethod.getName().equals("main")
				&& curMethod.isStatic()
				&& curMethod.getErasedSignature().startsWith(
						"([Ljava/lang/String;)");
	}
}
