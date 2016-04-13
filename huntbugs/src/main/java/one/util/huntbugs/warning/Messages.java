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
package one.util.huntbugs.warning;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

/**
 * @author lan
 *
 */
public class Messages {
    private final Map<String, Message> map;
    
    public static class Message {
        private final String title, description, longDescription;

        public Message(String title, String description, String longDescription) {
            this.title = Objects.requireNonNull(title);
            this.description = Objects.requireNonNull(description);
            this.longDescription = Objects.requireNonNull(longDescription);
        }

        public String getTitle() {
            return title;
        }

        public String getDescription() {
            return description;
        }

        public String getLongDescription() {
            return longDescription.isEmpty() ? description : longDescription;
        }
    }
    
    private Messages(Map<String, Message> map) {
        this.map = map;
    }
    
    public Message getMessagesForType(String warningType) {
        Message message = map.get(warningType);
        if(message == null) {
            return new Message(warningType, warningType, warningType);
        }
        return message;
    }
    
    private static Element getChild(Element element, String tagName) {
        Node node = element.getFirstChild();
        while(node != null) {
            if(node instanceof Element && ((Element)node).getTagName().equals(tagName)) {
                return (Element) node;
            }
            node = node.getNextSibling();
        }
        return null;
    }
    
    private static String getText(Element element, String tagName) {
        Element child = getChild(element, tagName);
        return child == null ? "" : child.getTextContent();
    }
    
    public static Messages load() {
        Document dom;
        try(InputStream is = Messages.class.getClassLoader().getResourceAsStream("huntbugs/messages.xml")) {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            dom = builder.parse(is);
        }
        catch(IOException ex) {
            throw new UncheckedIOException(ex);
        } catch (ParserConfigurationException | SAXException e) {
            throw new RuntimeException(e);
        }
        Map<String, Message> map = new HashMap<>();
        Element element = dom.getDocumentElement();
        Element warnings = getChild(element, "WarningList");
        if(warnings != null) {
            Node node = warnings.getFirstChild();
            while(node != null) {
                if(node instanceof Element && ((Element)node).getTagName().equals("Warning")) {
                    Element warning = (Element) node;
                    String type = warning.getAttribute("Type");
                    String title = getText(warning, "Title");
                    String description = getText(warning, "Description");
                    String longDescription = getText(warning, "LongDescription");
                    map.put(type, new Message(title, description, longDescription));
                }
                node = node.getNextSibling();
            }
        }
        return new Messages(map);
    }
}
