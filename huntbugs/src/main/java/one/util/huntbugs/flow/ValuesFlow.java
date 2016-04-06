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
package one.util.huntbugs.flow;

import java.util.Arrays;
import java.util.List;
import java.util.function.BinaryOperator;
import java.util.function.Function;

import one.util.huntbugs.analysis.Context;
import one.util.huntbugs.util.Types;

import com.strobel.assembler.metadata.MethodDefinition;
import com.strobel.assembler.metadata.MethodReference;
import com.strobel.assembler.metadata.ParameterDefinition;
import com.strobel.assembler.metadata.TypeReference;
import com.strobel.assembler.metadata.VariableDefinition;
import com.strobel.componentmodel.Key;
import com.strobel.decompiler.ITextOutput;
import com.strobel.decompiler.ast.AstCode;
import com.strobel.decompiler.ast.Block;
import com.strobel.decompiler.ast.Condition;
import com.strobel.decompiler.ast.Expression;
import com.strobel.decompiler.ast.Label;
import com.strobel.decompiler.ast.Lambda;
import com.strobel.decompiler.ast.Node;
import com.strobel.decompiler.ast.Variable;

/**
 * @author lan
 *
 */
public class ValuesFlow {
	private static final Key<Node> SOURCE_KEY = Key.create("hb.valueSource");

	public static class PhiNode extends Node {
		Node[] sources;

		PhiNode(Node... sources) {
			this.sources = sources;
		}

		@Override
		public List<Node> getChildren() {
			return Arrays.asList(sources);
		}

		@Override
		public void writeTo(ITextOutput output) {
			output.write("phi(");
			for (int i = 0; i < sources.length; i++) {
				if (i > 0)
					output.write(", ");
				sources[i].writeTo(output);
			}
			output.write(")");
		}
	}

	static <T> T reduce(Node input, Function<Expression, T> mapper,
			BinaryOperator<T> reducer) {
		Node source = getSource(input);
		if (source instanceof Expression)
			return mapper.apply((Expression) source);
		PhiNode phi = (PhiNode) source;
		boolean first = true;
		T result = null;
		for (Node child : phi.sources) {
			if (first) {
				result = reduce(child, mapper, reducer);
				first = false;
			} else {
				result = reducer.apply(result, reduce(child, mapper, reducer));
			}
		}
		return result;
	}

	public static TypeReference reduceType(Expression input) {
		return reduce(input, Expression::getInferredType, (t1, t2) -> {
			if (t1 == null || t2 == null)
				return null;
			if (t1.equals(t2))
				return t1;
			List<TypeReference> chain1 = Types.getBaseTypes(t1);
			List<TypeReference> chain2 = Types.getBaseTypes(t2);
			for (int i = Math.min(chain1.size(), chain2.size())-1; i >= 0; i--) {
				if (chain1.get(i).equals(chain2.get(i)))
					return chain1.get(i);
			}
			return null;
		});
	}

	public static Node getSource(Node input) {
		if (!(input instanceof Expression)) {
			return input;
		}
		Node source = ((Expression) input).getUserData(SOURCE_KEY);
		return source == null ? input : source;
	}

	static class Frame {
		final Node[] sources;

		Frame(MethodDefinition md) {
			this.sources = new Node[md.getBody().getMaxLocals()];
			for (ParameterDefinition pd : md.getParameters()) {
				Expression expression = new Expression(AstCode.Load, pd, 0);
				expression.setInferredType(pd.getParameterType());
				expression.setExpectedType(pd.getParameterType());
				sources[pd.getSlot()] = expression;
			}
		}

		private Frame(Node[] sources) {
			this.sources = sources;
		}

		Frame replace(int pos, Node replacement) {
			if (sources[pos] != replacement) {
				Node[] res = sources.clone();
				res[pos] = replacement;
				return new Frame(res);
			}
			return this;
		}

		Frame process(Expression expr) {
			switch (expr.getCode()) {
			case Store: {
				Variable var = ((Variable) expr.getOperand());
				Expression child = expr.getArguments().get(0);
				Frame target = process(child);
				expr.putUserData(SOURCE_KEY, getSource(child));
				VariableDefinition origVar = var.getOriginalVariable();
				if (origVar != null)
					return target.replace(origVar.getSlot(), getSource(child));
				return target;
			}
			case Load: {
				Variable var = ((Variable) expr.getOperand());
				VariableDefinition origVar = var.getOriginalVariable();
				if (origVar != null)
					expr.putUserData(SOURCE_KEY, sources[origVar.getSlot()]);
				return this;
			}
			case TernaryOp: {
				Expression cond = expr.getArguments().get(0);
				Expression left = expr.getArguments().get(1);
				Expression right = expr.getArguments().get(2);
				Frame target = process(cond);
				Frame leftFrame = target.process(left);
				Frame rightFrame = target.process(right);
				return leftFrame.merge(rightFrame);
			}
			case PostIncrement:
			case PreIncrement: {
				if (expr.getOperand() instanceof Variable) {
					Variable var = ((Variable) expr.getOperand());
					Expression child = expr.getArguments().get(0);
					Frame target = process(child);
					expr.putUserData(SOURCE_KEY, target.sources[var
							.getOriginalVariable().getSlot()]);
					return target.replace(var.getOriginalVariable().getSlot(),
							child);
				}
			}
			default: {
				Frame result = this;
				for (Expression child : expr.getArguments()) {
					result = result.process(child);
				}
				if (expr.getOperand() instanceof Lambda) {
					Lambda lambda = (Lambda) expr.getOperand();
					MethodReference method = lambda.getMethod();
					// TODO: support lambdas
					/*
					 * if (method != null) new
					 * Frame(method).process(lambda.getBody());
					 */
				}
				return result;
			}
			}
		}

		Frame process(Block method) {
			Frame result = this;
			for (Node n : method.getBody()) {
				if (result == null) {
					// Something unsupported occurred
					return null;
				} else if (n instanceof Expression) {
					Expression expr = (Expression) n;
					switch (expr.getCode()) {
					case Return:
					case AThrow:
						continue;
					case LoopOrSwitchBreak:
					case LoopContinue:
					case Ret:
						return null;
					default:
					}
					result = result.process(expr);
				} else if (n instanceof Condition) {
					Condition cond = (Condition) n;
					result = result.process(cond.getCondition());
					Frame left = result.process(cond.getTrueBlock());
					Frame right = result.process(cond.getFalseBlock());
					if (left == null || right == null)
						return null;
					result = left.merge(right);
				} else if (n instanceof Label) {
					// Skip
				} else {
					// TODO: support switch, loops, exceptions
					return null;
				}
			}
			return result;
		}

		Frame merge(Frame other) {
			Node[] res = null;
			for (int i = 0; i < sources.length; i++) {
				Node left = sources[i];
				Node right = other.sources[i];
				if (right == null || right == left)
					continue;
				if (res == null)
					res = sources.clone();
				if (left == null) {
					res[i] = right;
					continue;
				}
				res[i] = new PhiNode(left, right);
			}
			return res == null ? this : new Frame(res);
		}
	}

	public static void annotate(Context ctx, MethodDefinition md, Block method) {
		ctx.incStat("LoadsTracker.Total");
		if (new Frame(md).process(method) != null) {
			ctx.incStat("LoadsTracker.Success");
		}
	}
}
