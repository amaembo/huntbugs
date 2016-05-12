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
package one.util.huntbugs.output;

import java.io.FileWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import one.util.huntbugs.analysis.Context;
import one.util.huntbugs.analysis.ErrorMessage;
import one.util.huntbugs.warning.Formatter;
import one.util.huntbugs.warning.Warning;
import one.util.huntbugs.warning.WarningAnnotation.Location;
import one.util.huntbugs.warning.WarningAnnotation.MemberInfo;
import one.util.huntbugs.warning.WarningAnnotation.TypeInfo;

/**
 * @author isopov
 *
 */
public final class Reports {
    public static void write(Path xmlTarget, Path htmlTarget, Context ctx) {
        Document dom = makeDom(ctx);
        try (Writer xmlWriter = new FileWriter(xmlTarget.toFile()); 
                Writer htmlWriter = new FileWriter(htmlTarget.toFile())) {
            new CombinedReportWriter(
                Arrays.asList(
                    new XmlReportWriter(xmlWriter),
                    new HtmlReportWriter(htmlWriter)
                )
            ).write(dom);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
    
    private static Document makeDom(Context ctx) {
        Document doc;
        try {
            doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        } catch (ParserConfigurationException e) {
            throw new RuntimeException(e);
        }
        Element root = doc.createElement("HuntBugs");
        Element errors = doc.createElement("ErrorList");
        ctx.errors().map(e -> writeError(doc, e)).forEach(errors::appendChild);
        if (errors.hasChildNodes())
            root.appendChild(errors);
        Element warnings = doc.createElement("WarningList");
        Formatter formatter = new Formatter(ctx.getMessages());
        ctx.warnings().sorted(Comparator.comparing(Warning::getScore).reversed().thenComparing(w -> w.getType()
                .getName()).thenComparing(Warning::getClassName)).map(w -> writeWarning(doc, w, formatter)).forEach(
                    warnings::appendChild);
        root.appendChild(warnings);
        doc.appendChild(root);
        return doc;
    }

    private static Element writeError(Document doc, ErrorMessage e) {
        Element element = doc.createElement("Error");
        if (e.getDetector() != null)
            element.setAttribute("Detector", e.getDetector());
        if (e.getClassName() != null)
            element.setAttribute("Class", e.getClassName());
        if (e.getElementName() != null)
            element.setAttribute("Member", e.getElementName());
        if (e.getDescriptor() != null)
            element.setAttribute("Signature", e.getDescriptor());
        if (e.getLine() != -1)
            element.setAttribute("Line", String.valueOf(e.getLine()));
        element.appendChild(doc.createCDATASection(e.getError()));
        return element;
    }

    private static Element writeWarning(Document doc, Warning w, Formatter formatter) {
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
            switch (anno.getRole()) {
            case "TYPE":
                classElement.setAttribute("Name", ((TypeInfo) anno.getValue()).getTypeName());
                break;
            case "FILE":
                classElement.setAttribute("SourceFile", formatter.formatValue(anno.getValue(), Formatter.FORMAT_PLAIN));
                break;
            case "LOCATION": {
                location.setAttribute("Offset", String.valueOf(((Location) anno.getValue()).getOffset()));
                int line = ((Location) anno.getValue()).getSourceLine();
                if (line != -1)
                    location.setAttribute("Line", String.valueOf(line));
                break;
            }
            case "ANOTHER_INSTANCE": {
                Element anotherLocation = doc.createElement("AnotherLocation");
                anotherLocation.setAttribute("Offset", String.valueOf(((Location) anno.getValue()).getOffset()));
                int line = ((Location) anno.getValue()).getSourceLine();
                if (line != -1)
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
                Object value = anno.getValue();
                Element attribute;
                if (value instanceof TypeInfo) {
                    attribute = doc.createElement("TypeAnnotation");
                    attribute.setAttribute("Name", ((TypeInfo) value).getTypeName());
                } else if (value instanceof Location) {
                    attribute = doc.createElement("LocationAnnotation");
                    attribute.setAttribute("Line", String.valueOf(((Location) value).getSourceLine()));
                } else if (value instanceof MemberInfo) {
                    MemberInfo mr = (MemberInfo) anno.getValue();
                    attribute = doc.createElement("MemberAnnotation");
                    attribute.setAttribute("Type", mr.getTypeName());
                    attribute.setAttribute("Name", mr.getName());
                    attribute.setAttribute("Signature", mr.getSignature());
                } else {
                    attribute = doc.createElement("Annotation");
                    attribute.appendChild(doc.createTextNode(formatter.formatValue(anno.getValue(),
                        Formatter.FORMAT_PLAIN)));
                }
                attribute.setAttribute("Role", anno.getRole());
                attributes.add(attribute);
            }
        });
        if (methodElement.hasAttribute("Name"))
            element.appendChild(methodElement);
        if (fieldElement.hasAttribute("Name"))
            element.appendChild(fieldElement);
        if (location.hasAttribute("Offset")) {
            if (classElement.hasAttribute("SourceFile"))
                location.setAttribute("SourceFile", classElement.getAttribute("SourceFile"));
            element.appendChild(location);
        }
        anotherLocations.forEach(al -> {
            if (classElement.hasAttribute("SourceFile"))
                al.setAttribute("SourceFile", classElement.getAttribute("SourceFile"));
            element.appendChild(al);
        });
        attributes.forEach(element::appendChild);
        return element;
    }

}
