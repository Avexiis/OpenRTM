package openrtm.cod.gsc;

record GscToken(Type type, String text, String file, int line, int column)
{
	enum Type
	{
		IDENTIFIER,
		NUMBER,
		STRING,
		SYMBOL,
		END
	}

	boolean is(String value)
	{
		return type != Type.STRING && type != Type.NUMBER && type != Type.END && text.equalsIgnoreCase(value);
	}

	String location()
	{
		return file + ":" + line + ":" + column;
	}
}
