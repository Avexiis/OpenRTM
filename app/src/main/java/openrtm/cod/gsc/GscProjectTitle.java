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
import java.util.Locale;

public final class GscProjectTitle
{
	private GscProjectTitle()
	{
	}

	public static String read(Path source)
	{
		Path selected = source.toAbsolutePath().normalize();
		boolean directory = Files.isDirectory(selected);
		Path parent = directory ? selected : selected.getParent();
		Path config = parent == null ? null : parent.resolve("config.il");
		boolean project = config != null && Files.isRegularFile(config);
		if (project)
		{
			try
			{
				if (Files.size(config) <= 65_536)
				{
					DocumentBuilderFactory factory = DocumentBuilderFactory.newDefaultInstance();
					factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
					factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
					factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
					factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
					factory.setXIncludeAware(false);
					factory.setExpandEntityReferences(false);
					var builder = factory.newDocumentBuilder();
					builder.setErrorHandler(new DefaultHandler());
					try (InputStream input = Files.newInputStream(config))
					{
						Element root = builder.parse(input).getDocumentElement();
						if (root.getTagName().equals("Project"))
						{
							for (Node child = root.getFirstChild(); child != null; child = child.getNextSibling())
							{
								if (child instanceof Element element && element.getTagName().equals("Title"))
								{
									String title = clean(element.getTextContent());
									if (!title.isEmpty())
									{
										return title;
									}
								}
							}
						}
					}
				}
			}
			catch (IOException | SAXException | ParserConfigurationException ignored)
			{
			}
		}
		Path name = (project ? parent : selected).getFileName();
		String title = name == null ? "GSC menu" : name.toString();
		if (!directory && !project && title.toLowerCase(Locale.ROOT).endsWith(".gsc"))
		{
			title = title.substring(0, title.length() - 4);
		}
		title = clean(title);
		return title.isEmpty() ? "GSC menu" : title;
	}

	private static String clean(String title)
	{
		return title.replaceAll("[\\p{Cntrl}\\s]+", " ").strip();
	}
}
