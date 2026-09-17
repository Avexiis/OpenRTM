package openrtm.games;

public enum OtherGame
{
	NFSMW("NFS:MW", "Need for Speed: Most Wanted", "454107D9");

	private final String tabName;
	private final String displayName;
	private final String titleId;

	OtherGame(String tabName, String displayName, String titleId)
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
