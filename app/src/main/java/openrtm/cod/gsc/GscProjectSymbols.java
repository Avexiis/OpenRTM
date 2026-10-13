package openrtm.cod.gsc;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

final class GscProjectSymbols
{
	private GscProjectSymbols()
	{
	}

	static Set<String> read(Path project)
	{
		if (project == null)
		{
			return Set.of();
		}
		Path selected = project.toAbsolutePath().normalize();
		Path parent = Files.isDirectory(selected) ? selected : selected.getParent();
		Path config = parent == null ? null : parent.resolve("config.il");
		if (config == null || !Files.isRegularFile(config))
		{
			return Set.of();
		}
		try
		{
			if (Files.size(config) > 65_536)
			{
				throw new IllegalArgumentException("config.il:1:1: Project settings are too large");
			}
			DocumentBuilderFactory factory = DocumentBuilderFactory.newDefaultInstance();
			factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
			factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
			factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
			factory.setXIncludeAware(false);
			factory.setExpandEntityReferences(false);
			var builder = factory.newDocumentBuilder();
			builder.setErrorHandler(new DefaultHandler());
			Set<String> symbols = new LinkedHashSet<>();
			try (InputStream input = Files.newInputStream(config))
			{
				Element root = builder.parse(input).getDocumentElement();
				if (!root.getTagName().equals("Project"))
				{
					throw new IllegalArgumentException("config.il:1:1: Invalid project settings");
				}
				for (Node child = root.getFirstChild(); child != null; child = child.getNextSibling())
				{
					if (child instanceof Element element && element.getTagName().equals("Symbols"))
					{
						for (String symbol : element.getTextContent().split("[;\\s]+"))
						{
							if (symbol.isEmpty())
							{
								continue;
							}
							if (!symbol.matches("[A-Za-z_][A-Za-z_0-9]*"))
							{
								throw new IllegalArgumentException("config.il:1:1: Invalid project symbol '" + symbol + "'");
							}
							symbols.add(symbol);
						}
					}
				}
			}
			return Set.copyOf(symbols);
		}
		catch (IOException | SAXException | ParserConfigurationException failure)
		{
			throw new IllegalArgumentException("config.il:1:1: Unable to read project settings", failure);
		}
	}
}
