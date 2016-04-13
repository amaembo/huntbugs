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
package one.util.huntbugs.output;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;




import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;




import org.w3c.dom.Document;
import org.w3c.dom.Element;




import one.util.huntbugs.analysis.Context;
import one.util.huntbugs.warning.Formatter;
import one.util.huntbugs.warning.Warning;
import one.util.huntbugs.warning.WarningAnnotation.Location;
import one.util.huntbugs.warning.WarningAnnotation.MemberInfo;


/**
 * @author lan
 *
 */
public class XmlReportWriter {
    private final Path target;

    public XmlReportWriter(Path target) {
        this.target = target;
    }

    public void write(Context ctx) {
        Document dom = makeDom(ctx);
        try {
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            DOMSource source = new DOMSource(dom);
            try (OutputStream stream = Files.newOutputStream(target)) {
                StreamResult result = new StreamResult(stream);
                transformer.setOutputProperty(javax.xml.transform.OutputKeys.MEDIA_TYPE, "text/xml");
                transformer.setOutputProperty(javax.xml.transform.OutputKeys.INDENT, "yes");
                transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
                transformer.transform(source, result);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        } catch (TransformerFactoryConfigurationError | TransformerException e) {
            throw new RuntimeException(e);
        }
    }

    private Document makeDom(Context ctx) {
        Document doc;
        try {
            doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        } catch (ParserConfigurationException e) {
            throw new RuntimeException(e);
        }
        Element root = doc.createElement("HuntBugs");
        Element list = doc.createElement("WarningList");
        Formatter formatter = new Formatter(ctx.getMessages());
        ctx.warnings().sorted(Comparator.comparing(Warning::getScore).reversed()
            .thenComparing(w -> w.getType().getName()).thenComparing(Warning::getClassName)).map(
            w -> writeWarning(doc, w, formatter)).forEach(list::appendChild);
        root.appendChild(list);
        doc.appendChild(root);
        return doc;
    }
    
    private Element writeWarning(Document doc, Warning w, Formatter formatter) {
        Element element = doc.createElement("Warning");
        element.setAttribute("Type", w.getType().getName());
        element.setAttribute("Category", w.getType().getCategory());
        element.setAttribute("Score", String.valueOf(w.getScore()));
        Element title = doc.createElement("Title");
        title.appendChild(doc.createTextNode(formatter.getTitle(w)));
        element.appendChild(title);
        Element description = doc.createElement("Description");
        description.appendChild(doc.createTextNode(formatter.getDescription(w)));
        element.appendChild(description);
        Element longDescription = doc.createElement("LongDescription");
        longDescription.appendChild(doc.createCDATASection(formatter.getLongDescription(w)));
        element.appendChild(longDescription);
        Element classElement = doc.createElement("Class");
        Element methodElement = doc.createElement("Method");
        Element fieldElement = doc.createElement("Field");
        element.appendChild(classElement);
        Element location = doc.createElement("Location");
        List<Element> anotherLocations = new ArrayList<>();
        List<Element> attributes = new ArrayList<>();
        w.annotations().forEach(anno -> {
            switch(anno.getRole()) {
            case "TYPE":
                classElement.setAttribute("Name", formatter.formatValue(anno.getValue()));
                break;
            case "FILE":
                classElement.setAttribute("SourceFile", formatter.formatValue(anno.getValue()));
                break;
            case "LOCATION": {
                location.setAttribute("Offset", String.valueOf(((Location)anno.getValue()).getOffset()));
                int line = ((Location)anno.getValue()).getSourceLine();
                if(line != -1)
                    location.setAttribute("Line", String.valueOf(line));
                break;
            }
            case "ANOTHER_INSTANCE": {
                Element anotherLocation = doc.createElement("AnotherLocation");
                anotherLocation.setAttribute("Offset", String.valueOf(((Location)anno.getValue()).getOffset()));
                int line = ((Location)anno.getValue()).getSourceLine();
                if(line != -1)
                    anotherLocation.setAttribute("Line", String.valueOf(line));
                anotherLocations.add(anotherLocation);
                break;
            }
            case "METHOD": {
                MemberInfo mr = (MemberInfo) anno.getValue();
                methodElement.setAttribute("Name", mr.getName());
                methodElement.setAttribute("Signature", mr.getSignature());
                break;
            }
            case "FIELD": {
                MemberInfo mr = (MemberInfo) anno.getValue();
                fieldElement.setAttribute("Name", mr.getName());
                fieldElement.setAttribute("Signature", mr.getSignature());
                break;
            }
            default:
                Element attribute = doc.createElement("Annotation");
                attribute.setAttribute("Name", anno.getRole());
                attribute.appendChild(doc.createTextNode(formatter.formatValue(anno.getValue())));
                attributes.add(attribute);
            }
        });
        if(methodElement.hasAttribute("Name"))
            element.appendChild(methodElement);
        if(fieldElement.hasAttribute("Name"))
            element.appendChild(fieldElement);
        if(location.hasAttribute("Offset")) {
            if(classElement.hasAttribute("SourceFile"))
                location.setAttribute("SourceFile", classElement.getAttribute("SourceFile"));
            element.appendChild(location);
        }
        anotherLocations.forEach(al -> {
            if(classElement.hasAttribute("SourceFile"))
                al.setAttribute("SourceFile", classElement.getAttribute("SourceFile"));
            element.appendChild(al);
        });
        attributes.forEach(element::appendChild);
        return element;
    }
}
