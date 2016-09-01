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

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import one.util.huntbugs.util.Xml;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

/**
 * @author Tagir Valeev
 *
 */
public class Messages {
    private static final String MESSAGES_XML = "huntbugs/messages.xml";
    
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
    
    public Message getMessagesForType(WarningType warningType) {
        return getMessagesForType(warningType.getName());
    }
    
    public Message getMessagesForType(String warningType) {
        Message message = map.get(warningType);
        if(message == null) {
            return new Message(warningType, warningType+" in $METHOD$", warningType);
        }
        return message;
    }
    
    public static Messages load() {
        Map<String, Message> allMessages = new HashMap<>();

        try {
            // 3-rd party detectors could provide their own messages
            Enumeration<URL> messageUrls = Messages.class.getClassLoader().getResources(MESSAGES_XML);
            while (messageUrls.hasMoreElements()) {
                URL messageUrl = messageUrls.nextElement();
                allMessages.putAll(toMap(readMessages(messageUrl)));
            }
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }

        return new Messages(allMessages);
    }

    private static Document readMessages(URL messageUrl) {
        try (InputStream is = messageUrl.openStream()) {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(is);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        } catch (ParserConfigurationException | SAXException e) {
            throw new RuntimeException(e);
        }
    }

    private static Map<String, Message> toMap(Document dom) {
        Map<String, Message> map = new HashMap<>();
        Element element = dom.getDocumentElement();
        Element warnings = Xml.getChild(element, "WarningList");
        if(warnings != null) {
            Node node = warnings.getFirstChild();
            while(node != null) {
                if(node instanceof Element && ((Element)node).getTagName().equals("Warning")) {
                    Element warning = (Element) node;
                    String type = warning.getAttribute("Type");
                    String title = Xml.getText(warning, "Title");
                    String description = Xml.getText(warning, "Description");
                    String longDescription = Xml.getText(warning, "LongDescription");
                    if(map.containsKey(type)) {
                        throw new IllegalStateException("Warning type "+type+" is declared twice");
                    }
                    map.put(type, new Message(title, description, longDescription));
                }
                node = node.getNextSibling();
            }
        }
        return map;
    }
}
