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

import com.strobel.assembler.metadata.MethodReference;
import com.strobel.decompiler.ast.AstCode;
import com.strobel.decompiler.ast.Expression;
import com.strobel.decompiler.ast.Node;

import one.util.huntbugs.registry.MethodContext;
import one.util.huntbugs.registry.anno.AstVisitor;
import one.util.huntbugs.registry.anno.WarningDefinition;
import one.util.huntbugs.util.Nodes;
import one.util.huntbugs.warning.WarningAnnotation;

/**
 * @author lan
 *
 */
@WarningDefinition(category = "Correctness", name = "InvalidMinMax", maxScore = 80)
public class InvalidMinMax {
	private static final int NONE = 0;
	private static final int MIN = -1;
	private static final int MAX = 1;

	private static int detectMethod(Node node) {
		if (!Nodes.isOp(node, AstCode.InvokeStatic)
				|| node.getChildren().size() != 2)
			return NONE;
		MethodReference mr = (MethodReference) ((Expression) node).getOperand();
		if (!mr.getDeclaringType().getPackageName().equals("java.lang"))
			return NONE;
		if (mr.getName().equals("max"))
			return MAX;
		if (mr.getName().equals("min"))
			return MIN;
		return NONE;
	}

	@AstVisitor
	public void visit(Node node, MethodContext mc) {
		int outer = detectMethod(node);
		if (outer == NONE)
			return;
		Node left = Nodes.getChild(node, 0);
		Node right = Nodes.getChild(node, 1);
		int leftChild = detectMethod(left);
		int rightChild = detectMethod(right);
		if (leftChild == NONE && rightChild == NONE)
			return;
		if (outer == leftChild || outer == rightChild
				|| leftChild == rightChild)
			return;
		if (rightChild != NONE) {
			Node tmp = left;
			left = right;
			right = tmp;
		}
		Object outerConst = Nodes.getConstant(right);
		if (!(outerConst instanceof Number))
			return;
		Node expr = left.getChildren().get(0);
		Object innerConst = Nodes.getConstant(expr);
		if (!(innerConst instanceof Number)) {
			innerConst = Nodes.getConstant(left.getChildren().get(1));
		} else {
			expr = left.getChildren().get(1);
		}
		if (!(innerConst instanceof Number))
			return;
		@SuppressWarnings("unchecked")
		int cmp = ((Comparable<Object>) outerConst).compareTo(innerConst)
				* outer;
		if (cmp > 0)
			mc.report("InvalidMinMax", 0, expr, new WarningAnnotation<>(
					"OUTER_NUMBER", outerConst), new WarningAnnotation<>(
					"OUTER_FUNC", outer == MAX ? "max" : "min"),
					new WarningAnnotation<>("INNER_NUMBER", innerConst),
					new WarningAnnotation<>("INNER_FUNC", outer == MAX ? "min"
							: "max"));
	}
}
