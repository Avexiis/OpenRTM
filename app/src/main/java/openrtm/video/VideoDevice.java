package openrtm.video;

import java.util.Objects;

public final class VideoDevice
{
	private final String id;
	private final String name;
	private final String locator;
	private final int index;

	public VideoDevice(String id, String name, String locator, int index)
	{
		this.id = id;
		this.name = name;
		this.locator = locator;
		this.index = index;
	}

	public String id()
	{
		return id;
	}

	public String locator()
	{
		return locator;
	}

	public int index()
	{
		return index;
	}

	@Override
	public boolean equals(Object other)
	{
		if (this == other)
		{
			return true;
		}
		if (!(other instanceof VideoDevice))
		{
			return false;
		}
		VideoDevice device = (VideoDevice) other;
		return id.equals(device.id);
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(id);
	}

	@Override
	public String toString()
	{
		return name;
	}
}
