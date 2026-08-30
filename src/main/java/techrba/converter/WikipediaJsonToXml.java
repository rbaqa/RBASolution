package techrba.converter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.Map;

/**
 * Converts a JSON document (e.g. Wikipedia search results captured via the
 * RBA REST test) into a valid, well-formed XML document.
 *
 * <p>Usage:
 * <pre>
 *   java -cp rba-task.jar techrba.converter.WikipediaJsonToXml &lt;input.json&gt; &lt;output.xml&gt;
 * </pre>
 * If arguments are omitted, falls back to {@code reports/wikipedia-response.json}
 * and {@code reports/wikipedia-response.xml}.
 *
 * <p>Guarantees valid XML by:
 * <ul>
 *   <li>Sanitising every JSON key into a legal XML element name (numeric and
 *       otherwise illegal leading characters are prefixed/escaped)</li>
 *   <li>Wrapping each array element in a fixed {@code <item>} element so array
 *       contents with different shapes stay well-formed</li>
 *   <li>Escaping all text content</li>
 * </ul></p>
 */
public final class WikipediaJsonToXml {

    private static final Logger LOG = LogManager.getLogger(WikipediaJsonToXml.class);
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    private static final String DEFAULT_INPUT = "reports/wikipedia-response.json";
    private static final String DEFAULT_OUTPUT = "reports/wikipedia-response.xml";

    private WikipediaJsonToXml() {
        // static utility
    }

    public static void main(String[] args) throws IOException {
        String input = args.length > 0 ? args[0] : DEFAULT_INPUT;
        String output = args.length > 1 ? args[1] : DEFAULT_OUTPUT;

        String json = readInput(input);
        String xml = convert(json);

        java.nio.file.Path parent = Paths.get(output).getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        byte[] bytes = xml.getBytes(StandardCharsets.UTF_8);
        Files.write(Paths.get(output), bytes);
        LOG.info("Converted '{}' -> '{}' ({} bytes)", input, output, bytes.length);
    }

    /**
     * Converts a JSON string into a valid XML string.
     *
     * @param json raw JSON input
     * @return valid, well-formed XML with a root {@code <response>} element
     */
    public static String convert(String json) throws IOException {
        JsonNode root = JSON_MAPPER.readTree(json);
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<response>");
        if (root.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                convertNode(field.getKey(), field.getValue(), sb);
            }
        } else {
            convertNode("response", root, sb);
        }
        sb.append("</response>\n");
        return sb.toString();
    }

    private static void convertNode(String key, JsonNode node, StringBuilder sb) {
        if (node.isObject()) {
            sb.append('<').append(sanitize(key)).append('>');
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                convertNode(field.getKey(), field.getValue(), sb);
            }
            sb.append("</").append(sanitize(key)).append('>');
        } else if (node.isArray()) {
            sb.append('<').append(sanitize(key)).append('>');
            for (JsonNode item : node) {
                // each element is wrapped in a fixed <item> element so
                // heterogeneous array elements stay well-formed
                convertNode("item", item, sb);
            }
            sb.append("</").append(sanitize(key)).append('>');
        } else if (node.isNull()) {
            sb.append('<').append(sanitize(key)).append("/>");
        } else {
            sb.append('<').append(sanitize(key)).append('>')
                    .append(escape(node.asText()))
                    .append("</").append(sanitize(key)).append('>');
        }
    }

    /**
     * Converts an arbitrary JSON key into a legal, collision-safe XML element name.
     */
    static String sanitize(String key) {
        String name = key == null ? "" : key.trim();
        if (name.isEmpty()) {
            return "node";
        }
        // Names starting with a digit or other illegal chars need a prefix
        StringBuilder sb = new StringBuilder();
        if (!Character.isLetter(name.charAt(0)) && name.charAt(0) != '_') {
            sb.append('_');
        }
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '.') {
                sb.append(c);
            } else {
                sb.append('_');
            }
        }
        return sb.toString();
    }

    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private static String readInput(String path) throws IOException {
        File f = new File(path);
        if (!f.exists()) {
            LOG.warn("Input file '{}' not found; using an empty JSON document", path);
            return "{}";
        }
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
