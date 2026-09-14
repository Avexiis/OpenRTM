package openrtm.cod;

import openrtm.console.ConsoleService;

import java.util.List;

public final class CodAdapters
{
	private CodAdapters()
	{
	}

	public static List<CodAdapter> create(ConsoleService console)
	{
		return List.of(
			new Cod4Adapter(console),
			new WawAdapter(console),
			new Mw2Adapter(console),
			new Bo1Adapter(console),
			new Mw3Adapter(console),
			new Bo2Adapter(console),
			new GhostsAdapter(console),
			new AwAdapter(console),
			new Bo3Adapter(console));
	}
}
