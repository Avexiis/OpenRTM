package openrtm.games;

import openrtm.console.ConsoleService;

import java.util.Map;

public record ActionContext(
        ConsoleService service,
        GameDefinition game,
        Map<String, String> inputs,
        int clientIndex,
        boolean allClients,
        String mapCode,
        String gametypeCode
) {
    public String input(String key) {
        return inputs.getOrDefault(key, "").trim();
    }

    public int intInput(String key) {
        String value = input(key);
        if (value.isBlank()) return 0;
        return Integer.parseInt(value);
    }

    public long longInput(String key) {
        String value = input(key);
        if (value.isBlank()) return 0L;
        return Long.parseLong(value);
    }
}
