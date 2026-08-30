package openrtm.games;

public record GameButton(
        String group,
        String label,
        String detail,
        ActionRunner runner
) {
}
