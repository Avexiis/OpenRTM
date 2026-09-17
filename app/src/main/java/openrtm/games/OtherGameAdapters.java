package openrtm.games;

import openrtm.console.ConsoleService;

import java.util.List;

public final class OtherGameAdapters
{
	private OtherGameAdapters()
	{
	}

	public static List<OtherGameAdapter> create(ConsoleService console)
	{
		return List.of(new NfsmwAdapter(console));
	}
}
