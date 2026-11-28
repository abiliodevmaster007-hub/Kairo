package dev.kairo.xml;

import dev.kairo.core.Diagnostic;
import dev.kairo.core.SourceLocation;
import dev.kairo.ir.BindingSpec;
import dev.kairo.ir.IRDocument;
import dev.kairo.ir.IRValidator;
import dev.kairo.ir.KairoStyleDialect;
import dev.kairo.ir.NodeIR;

import javax.xml.XMLConstants;
import javax.xml.stream.Location;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.StringReader;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Secure JDK StAX XML-to-IR parser. Disables DTD, external entities and untrusted fetches.
 * Produces IRDocument with source locations, explicit component/style imports and Kairo style validation.
 */
public final class KairoXmlParser {
    private static final ThreadLocal<XMLInputFactory> SECURE_FACTORY =
            ThreadLocal.withInitial(KairoXmlParser::createSecureFactory);

    private static XMLInputFactory createSecureFactory() {
        XMLInputFactory factory = XMLInputFactory.newInstance();
        factory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, false);
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        try {
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        } catch (IllegalArgumentException ignored) {
            // Some StAX implementations rely exclusively on SUPPORT_DTD=false
        }
        return factory;
    }

    public record ParseResult(NodeIR root, Diagnostic.Bag diagnostics) {}

    private static final class Frame {
        final String tag;
        final String id;
        final String keyExpr;
        final String slotName;
        final List<String> classes;
        final Map<String, String> props = new LinkedHashMap<>();
        final Map<String, BindingSpec> bindings = new LinkedHashMap<>();
        final Map<String, String> events = new LinkedHashMap<>();
        final List<NodeIR> children = new ArrayList<>();
        final SourceLocation location;
        final StringBuilder textContent = new StringBuilder();

        Frame(String tag, String id, String keyExpr, String slotName, List<String> classes, SourceLocation location) {
            this.tag = tag;
            this.id = id;
            this.keyExpr = keyExpr;
            this.slotName = slotName;
            this.classes = classes;
            this.location = location;
        }
    }

    public ParseResult parse(String fileName, String xmlSource) {
        IRDocument doc = parseDocument(fileName, xmlSource);
        return new ParseResult(doc.root(), doc.diagnostics());
    }

    public IRDocument parseDocument(String fileName, String xmlSource) {
        Diagnostic.Bag diagnostics = new Diagnostic.Bag();
        Deque<Frame> stack = new ArrayDeque<>();
        List<String> styleImports = new ArrayList<>();
        Map<String, String> componentImports = new LinkedHashMap<>();
        NodeIR root = null;

        if (xmlSource == null) {
            diagnostics.error("XML_NULL_SOURCE", "XML source cannot be null", new SourceLocation(fileName, 1, 1));
            return new IRDocument(fileName, null, styleImports, componentImports, diagnostics);
        }

        XMLStreamReader reader = null;
        try {
            reader = SECURE_FACTORY.get().createXMLStreamReader(new StringReader(xmlSource));
            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    Location loc = reader.getLocation();
                    SourceLocation sourceLoc = new SourceLocation(fileName, loc.getLineNumber(), loc.getColumnNumber());
                    String tag = reader.getLocalName();
                    String id = null;
                    String keyExpr = null;
                    String slotName = null;
                    List<String> classes = List.of();

                    Map<String, String> props = new LinkedHashMap<>();
                    Map<String, BindingSpec> bindings = new LinkedHashMap<>();
                    Map<String, String> events = new LinkedHashMap<>();

                    for (int i = 0; i < reader.getAttributeCount(); i++) {
                        String attrName = reader.getAttributeLocalName(i);
                        String prefix = reader.getAttributePrefix(i);
                        String fullAttr = (prefix != null && !prefix.isEmpty() && !attrName.startsWith(prefix + ":"))
                                ? prefix + ":" + attrName
                                : attrName;
                        String attrValue = reader.getAttributeValue(i);

                        if ("id".equals(fullAttr)) {
                            id = attrValue;
                        } else if ("key".equals(fullAttr)) {
                            keyExpr = attrValue;
                        } else if ("name".equals(fullAttr) && "slot".equals(tag)) {
                            slotName = attrValue;
                        } else if ("class".equals(fullAttr)) {
                            classes = Arrays.stream(attrValue.trim().split("\\s+"))
                                    .filter(s -> !s.isBlank())
                                    .toList();
                        } else if ("style".equals(fullAttr) && !"style".equals(tag)) {
                            validateInlineKairoStyle(attrValue, sourceLoc, diagnostics);
                            props.put(fullAttr, attrValue);
                        } else if (fullAttr.startsWith("bind:")) {
                            String prop = fullAttr.substring("bind:".length());
                            bindings.put(prop, BindingSpec.twoWay(prop, attrValue));
                        } else if (fullAttr.startsWith("on") && fullAttr.length() > 2 && Character.isUpperCase(fullAttr.charAt(2))) {
                            events.put(fullAttr, attrValue);
                        } else if (attrValue.startsWith("{{") && attrValue.endsWith("}}")) {
                            String expr = attrValue.substring(2, attrValue.length() - 2).trim();
                            bindings.put(fullAttr, BindingSpec.oneWay(fullAttr, expr));
                        } else {
                            props.put(fullAttr, attrValue);
                        }
                    }

                    Frame frame = new Frame(tag, id, keyExpr, slotName, classes, sourceLoc);
                    frame.props.putAll(props);
                    frame.bindings.putAll(bindings);
                    frame.events.putAll(events);
                    stack.push(frame);
                } else if (event == XMLStreamConstants.CHARACTERS || event == XMLStreamConstants.CDATA) {
                    if (!stack.isEmpty()) {
                        stack.peek().textContent.append(reader.getText());
                    }
                } else if (event == XMLStreamConstants.END_ELEMENT) {
                    Frame completed = stack.pop();
                    if ("style".equals(completed.tag)) {
                        String src = completed.props.get("src");
                        if (src == null || src.isBlank()) {
                            diagnostics.error("XML_STYLE_MISSING_SRC", "<style> directive requires 'src'", completed.location);
                        } else {
                            styleImports.add(src.trim());
                        }
                        continue;
                    }
                    if ("import".equals(completed.tag)) {
                        String component = completed.props.get("component");
                        String alias = completed.props.getOrDefault("as", component);
                        if (component == null || component.isBlank()) {
                            diagnostics.error("XML_IMPORT_MISSING_COMPONENT", "<import> directive requires 'component'", completed.location);
                        } else {
                            componentImports.put(alias.trim(), component.trim());
                        }
                        continue;
                    }
                    if ("kairo".equals(completed.tag)) {
                        continue;
                    }

                    String trimmedText = completed.textContent.toString().trim();
                    if (!trimmedText.isEmpty() && !completed.props.containsKey("text") && !completed.bindings.containsKey("text")) {
                        if (trimmedText.startsWith("{{") && trimmedText.endsWith("}}")) {
                            String expr = trimmedText.substring(2, trimmedText.length() - 2).trim();
                            completed.bindings.put("text", BindingSpec.oneWay("text", expr));
                        } else {
                            completed.props.put("text", trimmedText);
                        }
                    }

                    NodeIR node = new NodeIR(
                            completed.tag,
                            completed.id,
                            completed.keyExpr,
                            completed.classes,
                            completed.props,
                            completed.bindings,
                            completed.events,
                            completed.slotName,
                            completed.children,
                            completed.location
                    );
                    if (stack.isEmpty()) {
                        root = node;
                    } else if ("kairo".equals(stack.peek().tag) && root == null) {
                        root = node;
                    } else {
                        stack.peek().children.add(node);
                    }
                }
            }
        } catch (XMLStreamException ex) {
            Location loc = ex.getLocation();
            SourceLocation sourceLoc = loc != null
                    ? new SourceLocation(fileName, loc.getLineNumber(), loc.getColumnNumber())
                    : new SourceLocation(fileName, -1, -1);
            diagnostics.error("XML_SYNTAX", ex.getMessage(), sourceLoc);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (XMLStreamException ignored) {
                    // Best-effort reader cleanup
                }
            }
        }

        if (root != null) {
            new IRValidator().validateInto(root, diagnostics);
        } else if (!diagnostics.hasErrors()) {
            diagnostics.error("XML_EMPTY", "XML document produced no root UI element", new SourceLocation(fileName, 1, 1));
        }

        return new IRDocument(fileName, root, styleImports, componentImports, diagnostics);
    }

    private void validateInlineKairoStyle(String rawStyle, SourceLocation location, Diagnostic.Bag diagnostics) {
        for (String stmt : rawStyle.split(";")) {
            String trimmed = stmt.trim();
            if (trimmed.isEmpty()) continue;
            int colon = trimmed.indexOf(':');
            if (colon > 0) {
                String prop = trimmed.substring(0, colon).trim();
                String val = trimmed.substring(colon + 1).trim();
                KairoStyleDialect.validateDeclaration(prop, val, location, diagnostics);
            }
        }
    }
}
