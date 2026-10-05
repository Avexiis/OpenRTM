package openrtm.cod.gsc;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

final class Iw4GscTables
{
	private static final Map<String, Integer> FUNCTIONS = new LinkedHashMap<>();
	private static final Map<String, Integer> METHODS = new LinkedHashMap<>();
	private static final Map<String, Integer> TOKENS = new LinkedHashMap<>();

	static
	{
		loadBuiltins();
		loadTokens();
	}

	static int function(String name)
	{
		return FUNCTIONS.getOrDefault(normalize(name), -1);
	}

	static int method(String name)
	{
		return METHODS.getOrDefault(normalize(name), -1);
	}

	static int token(String name)
	{
		return TOKENS.getOrDefault(normalize(name), -1);
	}

	private static void loadBuiltins()
	{
		read("/openrtm/cod/iw4-builtins.tsv", values -> {
			Map<String, Integer> target = "f".equals(values[0]) ? FUNCTIONS : METHODS;
			target.putIfAbsent(normalize(values[2]), Integer.decode(values[1]));
		});
	}

	private static void loadTokens()
	{
		read("/openrtm/cod/iw4-tokens.tsv",
			values -> TOKENS.putIfAbsent(normalize(values[1]), Integer.decode(values[0])));
	}

	private static void read(String resource, LineReader reader)
	{
		InputStream input = Iw4GscTables.class.getResourceAsStream(resource);
		if (input == null)
		{
			throw new ExceptionInInitializerError("Missing GSC table " + resource);
		}
		try (BufferedReader lines = new BufferedReader(new InputStreamReader(input, StandardCharsets.US_ASCII)))
		{
			String line;
			while ((line = lines.readLine()) != null)
			{
				if (!line.isBlank())
				{
					reader.read(line.split("\\t"));
				}
			}
		}
		catch (IOException failure)
		{
			throw new ExceptionInInitializerError(failure);
		}
	}

	private static String normalize(String value)
	{
		return value.toLowerCase(Locale.ROOT);
	}

	private Iw4GscTables()
	{
	}

	@FunctionalInterface
	private interface LineReader
	{
		void read(String[] values);
	}
}
