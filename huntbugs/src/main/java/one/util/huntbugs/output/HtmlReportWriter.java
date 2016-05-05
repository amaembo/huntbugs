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
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.io.Writer;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.w3c.dom.Document;

/**
 * @author lan
 *
 */
class HtmlReportWriter implements ReportWriter {
    private static final String XSL_PATH = "huntbugs/report.xsl";
    private final Writer target;

    public HtmlReportWriter(Writer target) {
        this.target = target;

    }

    @Override
    public void write(Document dom) {
        try {
            try (InputStream is = HtmlReportWriter.class.getClassLoader().getResourceAsStream(XSL_PATH)) {
                StreamSource xsl = new StreamSource(is);
                Transformer transformer = TransformerFactory.newInstance().newTransformer(xsl);
                transformer.setOutputProperty(javax.xml.transform.OutputKeys.INDENT, "yes");
                transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
                StreamResult result = new StreamResult(target);
                transformer.transform(new DOMSource(dom), result);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        } catch (TransformerFactoryConfigurationError | TransformerException e) {
            throw new RuntimeException(e);
        }
    }

}
