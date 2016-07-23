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

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.strobel.assembler.ir.Instruction;
import com.strobel.assembler.ir.OpCode;
import com.strobel.assembler.metadata.FieldDefinition;
import com.strobel.assembler.metadata.FieldReference;
import com.strobel.assembler.metadata.MethodBody;
import com.strobel.assembler.metadata.MethodDefinition;
import com.strobel.assembler.metadata.MethodReference;
import com.strobel.assembler.metadata.TypeDefinition;
import com.strobel.assembler.metadata.TypeReference;
import com.strobel.assembler.metadata.VariableReference;

import one.util.huntbugs.registry.AbstractTypeDatabase;
import one.util.huntbugs.registry.anno.TypeDatabase;
import one.util.huntbugs.registry.anno.TypeDatabaseItem;
import one.util.huntbugs.util.Types;

/**
 * @author Tagir Valeev
 *
 */
@TypeDatabase
public class FieldStats extends AbstractTypeDatabase<FieldStats.TypeFieldStats>{
    public static final int WRITE_CONSTRUCTOR = 0x0001;
    public static final int WRITE_CLASS = 0x0002;
    public static final int WRITE_PACKAGE = 0x0004;
    public static final int WRITE_OUTSIDE = 0x0008;
    public static final int WRITE_NONNULL = 0x0010;
    public static final int WRITE = WRITE_CONSTRUCTOR | WRITE_CLASS | WRITE_PACKAGE | WRITE_OUTSIDE;
    public static final int READ_CLASS = 0x0100;
    public static final int READ_PACKAGE = 0x0200;
    public static final int READ_OUTSIDE = 0x0400;
    public static final int READ = READ_CLASS | READ_PACKAGE | READ_OUTSIDE;
    public static final int ACCESS = READ | WRITE;
    public static final int UNRESOLVED = 0x10000000;
    
    public FieldStats() {
        super(name -> new TypeFieldStats());
    }
    
    static final class SimpleStack {
        private static final Object UNKNOWN_CONST = new Object();
        private static final Object NULL_CONST = new Object();

        Set<Instruction> seenLabels = new HashSet<>();
        Deque<Object> constStack = new ArrayDeque<>();
        Object[] locals;
        
        SimpleStack(int maxLocals) {
            locals = new Object[maxLocals];
            Arrays.fill(locals, UNKNOWN_CONST);
        }
        
        void registerJump(Instruction instr) {
            if(instr.getOperandCount() == 1 && instr.getOperand(0) instanceof Instruction) {
                seenLabels.add(instr.getOperand(0));
            }
        }

        void preprocess(Instruction instr) {
            if(seenLabels.contains(instr)) {
                constStack.clear();
                Arrays.fill(locals, UNKNOWN_CONST);
            }
        }
        
        void set(int slot, Object cst) {
            locals[slot] = cst;
        }
        
        void setUnknown(int slot) {
            locals[slot] = UNKNOWN_CONST;
        }
        
        Object get(int slot) {
            return locals[slot];
        }
        
        void pushUnknown() {
            push(UNKNOWN_CONST);
        }
        
        void push(Object cst) {
            constStack.add(cst == null ? NULL_CONST : cst);
        }
        
        Object poll() {
            Object cst = constStack.pollLast();
            return cst == null ? UNKNOWN_CONST : cst == NULL_CONST ? null : cst; 
        }

        void clear() {
            constStack.clear();
        }
    }
    
