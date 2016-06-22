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
package one.util.huntbugs.flow;

import java.util.ArrayList;
import java.util.List;

import com.strobel.componentmodel.Key;
import com.strobel.decompiler.ast.Expression;

/**
 * @author shustkost
 *
 */
public class Annotators {
    private Annotators() {
    }

    private static final List<String> names = new ArrayList<>();
    private static final Key<Object[]> hbData = Key.create("hb.data");
    
    public static final PurityAnnotator PURITY = new PurityAnnotator();

    public static final SourceAnnotator SOURCE = new SourceAnnotator();
    
    public static final ConstAnnotator CONST = new ConstAnnotator();

    public static final BackLinkAnnotator BACKLINK = new BackLinkAnnotator();
    
    static int register(String name) {
        if(names.contains(name))
            throw new IllegalStateException(name);
        names.add(name);
        return names.size()-1;
    }
    
    static Object get(Expression expr, int i) {
        Object[] data = expr.getUserData(hbData);
        return data == null ? null : data[i];
    }
    
    static void put(Expression expr, int i, Object data) {
        Object[] userData = expr.getUserData(hbData);
        if(userData == null) {
            userData = new Object[names.size()];
            expr.putUserData(hbData, userData);
        }
        userData[i] = data;
    }
    
    static void replace(Expression expr, int i, Object oldData, Object data) {
        Object[] userData = expr.getUserData(hbData);
        if(userData == null) {
            userData = new Object[names.size()];
            expr.putUserData(hbData, userData);
        }
        if(userData[i] == oldData)
            userData[i] = data;
    }
    
    static void remove(Expression expr, int i) {
        Object[] userData = expr.getUserData(hbData);
        if(userData != null) {
            userData[i] = null;
        }
    }
    
    /**
     * Debug output of all facts known for expression
     * 
     * @param expr
     * @return
     */
    public static String facts(Expression expr) {
        Object[] data = expr.getUserData(hbData);
        if(data == null)
            return "{}";
        StringBuilder sb = new StringBuilder("{\n");
        for(int i=0; i<names.size(); i++) {
            if(data[i] == null)
                continue;
            sb.append("  ").append(i+1).append(".").append(names.get(i))
                .append(" = ").append(data[i]).append("\n");
        }
        sb.append("}");
        return sb.toString();
    }
}
