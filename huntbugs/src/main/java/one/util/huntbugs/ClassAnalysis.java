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
package one.util.huntbugs;

import java.io.File;
import java.net.URISyntaxException;

import one.util.huntbugs.analysis.Context;

import com.strobel.assembler.metadata.ClasspathTypeLoader;

/**
 * @author lan
 *
 */
public class ClassAnalysis {
    public static void main(String[] args) throws URISyntaxException {

        String classPath = new File(ClassAnalysis.class.getClassLoader().getResource(".").toURI()).toString();
        System.out.println(classPath);
        Context ctx = new Context(new ClasspathTypeLoader(classPath));
        ctx.analyzeClass(ClassAnalysis.class.getName().replace(".", "/"));
        ctx.reportErrors(System.err);
        ctx.reportWarnings(System.out);
        
/*        DecompilerSettings settings = new DecompilerSettings();
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
*/    }
    
    private void test() {
        double a = 3.14;
        double b = 3.141592;
        double c = 2.7182;
        double d[] = {3.14, 3.15, 3.16};
        Double e[] = {3.141, 3.142, 3.143};
        int x = 2, y = 3;
        
        if(e == null & e[0] == a) {
            System.out.println("Ho-ho-ho!");
        }
        
        if((x & y) == 3) {
            System.out.println("Ho-ho-ho!");
        }
        
        if(e[1] > 5 & e[2] < 4) {
            System.out.println("Ho-ho-ho!");
        }
        
        if(a > b & c > b) {
            System.out.println("Ha-ha-ha!");
        }
    }
}
