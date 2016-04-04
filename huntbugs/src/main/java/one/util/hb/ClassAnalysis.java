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
package one.util.hb;

import java.io.File;
import java.net.URISyntaxException;

import com.strobel.assembler.metadata.ClasspathTypeLoader;
import com.strobel.assembler.metadata.MetadataSystem;
import com.strobel.assembler.metadata.MethodBody;
import com.strobel.assembler.metadata.MethodDefinition;
import com.strobel.assembler.metadata.TypeDefinition;
import com.strobel.decompiler.DecompilerContext;
import com.strobel.decompiler.DecompilerSettings;
import com.strobel.decompiler.PlainTextOutput;
import com.strobel.decompiler.ast.AstBuilder;
import com.strobel.decompiler.ast.AstOptimizationStep;
import com.strobel.decompiler.ast.AstOptimizer;
import com.strobel.decompiler.ast.Block;

/**
 * @author lan
 *
 */
public class ClassAnalysis {
    public static void main(String[] args) throws URISyntaxException {
        DecompilerSettings settings = new DecompilerSettings();
        String classPath = new File(ClassAnalysis.class.getClassLoader().getResource(".").toURI()).toString();
        System.out.println(classPath);
        ClasspathTypeLoader loader = new ClasspathTypeLoader(classPath);
        MetadataSystem ms = new MetadataSystem(loader);
        TypeDefinition type = ms.resolve(ms.lookupType(ClassAnalysis.class.getName().replace(".", "/")));
        PlainTextOutput output = new PlainTextOutput();
        for(MethodDefinition md : type.getDeclaredMethods()) {
            MethodBody body = md.getBody();
            final DecompilerContext context = new DecompilerContext();

            context.setCurrentMethod(md);
            context.setCurrentType(type);
            final Block methodAst = new Block();
            methodAst.getBody().addAll(AstBuilder.build(body, true, context));
            AstOptimizer.optimize(context, methodAst, AstOptimizationStep.None);
            methodAst.writeTo(output);
        }
        //new BytecodeAstLanguage().decompileType(type, output, new DecompilationOptions());
        System.out.println(output.toString());
    }
    
    private void test(long a, int b) {
        char c = 'a';
        if(a > b && b < 2) {
            if(a > 1 || b > 3 && c > '0') {
                if(a <= 5) {
                    System.out.println("1");
                } else {
                    System.out.println("2");
                }
            }
        } else {
            System.out.println("3");
        }
    }
}
