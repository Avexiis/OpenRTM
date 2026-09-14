package openrtm.cod;

public enum CodGame
{
	COD4("COD4", "Call of Duty 4: Modern Warfare", "415607E6"),
	WAW("WAW", "Call of Duty: World at War", "4156081C"),
	MW2("MW2", "Call of Duty: Modern Warfare 2", "41560817"),
	BO1("BO1", "Call of Duty: Black Ops", "41560855"),
	MW3("MW3", "Call of Duty: Modern Warfare 3", "415608CB"),
	BO2("BO2", "Call of Duty: Black Ops II", "415608C3"),
	GHOSTS("Ghosts", "Call of Duty: Ghosts", "415608FC"),
	AW("AW", "Call of Duty: Advanced Warfare", "41560914"),
	BO3("BO3", "Call of Duty: Black Ops III", "4156091D");

	private final String tabName;
	private final String displayName;
	private final String titleId;

	CodGame(String tabName, String displayName, String titleId)
	{
		this.tabName = tabName;
		this.displayName = displayName;
		this.titleId = titleId;
	}

	public String tabName()
	{
		return tabName;
	}

	public String displayName()
	{
		return displayName;
	}

	public String titleId()
	{
		return titleId;
	}
}
