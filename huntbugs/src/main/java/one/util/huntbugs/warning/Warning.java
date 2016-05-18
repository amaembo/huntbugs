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
package one.util.huntbugs.warning;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import one.util.huntbugs.warning.WarningAnnotation.TypeInfo;

/**
 * @author Tagir Valeev
 *
 */
public class Warning {
    public static final int MIN_SCORE = 0;
    public static final int MAX_SCORE = 100;

    private final WarningType type;
    private final int priority;

    private final List<WarningAnnotation<?>> annotations;

    public Warning(WarningType type, int priority, List<WarningAnnotation<?>> annotations) {
        this.type = type;
        if(priority < 0) {
            throw new IllegalArgumentException("Priority is negative: "+priority+" (warning: "+type.getName()+")");
        }
        this.priority = priority;
        this.annotations = annotations;
    }
    
    public String getClassName() {
        TypeInfo ti = getAnnotation(Roles.TYPE);
        return ti == null ? "(Unknown)" : ti.toString();
    }
    
    public <T> T getAnnotation(Role<T> role) {
        for(WarningAnnotation<?> anno : annotations) {
            if(anno.getRole().equals(role))
                return role.getType().cast(anno.getValue());
        }
        return null;
    }
    
    public WarningAnnotation<?> getAnnotation(String name) {
        for(WarningAnnotation<?> anno : annotations) {
            if(anno.getRole().toString().equals(name))
                return anno;
        }
        return null;
    }
    
    public Stream<WarningAnnotation<?>> annotations() {
        return annotations.stream();
    }

    public int getScore() {
        return saturateScore(type.getMaxScore() - priority);
    }

    public WarningType getType() {
        return type;
    }

    public static int saturateScore(int score) {
        return score < MIN_SCORE ? MIN_SCORE : score > MAX_SCORE ? MAX_SCORE : score;
    }

    @Override
    public String toString() {
        return type.getCategory() + "/" + type.getName() + " (" + getScore() + ")\n"
            + annotations.stream().map(wa -> "\t" + wa + "\n").collect(Collectors.joining());
    }
}
