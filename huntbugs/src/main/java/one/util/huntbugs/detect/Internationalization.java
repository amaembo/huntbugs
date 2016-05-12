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

import java.util.HashMap;
import java.util.Map;

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
import one.util.huntbugs.warning.WarningAnnotation.MemberInfo;

/**
 * @author Tagir Valeev
 *
 */
@WarningDefinition(category="Internationalization", name="ConvertCaseWithDefaultLocale", maxScore=25)
@WarningDefinition(category="Internationalization", name="MethodReliesOnDefaultEncoding", maxScore=40)
public class Internationalization {
    private static final Map<MemberInfo, MemberInfo> defEncodingMethods = new HashMap<>();
    
    private static void add(String className, String methodName, String badSignature, String goodSignature) {
        defEncodingMethods.put(new MemberInfo(className, methodName, badSignature), 
            goodSignature == null ? null : new MemberInfo(className, methodName, goodSignature));
    }
    
    static {
        add("java/lang/String", "getBytes", "()[B", "(Ljava/nio/charset/Charset;)[B");
        add("java/lang/String", "<init>", "([B)V", "([BLjava/nio/charset/Charset;)V");
        add("java/lang/String", "<init>", "([BII)V", "([BIILjava/nio/charset/Charset;)V");
        add("java/io/ByteArrayOutputStream", "toString", "()Ljava/lang/String;", "(Ljava/lang/String;)Ljava/lang/String;");
        add("java/io/FileReader", "<init>", "(Ljava/lang/String;)V", null);
        add("java/io/FileReader", "<init>", "(Ljava/io/File;)V", null);
        add("java/io/FileReader", "<init>", "(Ljava/io/FileDescriptor;)V", null);
        add("java/io/FileWriter", "<init>", "(Ljava/lang/String;)V", null);
        add("java/io/FileWriter", "<init>", "(Ljava/lang/String;Z)V", null);
        add("java/io/FileWriter", "<init>", "(Ljava/io/File;)V", null);
        add("java/io/FileWriter", "<init>", "(Ljava/io/File;Z)V", null);
        add("java/io/FileWriter", "<init>", "(Ljava/io/FileDescriptor;)V", null);
        add("java/io/InputStreamReader", "<init>", "(Ljava/io/InputStream;)V", "(Ljava/io/InputStream;Ljava/nio/charset/Charset;)V");
        add("java/io/OutputStreamWriter", "<init>", "(Ljava/io/OutputStream;)V", "(Ljava/io/OutputStream;Ljava/nio/charset/Charset;)V");
        add("java/io/PrintStream", "<init>", "(Ljava/io/File;)V", "(Ljava/io/File;Ljava/lang/String;)V");
        add("java/io/PrintStream", "<init>", "(Ljava/io/OutputStream;)V", "(Ljava/io/OutputStream;ZLjava/lang/String;)V");
        add("java/io/PrintStream", "<init>", "(Ljava/io/OutputStream;Z)V", "(Ljava/io/OutputStream;ZLjava/lang/String;)V");
        add("java/io/PrintStream", "<init>", "(Ljava/lang/String;)V", "(Ljava/lang/String;Ljava/lang/String;)V");
        add("java/io/PrintWriter", "<init>", "(Ljava/io/File;)V", "(Ljava/io/File;Ljava/lang/String;)V");
        add("java/io/PrintWriter", "<init>", "(Ljava/io/OutputStream;)V", null);
        add("java/io/PrintWriter", "<init>", "(Ljava/io/OutputStream;Z)V", null);
        add("java/io/PrintWriter", "<init>", "(Ljava/lang/String;)V", "(Ljava/lang/String;Ljava/lang/String;)V");
        add("java/util/Scanner", "<init>", "(Ljava/io/File;)V", "(Ljava/io/File;Ljava/lang/String;)V");
        add("java/util/Scanner", "<init>", "(Ljava/nio/file/Path;)V", "(Ljava/nio/file/Path;Ljava/lang/String;)V");
        add("java/util/Scanner", "<init>", "(Ljava/io/InputStream;)V", "(Ljava/io/InputStream;Ljava/lang/String;)V");
        add("java/util/Scanner", "<init>", "(Ljava/nio/channels/ReadableByteChannel;)V", "(Ljava/nio/channels/ReadableByteChannel;Ljava/lang/String;)V");
        add("java/util/Formatter", "<init>", "(Ljava/lang/String;)V", "(Ljava/lang/String;Ljava/lang/String;)V");
        add("java/util/Formatter", "<init>", "(Ljava/io/File;)V", "(Ljava/io/File;Ljava/lang/String;)V");
        add("java/util/Formatter", "<init>", "(Ljava/io/OutputStream;)V", "(Ljava/io/OutputStream;Ljava/lang/String;)V");
    }
    
    @AstVisitor(nodes=AstNodes.EXPRESSIONS)
    public void visit(Expression expr, MethodContext mc) {
        if(expr.getCode() == AstCode.InvokeVirtual || expr.getCode() == AstCode.InitObject) {
            MethodReference mr = (MethodReference) expr.getOperand();
            if(mr.getDeclaringType().getInternalName().equals("java/lang/String") && mr.getSignature().equals("()Ljava/lang/String;")
                    && (mr.getName().equals("toUpperCase") || mr.getName().equals("toLowerCase"))) {
                mc.report("ConvertCaseWithDefaultLocale", 0, expr, WarningAnnotation.forMember("REPLACEMENT", mr.getDeclaringType().getInternalName(),
                    mr.getName(), "(Ljava/util/Locale;)Ljava/lang/String;"));
            } else {
                MemberInfo mi = new MemberInfo(mr);
                if(defEncodingMethods.containsKey(mi)) {
                    if(!expr.getArguments().isEmpty()) {
                        Expression arg = Nodes.getChild(expr, 0);
                        if(arg.getCode() == AstCode.GetStatic) {
                            FieldReference fr = (FieldReference) arg.getOperand();
                            if(fr.getDeclaringType().getInternalName().equals("java/lang/System")) {
                                // Usages like new Formatter(System.out) are considered ok
                                return;
                            }
                        }
                    }
                    MemberInfo replacement = defEncodingMethods.get(mi);
                    if(replacement != null) {
                        mc.report("MethodReliesOnDefaultEncoding", replacement.getSignature().contains("Charset") ? 0
                                : 3, expr, new WarningAnnotation<>("REPLACEMENT", replacement));
                    } else {
                        mc.report("MethodReliesOnDefaultEncoding", 10, expr);
                    }
                }
            }
        }
    }
}
