package openrtm.cod;

import java.util.List;
import java.util.Map;

public interface CodAdapter
{
	record StatField(String key, String label, long minimum, long maximum)
	{
	}

	record StatGroup(String key, String label, List<StatField> fields, boolean readable)
	{
		public StatGroup
		{
			fields = List.copyOf(fields);
		}
	}

	record Action(String key, String label, String section, boolean clientTargeted)
	{
	}

	record Toggle(String key, String label, String section, boolean clientTargeted)
	{
	}

	record NumberOption(String key, String label, String section, long minimum, long maximum, long initial,
		boolean clientTargeted)
	{
	}

	record ChoiceOption(String key, String label, String section, List<String> choices, boolean clientTargeted)
	{
		public ChoiceOption
		{
			choices = List.copyOf(choices);
		}
	}

	record TextOption(String key, String label, String section, int maximumLength, boolean clientTargeted)
	{
	}

	record ClientInfo(int slot, String name)
	{
		@Override
		public String toString()
		{
			return name == null || name.isBlank() ? "Slot " + slot : "Slot " + slot + " - " + name;
		}
	}

	CodGame game();

	List<StatGroup> statGroups();

	Map<String, Long> readStats(String group);

	void writeStats(String group, Map<String, Long> values);

	int classCount();

	boolean classNamesReadable();

	List<String> readClassNames();

	void writeClassNames(List<String> names);

	void unlockAll();

	default boolean unlockTargetsClient()
	{
		return false;
	}

	void unlockClient(int client);

	void derankSignedInProfile();

	boolean clientNamesReadable();

	int maximumClients();

	List<ClientInfo> readClients();

	List<Action> actions();

	void runAction(String key, int client);

	List<Toggle> toggles();

	void setToggle(String key, boolean enabled, int client);

	List<NumberOption> numberOptions();

	void setNumber(String key, long value, int client);

	List<ChoiceOption> choiceOptions();

	void setChoice(String key, String value, int client);

	List<TextOption> textOptions();

	void setText(String key, String value, int client);
}
