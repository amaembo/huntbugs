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
    private static final Object UNKNOWN_CONST = new Object();
    private static final Object NULL_CONST = new Object();
    
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
    
    @Override
    protected void visitType(TypeDefinition td) {
        getOrCreate(td);
        for(MethodDefinition md : td.getDeclaredMethods()) {
            MethodBody body = md.getBody();
            if(body != null) {
                Set<Instruction> seenLabels = new HashSet<>();
                Deque<Object> constStack = new ArrayDeque<>();
                for(Instruction instr : body.getInstructions()) {
                    if(instr.getOperandCount() == 1 && instr.getOperand(0) instanceof Instruction) {
                        seenLabels.add(instr.getOperand(0));
                    }
                    if(seenLabels.contains(instr)) {
                        constStack.clear();
                    }
                    switch(instr.getOpCode()) {
                    case LDC:
                    case LDC_W:
                        constStack.add(instr.getOperand(0));
                        continue;
                    case INVOKESTATIC:
                    case INVOKEVIRTUAL: {
                        if(constStack.size() >= 2) {
                            MethodReference mr = instr.getOperand(0);
                            if (mr.getName().equals("newUpdater") && mr.getDeclaringType().getPackageName().equals(
                                "java.util.concurrent.atomic")) {
                                linkUncontrolled(constStack, 0);
                            }
                            if (mr.getName().equals("getDeclaredField") && Types.is(mr.getDeclaringType(),
                                Class.class)) {
                                linkUncontrolled(constStack, 0);
                            }
                            if (mr.getDeclaringType().getInternalName().equals("java/lang/invoke/MethodHandles$Lookup")
                                && (mr.getName().equals("findGetter") || mr.getName().equals("findSetter") || mr
                                        .getName().startsWith("findStatic"))) {
                                linkUncontrolled(constStack, 1);
                            }
                        }
                        break;
                    }
                    case ACONST_NULL:
                        constStack.add(NULL_CONST);
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
                        Object value = constStack.isEmpty() ? UNKNOWN_CONST : constStack.removeLast();
                        if(instr.getOpCode() == OpCode.PUTFIELD)
                            constStack.pollLast();
                        getOrCreate(fr.getDeclaringType()).link(md, fr,
                            instr.getOpCode() == OpCode.PUTSTATIC, true, value == NULL_CONST);
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
                            constStack.pollLast();
                        getOrCreate(fr.getDeclaringType()).link(md, fr,
                            instr.getOpCode() == OpCode.GETSTATIC, false, false);
                        constStack.add(UNKNOWN_CONST);
                        continue;
                    default:
                    }
                    constStack.clear();
                }
            }
        }
    }
    
    private void linkUncontrolled(Deque<Object> constStack, int shift) {
        int size = constStack.size();
        if(size < shift+2)
            return;
        while(shift-->0)
            constStack.removeLast();
        Object fieldName = constStack.removeLast();
        Object type = constStack.removeLast();
        if(type instanceof TypeReference && fieldName instanceof String) {
            getOrCreate((TypeReference) type).linkUncontrolled((String) fieldName);
        }
    }

    public int getFlags(FieldReference fr) {
        TypeFieldStats fs = get(fr.getDeclaringType());
        return fs == null ? UNRESOLVED : fs.fieldRecords.getOrDefault(fr.getName(), 0); 
    }

    @TypeDatabaseItem(parentDatabase=FieldStats.class)
    public static class TypeFieldStats {
        Map<String, Integer> fieldRecords = new HashMap<>();
        
        void linkUncontrolled(String fieldName) {
            fieldRecords.put(fieldName, ACCESS | WRITE_NONNULL);
        }
        
        void link(MethodDefinition src, FieldReference fr, boolean isStatic, boolean write, boolean hadNull) {
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
