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
package one.util.huntbugs.input;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import one.util.huntbugs.analysis.Context;
import one.util.huntbugs.analysis.ErrorMessage;
import one.util.huntbugs.analysis.HuntBugsResult;
import one.util.huntbugs.util.Xml;
import one.util.huntbugs.warning.Messages;
import one.util.huntbugs.warning.Role.LocationRole;
import one.util.huntbugs.warning.Role.MemberRole;
import one.util.huntbugs.warning.Role.NumberRole;
import one.util.huntbugs.warning.Role.StringRole;
import one.util.huntbugs.warning.Role.TypeRole;
import one.util.huntbugs.warning.Roles;
import one.util.huntbugs.warning.Warning;
import one.util.huntbugs.warning.WarningAnnotation;
import one.util.huntbugs.warning.WarningAnnotation.Location;
import one.util.huntbugs.warning.WarningAnnotation.TypeInfo;
import one.util.huntbugs.warning.WarningType;

/**
 * @author lan
 *
 */
public class XmlReportReader {
    public static HuntBugsResult read(Context ctx, Path path) throws IOException, SAXException, ParserConfigurationException {
        try (InputStream is = Files.newInputStream(path)) {
            return read(ctx, is);
        }
    }

    public static HuntBugsResult read(Context ctx, InputStream is) throws SAXException, IOException, ParserConfigurationException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document dom = builder.parse(is);
        return read(ctx, dom);
    }

    public static HuntBugsResult read(Context ctx, Document dom) {
        List<Warning> warnings = loadWarnings(dom, ctx);
        List<ErrorMessage> errors = loadErrors(dom);
        return new HuntBugsResult() {
            @Override
            public Stream<Warning> warnings() {
                return warnings.stream();
            }
            
            @Override
            public Messages getMessages() {
                return ctx.getMessages();
            }
            
            @Override
            public Stream<ErrorMessage> errors() {
                return errors.stream();
            }
        };
    }

    private static List<ErrorMessage> loadErrors(Document dom) {
        Element doc = dom.getDocumentElement();
        Element errorList = Xml.getChild(doc, "ErrorList");
        if(errorList == null)
            return Collections.emptyList();
        return Xml.elements(errorList).filter(e -> e.getTagName().equals("Error"))
            .map(XmlReportReader::loadError).collect(Collectors.toList());
    }
    
    private static ErrorMessage loadError(Element e) {
        String message = e.getTextContent();
        String className = Xml.getAttribute(e, "Class");
        String memberName = Xml.getAttribute(e, "Member");
        String signature = Xml.getAttribute(e, "Signature");
        String detector = Xml.getAttribute(e, "Detector");
        int line = Xml.getIntAttribute(e, "Line", -1);
        return new ErrorMessage(detector, className, memberName, signature, line, message);
    }

    private static List<Warning> loadWarnings(Document dom, Context ctx) {
        Element doc = dom.getDocumentElement();
        Element warningList = Xml.getChild(doc, "WarningList");
        if(warningList == null)
            return Collections.emptyList();
        return Xml.elements(warningList).filter(e -> e.getTagName().equals("Warning"))
            .map(e -> loadWarning(e, ctx)).filter(Objects::nonNull).collect(Collectors.toList());
    }
    
    private static Warning loadWarning(Element e, Context ctx) {
        String type = Xml.getAttribute(e, "Type");
        WarningType wtype = ctx.getWarningType(type);
        if(wtype == null) {
            return null;
        }
        int score = Xml.getIntAttribute(e, "Score", wtype.getMaxScore());
        int priority = Math.max(0, wtype.getMaxScore()-score);
        return new Warning(wtype, priority, Xml.elements(e).flatMap(XmlReportReader::loadAnnotations).collect(Collectors.toList()));
    }
    
    private static Stream<WarningAnnotation<?>> loadAnnotations(Element e) {
        switch(e.getTagName()) {
        case "Class": {
            String file = Xml.getAttribute(e, "SourceFile");
            WarningAnnotation<TypeInfo> type = Roles.TYPE.create(e.getAttribute("Name"));
            return file == null ? Stream.of(type)
                    : Stream.of(type, Roles.FILE.create(file));
        }
        case "Method":
            return Stream.of(Roles.METHOD.create(Xml.getChild((Element) e.getParentNode(), "Class")
                    .getAttribute("Name"), e.getAttribute("Name"), e.getAttribute("Signature")));
        case "Field":
            return Stream.of(Roles.FIELD.create(Xml.getChild((Element) e.getParentNode(), "Class")
                .getAttribute("Name"), e.getAttribute("Name"), e.getAttribute("Signature")));
        case "Location":
            return Stream.of(Roles.LOCATION.create(new Location(Xml.getIntAttribute(e, "Offset", -1), Xml
                    .getIntAttribute(e, "Line", -1))));
        case "AnotherLocation":
            return Stream.of(Roles.ANOTHER_INSTANCE.create(new Location(Xml.getIntAttribute(e, "Offset", -1), Xml
                .getIntAttribute(e, "Line", -1))));
        case "Annotation":
            return Stream.of(StringRole.forName(e.getAttribute("Role")).create(e.getTextContent()));
        case "NumberAnnotation": {
            Number number = loadNumber(e.getAttribute("Type"), e.getAttribute("Value"));
            return number == null ? null : Stream.of(NumberRole.forName(e.getAttribute("Role")).create(number));
        }
        case "TypeAnnotation":
            return Stream.of(TypeRole.forName(e.getAttribute("Role")).create(e.getAttribute("Name")));
        case "LocationAnnotation":
            return Stream.of(LocationRole.forName(e.getAttribute("Role")).create(
                new Location(Xml.getIntAttribute(e, "Offset", -1), Xml.getIntAttribute(e, "Line", -1))));
        case "MemberAnnotation":
            return Stream.of(MemberRole.forName(e.getAttribute("Role")).create(e.getAttribute("Type"),
                e.getAttribute("Name"), e.getAttribute("Signature")));
        default:
            return null;
        }
    }

    private static Number loadNumber(String type, String value) {
        try {
            switch(type) {
            case "Integer":
                return Integer.valueOf(value);
            case "Long":
                return Long.valueOf(value);
            case "Float":
                return Float.valueOf(value);
            case "Double":
                return Double.valueOf(value);
            case "BigInteger":
                return new BigInteger(value);
            }
        } catch (NumberFormatException e) {
            // ignore
        }
        return null;
    }
}
