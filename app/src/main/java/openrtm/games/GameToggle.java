package openrtm.games;

public record GameToggle(
        String group,
        String label,
        String detail,
        ActionRunner enable,
        ActionRunner disable
) {
}
