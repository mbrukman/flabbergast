package flabbergast;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

class ApiGenerator {
  private class AccumulateNames implements Function<String, String> {
    String base;

    @Override
    public String apply(String input) {
      if (base == null) {
        base = input;
      } else {
        base = base + "." + input;
      }
      return base;
    }
  }

  public static ApiGenerator create(String library_name, String github)
      throws ParserConfigurationException {
    DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
    DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

    Document doc = docBuilder.newDocument();
    doc.setXmlVersion("1.0");
    doc.appendChild(
        doc.createProcessingInstruction("xml-stylesheet", "href=\"o_0.xsl\" type=\"text/xsl\""));
    Element node = doc.createElementNS("http://flabbergast.org/api", "o_0:lib");
    node.setAttribute("xmlns", "http://www.w3.org/1999/xhtml");
    node.setAttributeNS(node.getNamespaceURI(), "name", library_name);
    if (github != null) {
      node.setAttributeNS(node.getNamespaceURI(), "github", github + "/" + library_name + ".o_0");
    }
    doc.appendChild(node);
    return new ApiGenerator(doc, node, new String[0]);
  }

  private Element description = null;
  private final Document document;

  private final String[] names;
  private final Node node;
  private final Map<String, Node> refs = new HashMap<String, Node>();
  private final Map<String, Node> uses = new HashMap<String, Node>();

  private ApiGenerator(Document doc, Node node, String[] names) {
    document = doc;
    this.node = node;
    this.names = names;
  }

  public Element appendDescriptionTag(String xmlns, String tag, String text) {
    Element node = document.createElementNS(xmlns, tag);
    node.appendChild(document.createTextNode(text));
    getDescription().appendChild(node);
    return node;
  }

  public void appendDescriptionText(String text) {
    Node node = document.createTextNode(text);
    getDescription().appendChild(node);
  }

  public ApiGenerator createChild(
      String name, SourceLocation location, boolean informative, String... types) {
    Element node =
        document.createElementNS(document.getDocumentElement().getNamespaceURI(), "o_0:attr");
    node.setAttributeNS(document.getDocumentElement().getNamespaceURI(), "name", name);
    node.setAttributeNS(
        document.getDocumentElement().getNamespaceURI(),
        "startline",
        Integer.toString(location.getStartLine()));
    node.setAttributeNS(
        document.getDocumentElement().getNamespaceURI(),
        "startcol",
        Integer.toString(location.getStartColumn()));
    node.setAttributeNS(
        document.getDocumentElement().getNamespaceURI(),
        "endline",
        Integer.toString(location.getEndLine()));
    node.setAttributeNS(
        document.getDocumentElement().getNamespaceURI(),
        "endcol",
        Integer.toString(location.getEndColumn()));
    node.setAttributeNS(
        document.getDocumentElement().getNamespaceURI(),
        "informative",
        informative ? "true" : "false");
    this.node.appendChild(node);

    Arrays.stream(names)
        .map(new AccumulateNames())
        .map(base -> base + "." + name)
        .forEach(
            defName -> {
              Element defNode =
                  document.createElementNS(
                      document.getDocumentElement().getNamespaceURI(), "o_0:def");
              defNode.appendChild(document.createTextNode(defName));
              node.appendChild(defNode);
            });
    String[] newNames;
    if (Arrays.stream(types).anyMatch(type -> type.equals("Template"))) {
      newNames = new String[0];
    } else {
      newNames = Stream.concat(Stream.of(name), Arrays.stream(names)).toArray(String[]::new);
    }

    for (String type : types) {
      Element type_node =
          document.createElementNS(document.getDocumentElement().getNamespaceURI(), "o_0:type");
      type_node.appendChild(document.createTextNode(type));
      node.appendChild(type_node);
    }
    return new ApiGenerator(document, node, newNames);
  }

  private Element getDescription() {
    if (description == null) {
      description =
          document.createElementNS(
              getDocument().getDocumentElement().getNamespaceURI(), "o_0:description");
      node.appendChild(description);
    }
    return description;
  }

  public Document getDocument() {
    return document;
  }

  private void register(Map<String, Node> known, String tag, String content) {
    if (known.containsKey(content)) {
      return;
    }
    Element node = document.createElementNS(document.getDocumentElement().getNamespaceURI(), tag);
    node.appendChild(document.createTextNode(content));
    known.put(content, node);
    this.node.appendChild(node);
  }

  public void registerRef(String uri) {
    if (uri.startsWith("lib:")) {
      register(refs, "o_0:ref", uri.substring(4));
    }
  }

  public void registerUse(Stream<String> names, String... suffixes) {
    String baseName = names.collect(Collectors.joining("."));
    if (suffixes.length == 0) {
      registerUse(baseName);
    } else {
      Stream<String> stream = Arrays.stream(suffixes);
      if (baseName.length() > 0) {
        stream = stream.map(suffix -> baseName + "." + suffix);
      }
      stream.forEach(this::registerUse);
    }
  }

  public void registerUse(String name) {
    register(uses, "o_0:use", name);
  }
}