    @Override
    protected void visitType(TypeDefinition td) {
        getOrCreate(td);
        for(MethodDefinition md : td.getDeclaredMethods()) {
            MethodBody body = md.getBody();
            if(body != null) {
                SimpleStack ss = new SimpleStack(body.getMaxLocals());
                for(Instruction instr : body.getInstructions()) {
                    ss.registerJump(instr);
                }
                for(Instruction instr : body.getInstructions()) {
                    ss.preprocess(instr);
                    switch(instr.getOpCode()) {
                    case ALOAD_0:
                    case ALOAD_1:
                    case ALOAD_2:
                    case ALOAD_3:
                        ss.push(ss.get(instr.getOpCode().getCode()-OpCode.ALOAD_0.getCode()));
                        continue;
                    case ALOAD:
                        ss.push(ss.get(((VariableReference)instr.getOperand(0)).getSlot()));
                        continue;
                    case ASTORE_0:
                    case ASTORE_1:
                    case ASTORE_2:
                    case ASTORE_3:
                        ss.set(instr.getOpCode().getCode()-OpCode.ASTORE_0.getCode(), ss.poll());
                        continue;
                    case ASTORE:
                        ss.set(((VariableReference)instr.getOperand(0)).getSlot(), ss.poll());
                        continue;
                    case LDC:
                    case LDC_W:
                        ss.push(instr.getOperand(0));
                        continue;
                    case INVOKESTATIC:
                    case INVOKEVIRTUAL: {
                        MethodReference mr = instr.getOperand(0);
                        if (mr.getName().equals("newUpdater") && mr.getDeclaringType().getPackageName().equals(
                            "java.util.concurrent.atomic") || mr.getName().equals("getDeclaredField") && Types.is(mr.getDeclaringType(),
                                Class.class)) {
                            Object fieldName = ss.poll();
                            if(mr.getParameters().size() == 3) {
                                ss.poll(); // field type for AtomicReferenceFieldUpdater
                            }
                            Object type = ss.poll();
                            linkUncontrolled(type, fieldName);
                        }
                        if (mr.getDeclaringType().getInternalName().equals("java/lang/invoke/MethodHandles$Lookup")
                            && (mr.getName().equals("findGetter") || mr.getName().equals("findSetter") || mr
                                    .getName().startsWith("findStatic"))) {
                            ss.poll();
                            Object fieldName = ss.poll();
                            Object type = ss.poll();
                            linkUncontrolled(type, fieldName);
                        }
                        if (mr.getName().equals("getDeclaredFields") && Types.is(mr.getDeclaringType(), Class.class)) {
                            Object type = ss.poll();
                            linkUncontrolled(type, null);
                        }
                        break;
                    }
                    case ACONST_NULL:
                        ss.push(null);
                        continue;
                    case PUTFIELD:
                    case PUTSTATIC: {
                        FieldReference fr = (FieldReference)instr.getOperand(0);
                        FieldDefinition fd = fr.resolve();
                        if(fd != null) {
                            if(fd.isSynthetic())
                                break;
                            fr = fd;
                        } // fd == null case is necessary to workaround procyon problem#301
                        Object value = ss.poll();
                        if(instr.getOpCode() == OpCode.PUTFIELD)
                            ss.poll();
                        getOrCreate(fr.getDeclaringType()).link(md, fr,
                            instr.getOpCode() == OpCode.PUTSTATIC, true, value == null);
                        continue;
                    }
                    case GETFIELD:
                    case GETSTATIC:
                        FieldReference fr = (FieldReference)instr.getOperand(0);
                        FieldDefinition fd = fr.resolve();
                        if(fd != null) {
                            if(fd.isSynthetic())
                                break;
                            fr = fd;
                        } // fd == null case is necessary to workaround procyon problem#301
                        if(instr.getOpCode() == OpCode.GETFIELD)
                            ss.poll();
                        getOrCreate(fr.getDeclaringType()).link(md, fr,
                            instr.getOpCode() == OpCode.GETSTATIC, false, false);
                        ss.pushUnknown();
                        continue;
                    default:
                    }
                    ss.clear();
                }
            }
        }
    }
    
    private void linkUncontrolled(Object type, Object fieldName) {
        if(type instanceof TypeReference) {
            TypeFieldStats tfs = getOrCreate((TypeReference) type);
            if(fieldName instanceof String)
                tfs.linkUncontrolled((String) fieldName);
            else
                tfs.linkUncontrolled();
        }
    }

    public int getFlags(FieldReference fr) {
        TypeFieldStats fs = get(fr.getDeclaringType());
        return fs == null ? UNRESOLVED : fs.getFlags(fr.getName()); 
    }

    @TypeDatabaseItem(parentDatabase=FieldStats.class)
    public static class TypeFieldStats {
        // Can be null if the whole type is uncontrolled
        private Map<String, Integer> fieldRecords = new HashMap<>();
        
        void linkUncontrolled(String fieldName) {
            if(fieldRecords != null)
                fieldRecords.put(fieldName, ACCESS | WRITE_NONNULL);
        }
        
        public int getFlags(String name) {
            if(fieldRecords == null) {
                return ACCESS | WRITE_NONNULL;
            }
            return fieldRecords.getOrDefault(name, 0);
        }

        void linkUncontrolled() {
            fieldRecords = null;
        }

        void link(MethodDefinition src, FieldReference fr, boolean isStatic, boolean write, boolean hadNull) {
            if(fieldRecords == null)
                return;
            int prevStatus = fieldRecords.getOrDefault(fr.getName(), 0);
            int curStatus = prevStatus;
            if(src.getDeclaringType().isEquivalentTo(fr.getDeclaringType())) {
                if(write && (src.isConstructor() && !isStatic || src.isTypeInitializer() && isStatic)) {
                    curStatus |= WRITE_CONSTRUCTOR;
                } else {
                    curStatus |= write ? WRITE_CLASS : READ_CLASS;
                }
            } else if(src.getDeclaringType().getPackageName().equals(fr.getDeclaringType().getPackageName())) {
                curStatus |= write ? WRITE_PACKAGE : READ_PACKAGE;
            } else {
                curStatus |= write ? WRITE_OUTSIDE : READ_OUTSIDE;
            }
            if(write && !hadNull) {
                curStatus |= WRITE_NONNULL;
            }
            if(prevStatus != curStatus) {
                fieldRecords.put(fr.getName(), curStatus);
            }
        }
    }
}
