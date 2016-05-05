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

import java.util.Arrays;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import com.strobel.assembler.metadata.FieldReference;
import com.strobel.assembler.metadata.MethodReference;
import com.strobel.decompiler.ast.AstCode;
import com.strobel.decompiler.ast.Expression;

import one.util.huntbugs.registry.MethodContext;
import one.util.huntbugs.registry.anno.AstNodes;
import one.util.huntbugs.registry.anno.AstVisitor;
import one.util.huntbugs.registry.anno.WarningDefinition;
import one.util.huntbugs.util.Nodes;
import one.util.huntbugs.warning.WarningAnnotation;

/**
 * @author lan
 *
 */
@WarningDefinition(category="Correctness", name="RegexUnintended", maxScore=85)
@WarningDefinition(category="Correctness", name="RegexFileSeparator", maxScore=70)
@WarningDefinition(category="Correctness", name="RegexBadSyntax", maxScore=80)
public class RegexProblems {
    @AstVisitor(nodes=AstNodes.EXPRESSIONS)
    public void visit(Expression expr, MethodContext mc) {
        if(expr.getCode() == AstCode.InvokeStatic || expr.getCode() == AstCode.InvokeVirtual) {
            MethodReference mr = (MethodReference) expr.getOperand();
            String type = mr.getDeclaringType().getInternalName();
            String name = mr.getName();
            String sig = mr.getSignature();
            if (type.equals("java/util/regex/Pattern") && (name.equals("compile") && sig.equals("(Ljava/lang/String;)") ||
                    name.equals("matches"))) {
                checkRegexp(mc, Nodes.getChild(expr, 0), 0);
            } else if(type.equals("java/util/regex/Pattern") && name.equals("compile") && sig.equals("(Ljava/lang/String;I)")) {
                Object flags = Nodes.getConstant(expr.getArguments().get(1));
                checkRegexp(mc, Nodes.getChild(expr, 0), flags instanceof Integer ? (int)flags: 0);
            } else if(type.equals("java/lang/String") && (name.equals("replaceAll") || name.equals("replaceFirst")
                    || name.equals("matches") || name.equals("split"))) {
                checkRegexp(mc, Nodes.getChild(expr, 1), 0);
                checkBadPatterns(mc, Nodes.getChild(expr, 1), name.equals("replaceAll") ? Nodes.getConstant(expr.getArguments().get(2)) : null);
            }
        }
    }

    private void checkBadPatterns(MethodContext mc, Expression regexExpr, Object replacementObj) {
        Object regexObj = Nodes.getConstant(regexExpr);
        if(!(regexObj instanceof String)) {
            return;
        }
        String regex = (String)regexObj;
        if(regex.equals("|")) {
            mc.report("RegexUnintended", 0, regexExpr, new WarningAnnotation<>("REGEXP", regex));
        } else if(regex.equals(".")) {
            if(replacementObj instanceof String) {
                String replacement = (String) replacementObj;
                if(Arrays.asList("x", "-", "*", " ", "\\*").contains(replacement.toLowerCase(Locale.ENGLISH)))
                    return;
            }
            mc.report("RegexUnintended", 10, regexExpr, new WarningAnnotation<>("REGEXP", regex));
        }
    }

    private void checkRegexp(MethodContext mc, Expression regexExpr, int flags) {
        if((flags & Pattern.LITERAL) == 0) {
            if(regexExpr.getCode() == AstCode.GetStatic) {
                FieldReference fr = (FieldReference) regexExpr.getOperand();
                if(fr.getName().equals("separator") && fr.getDeclaringType().getInternalName().equals("java/io/File")) {
                    mc.report("RegexFileSeparator", 0, regexExpr);
                }
            } else if(regexExpr.getCode() == AstCode.InvokeVirtual) {
                MethodReference mr = (MethodReference)regexExpr.getOperand();
                if(mr.getName().equals("getSeparator") && mr.getDeclaringType().getInternalName().equals("java/nio/file/FileSystem")) {
                    mc.report("RegexFileSeparator", 0, regexExpr);
                }
            }
        }
        Object regexObj = Nodes.getConstant(regexExpr);
        if(!(regexObj instanceof String)) {
            return;
        }
        String regex = (String)regexObj;
        try {
            Pattern.compile(regex, flags);
        } catch (PatternSyntaxException e) {
            mc.report("RegexBadSyntax", 0, regexExpr, new WarningAnnotation<>("REGEXP", regex),
                new WarningAnnotation<>("ERROR_MESSAGE", e.getMessage()));
        }
    }
}
