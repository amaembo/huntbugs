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
package one.util.huntbugs.util;

import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * @author lan
 *
 */
public class Xml {
    
    public static Stream<Element> elements(Element parent) {
        NodeList nodes = parent.getChildNodes();
        return IntStream.range(0, nodes.getLength()).mapToObj(nodes::item).filter(Element.class::isInstance)
                .map(Element.class::cast);
    }

    public static Element getChild(Element element, String tagName) {
        Node node = element.getFirstChild();
        while(node != null) {
            if(node instanceof Element && ((Element)node).getTagName().equals(tagName)) {
                return (Element) node;
            }
            node = node.getNextSibling();
        }
        return null;
    }

    public static String getText(Element element, String tagName) {
        Element child = getChild(element, tagName);
        return child == null ? "" : child.getTextContent();
    }
    
    public static String getAttribute(Element element, String attribute) {
        if(element.hasAttribute(attribute))
            return element.getAttribute(attribute);
        return null;
    }
    
    public static int getIntAttribute(Element element, String attribute, int defaultValue) {
        String str = getAttribute(element, attribute);
        if(str == null)
            return defaultValue;
        try {
            return Integer.parseInt(str);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

}
