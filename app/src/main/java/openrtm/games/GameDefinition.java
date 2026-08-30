package openrtm.games;

import java.util.List;
import java.util.Map;

public record GameDefinition(
        String name,
        String titleId,
        long cbufAddress,
        ClientLayout clients,
        Map<String, String> maps,
        Map<String, String> gametypes,
        List<GameButton> buttons,
        List<GameToggle> toggles,
        List<StatField> statFields,
        List<StatPreset> presets,
        StatsApplier statsApplier
) {
}
