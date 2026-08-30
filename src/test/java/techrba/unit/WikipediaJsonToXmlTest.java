package techrba.unit;

import org.testng.Assert;
import org.testng.annotations.Test;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import techrba.converter.WikipediaJsonToXml;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;

/**
 * Unit tests for {@link WikipediaJsonToXml} - verify output is always valid XML
 * and that JSON numeric keys / arrays are handled safely.
 */
public class WikipediaJsonToXmlTest {

    private static Document parse(String xml) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        DocumentBuilder b = f.newDocumentBuilder();
        return b.parse(new InputSource(new StringReader(xml)));
    }

    @Test(groups = {"unit", "smoke", "regression"})
    public void numericKeysProduceValidXml() throws Exception {
        String json = "{\"query\":{\"pages\":{\"7353998\":{\"title\":\"Raiffeisen\",\"pageid\":7353998}}}}";
        String xml = WikipediaJsonToXml.convert(json);
        Document doc = parse(xml); // throws if not well-formed
        Assert.assertEquals(doc.getDocumentElement().getNodeName(), "response");
    }

    @Test(groups = {"unit", "smoke", "regression"})
    public void arraysAreWrappedInItemElements() throws Exception {
        String json = "{\"list\":[10,20,30]}";
        String xml = WikipediaJsonToXml.convert(json);
        Document doc = parse(xml);
        Assert.assertEquals(doc.getElementsByTagName("item").getLength(), 3);
    }

    @Test(groups = {"unit", "smoke", "regression"})
    public void specialCharactersAreEscaped() throws Exception {
        String json = "{\"note\":\"a < b & c \\\"d\\\"\"}";
        String xml = WikipediaJsonToXml.convert(json);
        parse(xml); // must not throw
    }
}
