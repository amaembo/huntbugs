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

import com.strobel.assembler.metadata.MethodDefinition;
import com.strobel.decompiler.ast.Expression;

/**
 * @author lan
 *
 */
interface Dataflow<FACT, STATE> {
    public STATE makeEntryState(MethodDefinition md, STATE closureState);

    public STATE makeTopState();
    
    public STATE transferState(STATE src, Expression expr);

    public STATE transferExceptionalState(STATE src, Expression expr);
    
    public TrueFalse<STATE> transferConditionalState(STATE src, Expression expr);

    public STATE mergeStates(STATE s1, STATE s2);
    
    public boolean sameState(STATE s1, STATE s2);
    
    public FACT makeFact(STATE state, Expression expr);
    
    public FACT makeUnknownFact();
    
    public FACT mergeFacts(FACT f1, FACT f2);
    
    public boolean sameFact(FACT f1, FACT f2);
    
    public default void onSuccess(STATE exitState) {};
    
    public default void onFail(STATE exitState) {};
}
