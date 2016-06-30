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
package one.util.huntbugs.db;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.strobel.assembler.ir.Instruction;
import com.strobel.assembler.metadata.Flags;
import com.strobel.assembler.metadata.MethodBody;
import com.strobel.assembler.metadata.MethodDefinition;
import com.strobel.assembler.metadata.MethodReference;
import com.strobel.assembler.metadata.TypeDefinition;

import one.util.huntbugs.registry.AbstractTypeDatabase;
import one.util.huntbugs.registry.anno.TypeDatabase;
import one.util.huntbugs.util.Methods;
import one.util.huntbugs.warning.WarningAnnotation.MemberInfo;

/**
 * @author lan
 *
 */
@TypeDatabase
public class MethodStats extends AbstractTypeDatabase<Boolean> {
    public static final long METHOD_MAY_HAVE_SIDE_EFFECT = 0x1;
    public static final long METHOD_MAY_THROW = 0x2;
    public static final long METHOD_MAY_RETURN_NORMALLY = 0x4;
    public static final long METHOD_HAS_BODY = 0x8;
    public static final long METHOD_NON_TRIVIAL = 0x10;
    public static final long METHOD_FINAL = 0x20;
    
    Map<MemberInfo, MethodData> data = new HashMap<>();
    
    public MethodStats() {
        super(type -> Boolean.TRUE);
    }
    
    @Override
    protected void visitType(TypeDefinition td) {
        for(MethodDefinition md : td.getDeclaredMethods()) {
            MethodData mdata = data.computeIfAbsent(new MemberInfo(md), k -> new MethodData());
            if(md.isFinal() || td.isFinal() || md.isStatic() || md.isPrivate()) {
                mdata.flags |= METHOD_FINAL;
            }
            visitMethod(mdata, md);
            MethodDefinition superMethod = Methods.findSuperMethod(md);
            if(superMethod != null) {
                data.computeIfAbsent(new MemberInfo(superMethod), k -> new MethodData()).addSubMethod(mdata);
            }
        }
    }
    
    public MethodData getStats(MemberInfo mi) {
        return data.get(mi);
    }

    public MethodData getStats(MethodReference mr) {
        return data.get(new MemberInfo(mr));
    }
    
    private void visitMethod(MethodData mdata, MethodDefinition md) {
        MethodBody body = md.getBody();
        if(Flags.testAny(md.getFlags(), Flags.NATIVE)) {
            mdata.flags |= METHOD_MAY_HAVE_SIDE_EFFECT | METHOD_MAY_RETURN_NORMALLY | METHOD_MAY_THROW | METHOD_NON_TRIVIAL;
        }
        if(body != null) {
            visitBody(mdata, body);
        }
    }

    private void visitBody(MethodData mdata, MethodBody body) {
        mdata.flags |= METHOD_HAS_BODY;
        if(body.getInstructions().size() > 2) {
            mdata.flags |= METHOD_NON_TRIVIAL;
        }
        for(Instruction instr : body.getInstructions()) {
            switch(instr.getOpCode()) {
            case INVOKEINTERFACE:
            case INVOKESPECIAL:
            case INVOKESTATIC:
            case INVOKEVIRTUAL: {
                MethodReference mr = (MethodReference)instr.getOperand(0);
                if(!Methods.isSideEffectFree(mr)) {
                    mdata.flags |= METHOD_MAY_HAVE_SIDE_EFFECT;
                }
                if(Methods.knownToThrow(mr)) {
                    mdata.flags |= METHOD_MAY_THROW;
                }
                break;
            }
            case PUTFIELD:
            case PUTSTATIC:
            case INVOKEDYNAMIC:
            case AASTORE:
            case DASTORE:
            case BASTORE:
            case CASTORE:
            case SASTORE:
            case IASTORE:
            case LASTORE:
            case FASTORE:
                mdata.flags |= METHOD_MAY_HAVE_SIDE_EFFECT;
                break;
            case ATHROW:
                mdata.flags |= METHOD_MAY_THROW;
                break;
            case ARETURN:
            case IRETURN:
            case LRETURN:
            case FRETURN:
            case DRETURN:
            case RETURN:
                mdata.flags |= METHOD_MAY_RETURN_NORMALLY;
                break;
            default:
            }
        }
    }

    public static class MethodData {
        private List<MethodData> subMethods;
        long flags;
        
        void addSubMethod(MethodData md) {
            if(subMethods == null) {
                subMethods = new ArrayList<>();
            }
            subMethods.add(md);
        }
        
        public boolean testAny(long flag, boolean exact) {
            if((flags & flag) != 0)
                return true;
            if(!exact && subMethods != null) {
                for(MethodData subMethod : subMethods) {
                    if(subMethod.testAny(flag, false))
                        return true;
                }
            }
            return false;
        }
        
        public boolean mayHaveSideEffect(boolean exact) {
            if (exact || ((flags & METHOD_FINAL) != 0)) {
                return testAny(METHOD_MAY_HAVE_SIDE_EFFECT, true);
            }
            if(testAny(METHOD_MAY_HAVE_SIDE_EFFECT, false) || !testAny(METHOD_NON_TRIVIAL, false))
                return true;
            return false;
        }
    }
}
